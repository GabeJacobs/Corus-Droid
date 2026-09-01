package fm.corus.android.service

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import fm.corus.android.TestEnvironment
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when Play Integrity / App Check cannot mint a token. */
class AppCheckUnavailableException : Exception("App Check token unavailable")

/**
 * Waits for a token, then runs [block]. If the token never arrives, [block]
 * is not called — the request must not go out unattested.
 */
suspend fun <T> withAppCheckToken(
    awaitToken: suspend () -> Boolean,
    block: suspend () -> T,
): T {
    if (!awaitToken()) throw AppCheckUnavailableException()
    return block()
}

/**
 * Play Integrity / App Check token access for signup and other attested calls.
 *
 * iOS warms the token at launch and waits before retrying rejected reads.
 * Android previously installed the provider and hoped the Functions SDK would
 * attach a token; if Play Integrity was still minting, the request left
 * without one and the backend returned 401 App Check MISSING.
 */
@Singleton
class AppCheckTokenSource @Inject constructor() {

    /** Non-blocking first fetch so signup is less likely to wait. */
    fun warmup() {
        if (TestEnvironment.isActive) return
        try {
            FirebaseAppCheck.getInstance().getAppCheckToken(false)
        } catch (e: Exception) {
            Log.w(TAG, "App Check warmup failed", e)
        }
    }

    /**
     * Waits until a token is cached, or Play Integrity fails. Returns false
     * only when we must not send the request. Emulator builds skip this so
     * local signup against the Functions emulator still works.
     */
    suspend fun awaitToken(): Boolean {
        if (TestEnvironment.isActive) return true
        return try {
            FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
            true
        } catch (first: Exception) {
            Log.w(TAG, "App Check token fetch failed, retrying", first)
            try {
                FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
                true
            } catch (retry: Exception) {
                Log.e(TAG, "App Check token unavailable", retry)
                false
            }
        }
    }

    suspend fun <T> withToken(block: suspend () -> T): T =
        withAppCheckToken(::awaitToken, block)

    private companion object {
        const val TAG = "AppCheckTokenSource"
    }
}
