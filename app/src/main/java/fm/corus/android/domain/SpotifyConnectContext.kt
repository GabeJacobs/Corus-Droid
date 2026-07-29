package fm.corus.android.domain

import android.app.Activity
import android.content.Intent
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import java.lang.ref.WeakReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * App Remote authorization must run with a visible [Activity] context.
 * On Android 14+, Spotify's built-in auth activity cannot start while the
 * Spotify app is in the background, so interactive auth uses auth-lib from
 * Corus (foreground) instead of [ConnectionParams.showAuthView].
 */
object SpotifyConnectContext {
    const val AUTH_REQUEST_CODE = 53291

    private var activityRef: WeakReference<Activity>? = null
    private var authContinuation: Continuation<String>? = null

    fun setActivity(activity: Activity?) {
        activityRef = activity?.let { WeakReference(it) }
    }

    fun currentActivity(): Activity? = activityRef?.get()

    fun activityOr(applicationContext: android.content.Context): android.content.Context =
        currentActivity() ?: applicationContext

    /**
     * Opens Spotify auth-lib login while Corus is foreground (Android 14+ safe).
     * Resumes [authContinuation] from [deliverAuthorizationResult].
     */
    fun beginInteractiveAuthorization(onToken: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val activity = currentActivity()
        if (activity == null) {
            onFailure(SpotifyPlaybackError.NotConnected())
            return
        }
        val request = AuthorizationRequest.Builder(
            SpotifyAuthService.CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            SpotifyAuthService.REDIRECT_URI,
        )
            .setScopes(arrayOf("app-remote-control"))
            .build()
        AuthorizationClient.openLoginActivity(activity, AUTH_REQUEST_CODE, request)
        // Result delivered via deliverAuthorizationResult; store callbacks on service side
        // through pendingAuthHandlers below.
        pendingOnToken = onToken
        pendingOnFailure = onFailure
    }

    private var pendingOnToken: ((String) -> Unit)? = null
    private var pendingOnFailure: ((Exception) -> Unit)? = null

    fun deliverAuthorizationResult(resultCode: Int, data: Intent?) {
        val onToken = pendingOnToken
        val onFailure = pendingOnFailure
        pendingOnToken = null
        pendingOnFailure = null
        if (onToken == null || onFailure == null) return

        val response = AuthorizationClient.getResponse(resultCode, data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                val token = response.accessToken
                if (!token.isNullOrBlank()) {
                    onToken(token)
                } else {
                    onFailure(SpotifyPlaybackError.NotAuthorized())
                }
            }
            AuthorizationResponse.Type.ERROR -> {
                onFailure(
                    SpotifyPlaybackError.ApiError(
                        response.error ?: "Spotify authorization failed",
                    ),
                )
            }
            else -> onFailure(SpotifyPlaybackError.NotAuthorized())
        }
    }

    /** Suspend bridge used by [SpotifyPlaybackService]. */
    suspend fun awaitInteractiveAuthorization(): String {
        val existing = authContinuation
        if (existing != null) {
            throw SpotifyPlaybackError.ApiError("Spotify authorization already in progress")
        }
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            authContinuation = cont
            cont.invokeOnCancellation {
                if (authContinuation === cont) authContinuation = null
                pendingOnToken = null
                pendingOnFailure = null
            }
            beginInteractiveAuthorization(
                onToken = { token ->
                    authContinuation = null
                    cont.resume(token)
                },
                onFailure = { error ->
                    authContinuation = null
                    cont.resumeWithException(error)
                },
            )
        }
    }
}
