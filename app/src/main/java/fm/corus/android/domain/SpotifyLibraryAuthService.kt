package fm.corus.android.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spotify Web API OAuth (Authorization Code + PKCE) for the "Add Saved Songs
 * to Library" opt-in. Requests ONLY the `user-library-modify` scope — this
 * token is used for exactly one thing: `PUT /v1/me/tracks` via
 * [fm.corus.android.data.remote.SpotifyLibraryService].
 *
 * DELIBERATELY SEPARATE from [SpotifyAuthService] (the App Remote
 * full-playback token cache, no refresh token, keys `accessToken`/
 * `tokenExpiry` in the `fm.corus.spotify.auth` file). Sharing a token cache
 * between the two flows bit us on iOS: playing a song silently clobbered the
 * library-scoped access token with a playback-only one, so the very next
 * library-save call 403'd with "insufficient scope" right after the user had
 * just granted permission. This service owns its own EncryptedSharedPreferences
 * file ([PREFS_NAME]) and its own keys — [SpotifyAuthService] and
 * [SpotifyPlaybackService] are never read or written here.
 *
 * Reuses the SAME registered Spotify Developer app ([CLIENT_ID]) and the same
 * `corus://spotify-auth` redirect URI as the App Remote flow — no new
 * Developer Dashboard registration needed. `MainActivity` disambiguates the
 * two flows on the incoming redirect: a `code` query param routes to
 * [handleRedirect] here; an `access_token` (or `error`, App-Remote-shape)
 * routes to [SpotifyPlaybackService.handleRedirectUri] as before.
 *
 * Login uses a Chrome Custom Tab, NOT a WebView — Spotify's login page is
 * known to reject WebView user agents (the same reason iOS uses
 * `ASWebAuthenticationSession` rather than `WKWebView` for this flow).
 */
@Singleton
class SpotifyLibraryAuthService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val httpClient: HttpClient,
    private val analyticsService: fm.corus.android.service.AnalyticsService,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Set while a [login] round-trip is in flight; completed by
     *  [handleRedirect] once the `corus://spotify-auth` redirect (with a
     *  `code` or `error` param) comes back through MainActivity. */
    @Volatile
    private var pendingLogin: CompletableDeferred<Boolean>? = null

    /** The PKCE code verifier generated for the in-flight [login] — needed
     *  again in [handleRedirect] to complete the token exchange. */
    @Volatile
    private var pendingCodeVerifier: String? = null

    /**
     * Launch the Spotify authorize page in a Custom Tab, asking only for
     * `user-library-modify`. Suspends until [handleRedirect] resolves the
     * in-flight deferred. Returns false immediately if a login is already in
     * flight (shouldn't happen from a single settings toggle, but avoids
     * clobbering a pending code verifier).
     */
    suspend fun login(context: Context): Boolean {
        if (pendingLogin != null) return false

        val verifier = generateCodeVerifier()
        val challenge = codeChallenge(verifier)
        pendingCodeVerifier = verifier

        val deferred = CompletableDeferred<Boolean>()
        pendingLogin = deferred

        val authUrl = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("show_dialog", "true")
            .build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        // The redirect lands back on MainActivity via the existing corus://
        // spotify-auth intent-filter, not on an Activity we host — so this can
        // safely launch from any context, application context included.
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, authUrl)

        return deferred.await()
    }

    /**
     * Called by `MainActivity` once it captures the `corus://spotify-auth`
     * redirect with a `code` (or `error`) param. Exchanges the code for
     * tokens and resolves the in-flight [login]. Safe to call with no
     * [login] in flight (e.g. a stale/duplicate redirect) — resolves false
     * with no side effects.
     */
    suspend fun handleRedirect(uri: Uri): Boolean {
        val deferred = pendingLogin
        val verifier = pendingCodeVerifier
        pendingLogin = null
        pendingCodeVerifier = null

        val error = uri.getQueryParameter("error")
        if (error != null) {
            if (error.equals("access_denied", ignoreCase = true)) {
                analyticsService.logSpotifyAuthConnectCancelled()
            } else {
                analyticsService.logSpotifyAuthConnectFailed(error)
            }
            deferred?.complete(false)
            return false
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank() || verifier == null) {
            deferred?.complete(false)
            return false
        }

        val result = runCatching { exchangeCode(code, verifier) }
        val ok = result.getOrNull() == true
        if (ok) {
            analyticsService.logSpotifyAuthConnected("oauth")
        } else {
            analyticsService.logSpotifyAuthConnectFailed(
                result.exceptionOrNull()?.message ?: "token_exchange_failed",
            )
        }
        deferred?.complete(ok)
        return ok
    }

    /**
     * Returns the cached Web API access token if not expired (30s buffer,
     * mirroring iOS), else refreshes it using the stored refresh token. Null
     * when there's no refresh token or the refresh fails — never throws.
     */
    suspend fun accessToken(): String? {
        val expiry = prefs.getLong(TOKEN_EXPIRY_KEY, 0L)
        val cached = prefs.getString(ACCESS_TOKEN_KEY, null)
        if (!cached.isNullOrBlank() && expiry > System.currentTimeMillis() + 30_000L) {
            return cached
        }
        return refreshAccessToken()
    }

    /** Clears only this service's own stored tokens. */
    fun logout() {
        prefs.edit()
            .remove(ACCESS_TOKEN_KEY)
            .remove(TOKEN_EXPIRY_KEY)
            .remove(REFRESH_TOKEN_KEY)
            .apply()
        analyticsService.logSpotifyAuthDisconnected()
    }

    // ── Token exchange ─────────────────────────────────────────────────────

    private suspend fun exchangeCode(code: String, verifier: String): Boolean {
        val response = httpClient.post(TOKEN_URL) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", REDIRECT_URI)
                        append("client_id", CLIENT_ID)
                        append("code_verifier", verifier)
                    },
                ),
            )
        }
        if (response.status.value !in 200..299) return false
        val json = parseJson(response) ?: return false
        val accessToken = json["access_token"]?.jsonPrimitive?.content ?: return false
        val expiresIn = json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
        val refreshToken = json["refresh_token"]?.jsonPrimitive?.content
        storeTokens(accessToken, refreshToken, expiresIn)
        return true
    }

    private suspend fun refreshAccessToken(): String? {
        val refreshToken = prefs.getString(REFRESH_TOKEN_KEY, null) ?: return null
        return try {
            val response = httpClient.post(TOKEN_URL) {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", "refresh_token")
                            append("refresh_token", refreshToken)
                            append("client_id", CLIENT_ID)
                        },
                    ),
                )
            }
            if (response.status.value !in 200..299) return null
            val json = parseJson(response) ?: return null
            val accessToken = json["access_token"]?.jsonPrimitive?.content ?: return null
            val expiresIn = json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
            // Spotify rotates the refresh token on some (not all) refresh
            // responses — keep the old one when this response doesn't include one.
            val rotatedRefreshToken = json["refresh_token"]?.jsonPrimitive?.content
            storeTokens(accessToken, rotatedRefreshToken ?: refreshToken, expiresIn)
            accessToken
        } catch (e: Exception) {
            null
        }
    }

    private fun storeTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        prefs.edit().apply {
            putString(ACCESS_TOKEN_KEY, accessToken)
            putLong(TOKEN_EXPIRY_KEY, System.currentTimeMillis() + expiresInSeconds * 1000L)
            if (!refreshToken.isNullOrBlank()) putString(REFRESH_TOKEN_KEY, refreshToken)
        }.apply()
    }

    private suspend fun parseJson(response: HttpResponse): JsonObject? =
        runCatching { JSON.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()

    // ── PKCE ───────────────────────────────────────────────────────────────

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64UrlEncode(digest)
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    companion object {
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val SCOPE = "user-library-modify"

        private const val PREFS_NAME = "fm.corus.spotify.libraryauth"
        private const val ACCESS_TOKEN_KEY = "webApiAccessToken"
        private const val TOKEN_EXPIRY_KEY = "webApiTokenExpiry"
        private const val REFRESH_TOKEN_KEY = "refreshToken"

        /** Same registered Spotify Developer app + redirect as the App Remote
         *  flow — see [SpotifyAuthService]. */
        private val CLIENT_ID: String get() = SpotifyAuthService.CLIENT_ID
        private val REDIRECT_URI: String get() = SpotifyAuthService.REDIRECT_URI

        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
