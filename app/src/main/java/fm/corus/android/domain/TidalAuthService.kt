package fm.corus.android.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tidal.sdk.auth.TidalAuth
import com.tidal.sdk.auth.model.AuthConfig
import com.tidal.sdk.auth.model.LoginConfig
import com.tidal.sdk.auth.model.QueryParameter
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.ui.screens.auth.TidalLoginActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps TIDAL's Auth SDK: one-time configuration, the user OAuth
 * (authorization-code + PKCE) login, and credential access. The Android
 * counterpart to the iOS `TidalAuthService` — except the login UI is a TIDAL
 * web page hosted in [TidalLoginActivity] (mirrors TIDAL's own Android sample),
 * which captures the `corus://tidal-auth` redirect and hands it back here.
 *
 * Security: uses the PUBLIC OAuth client (clientId only, no secret) with PKCE.
 * The client secret is never shipped in the app — it lives only in Secret
 * Manager for the backend catalog resolver (`tidalLookup`). The SDK stores the
 * refreshable credentials encrypted on-device under [CREDENTIALS_KEY].
 */
@Singleton
class TidalAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private var tidalAuth: TidalAuth? = null

    /** Set while a [TidalLoginActivity] round-trip is in flight; completed by
     *  the activity (redirect captured) or on its dismissal (user cancelled). */
    @Volatile
    private var pendingLogin: CompletableDeferred<Boolean>? = null

    /** Idempotent one-time SDK configuration. `getInstance` is process-global,
     *  so the first config wins; safe to call before any auth call. */
    private fun configureIfNeeded(): TidalAuth {
        tidalAuth?.let { return it }
        val config = AuthConfig(
            clientId = CLIENT_ID,
            clientUniqueKey = stableClientUniqueKey(),
            clientSecret = null, // public client → PKCE, no secret in-app
            credentialsKey = CREDENTIALS_KEY,
            scopes = SCOPES,
        )
        return TidalAuth.getInstance(config, context).also {
            tidalAuth = it
            _isLoggedIn.value = it.credentialsProvider.isUserLoggedIn()
        }
    }

    /** Whether a TIDAL *user* session exists (configures the SDK on first call).
     *  Distinct from holding any token — only a user-level session can create
     *  playlists. */
    suspend fun isUserLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        runCatching { configureIfNeeded().credentialsProvider.isUserLoggedIn() }.getOrDefault(false)
    }

    /** Returns a TIDAL access token, refreshing transparently via the SDK. null
     *  when not logged in or refresh failed. */
    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        runCatching { configureIfNeeded().credentialsProvider.getCredentials().successData?.token }
            .getOrNull()
    }

    /** Launch the TIDAL login (auth-code + PKCE). Suspends until the login
     *  activity reports success/cancellation. Tapping "generate playlist" is the
     *  sign-in trigger, mirroring iOS. */
    suspend fun login(): Boolean {
        val auth = configureIfNeeded()
        // initializeLogin builds the PKCE authorize URL and stores the code
        // verifier on this (singleton) Auth instance for finalizeLogin.
        val loginUri = auth.auth.initializeLogin(REDIRECT_URI, loginConfig())
        val deferred = CompletableDeferred<Boolean>()
        pendingLogin = deferred
        val intent = Intent(context, TidalLoginActivity::class.java).apply {
            putExtra(TidalLoginActivity.EXTRA_LOGIN_URL, loginUri.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return deferred.await()
    }

    /** Called by [TidalLoginActivity] once it captures the redirect URI. Exchanges
     *  the auth code for a token and resolves the in-flight [login]. */
    suspend fun handleRedirect(redirect: Uri): Boolean = withContext(Dispatchers.IO) {
        val auth = configureIfNeeded()
        val ok = runCatching {
            // The SDK expects the untouched query component of the redirect URI.
            auth.auth.finalizeLogin(redirect.query.orEmpty()).isSuccess
        }.getOrDefault(false)
        _isLoggedIn.value = auth.credentialsProvider.isUserLoggedIn()
        val result = ok && _isLoggedIn.value
        pendingLogin?.complete(result)
        pendingLogin = null
        result
    }

    /** Called by [TidalLoginActivity] if it closes without a redirect (back
     *  press / dismissed). Resolves the in-flight [login] as cancelled. */
    fun cancelPendingLogin() {
        pendingLogin?.complete(false)
        pendingLogin = null
    }

    /** Force a fresh login to (re)grant scopes — needed when a user authorized
     *  before `playlists.write` was added, so their token lacks it and the
     *  playlist API 403s until they re-consent. */
    suspend fun reauthenticateForPlaylists(): Boolean {
        logout()
        return login()
    }

    /** Clears the stored TIDAL session so the user can disconnect / switch
     *  accounts. Safe to call when not logged in. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val auth = configureIfNeeded()
        runCatching { auth.auth.logout() }
        _isLoggedIn.value = auth.credentialsProvider.isUserLoggedIn()
    }

    /** TIDAL needs to know the client is an Android app; pass it through the
     *  login config as the sample app does. */
    private fun loginConfig() = LoginConfig(
        customParams = setOf(QueryParameter(key = "appMode", value = "android")),
    )

    /** Stable per-install id for [AuthConfig.clientUniqueKey] (scopes stored
     *  credentials to this device/install). */
    private fun stableClientUniqueKey(): String {
        val prefs = context.getSharedPreferences("corus_prefs", Context.MODE_PRIVATE)
        prefs.getString(UNIQUE_KEY_PREF, null)?.let { return it }
        return UUID.randomUUID().toString().also { prefs.edit().putString(UNIQUE_KEY_PREF, it).apply() }
    }

    companion object {
        /** Public OAuth client ID for the "Corus" TIDAL developer app. Safe to
         *  ship — it travels in the clear in every login URL. The secret is NOT
         *  here. Shared with iOS. */
        private const val CLIENT_ID = "eBMf4FEo0eSOJceR"

        /** Reuses the app's existing `corus://` scheme. Must be registered as a
         *  Redirect URI on the TIDAL dev app (same value as iOS). */
        const val REDIRECT_URI = "corus://tidal-auth"

        private const val CREDENTIALS_KEY = "fm.corus.tidal.credentials"
        private const val UNIQUE_KEY_PREF = "tidal.clientUniqueKey"

        /** Scoped strictly to playlist generation (this SDK is used for nothing
         *  else on Android — preview playback stays on ExoPlayer):
         *  `user.read` for the country lookup (`/users/me`), plus
         *  `playlists.read`/`playlists.write` to create the playlist.
         *  NOTE: the TIDAL dev app must have `playlists.read` + `playlists.write`
         *  enabled (the Corus app currently only has user.read/entitlements.read/
         *  playback) — add them in the developer portal or login will reject the
         *  scope request. */
        private val SCOPES = setOf(
            "user.read",
            "playlists.read",
            "playlists.write",
        )
    }
}
