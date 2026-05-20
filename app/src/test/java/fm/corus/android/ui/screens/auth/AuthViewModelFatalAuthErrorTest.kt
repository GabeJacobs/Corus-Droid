package fm.corus.android.ui.screens.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AuthViewModel.isFatalAuthErrorCode] — the inner string-level
 * helper that decides whether a FirebaseAuth error code should sign the user
 * out on cold launch. Kept string-based so it runs as a plain JVM unit test
 * without needing to construct real FirebaseAuthException instances (which
 * touch Android `TextUtils` and can't be built in a JVM-only test).
 */
class AuthViewModelFatalAuthErrorTest {

    @Test fun `user disabled is fatal`() {
        assertTrue(AuthViewModel.isFatalAuthErrorCode("ERROR_USER_DISABLED"))
    }

    @Test fun `user not found is fatal`() {
        assertTrue(AuthViewModel.isFatalAuthErrorCode("ERROR_USER_NOT_FOUND"))
    }

    @Test fun `invalid user token is fatal`() {
        assertTrue(AuthViewModel.isFatalAuthErrorCode("ERROR_INVALID_USER_TOKEN"))
    }

    @Test fun `expired user token is fatal`() {
        assertTrue(AuthViewModel.isFatalAuthErrorCode("ERROR_USER_TOKEN_EXPIRED"))
    }

    @Test fun `network request failed is NOT fatal`() {
        // The whole point of the bug fix: a network-failure FirebaseAuthException
        // must NOT log the user out on launch.
        assertFalse(AuthViewModel.isFatalAuthErrorCode("ERROR_NETWORK_REQUEST_FAILED"))
    }

    @Test fun `internal error is NOT fatal`() {
        assertFalse(AuthViewModel.isFatalAuthErrorCode("ERROR_INTERNAL_ERROR"))
    }

    @Test fun `too many requests is NOT fatal`() {
        assertFalse(AuthViewModel.isFatalAuthErrorCode("ERROR_TOO_MANY_REQUESTS"))
    }

    @Test fun `null error code is NOT fatal`() {
        // Firestore exceptions, generic IO errors, etc. have no FirebaseAuth code.
        assertFalse(AuthViewModel.isFatalAuthErrorCode(null))
    }

    @Test fun `empty string is NOT fatal`() {
        assertFalse(AuthViewModel.isFatalAuthErrorCode(""))
    }

    @Test fun `unrelated string is NOT fatal`() {
        assertFalse(AuthViewModel.isFatalAuthErrorCode("UNAVAILABLE"))
    }
}
