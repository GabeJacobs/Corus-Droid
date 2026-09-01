package fm.corus.android.ui.screens.auth

import fm.corus.android.service.AppCheckUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Maps backend / App Check failures to the copy the auth screen shows.
 * A missing token must never look like "Couldn't sign in with Google."
 */
class AuthSignupErrorsTest {

    @Test
    fun `unavailable exception is app check`() {
        assertTrue(isAppCheckUnavailable(AppCheckUnavailableException()))
    }

    @Test
    fun `generic exception is not app check`() {
        assertFalse(isAppCheckUnavailable(RuntimeException("network")))
    }

    @Test
    fun `email OTP missing token is device verification`() {
        assertEquals(
            AuthSignupErrorKind.DeviceVerification,
            classifyEmailOtpError(isAppCheckUnavailable = true, functionsCode = null),
        )
    }

    @Test
    fun `email OTP unauthenticated 401 is device verification`() {
        assertEquals(
            AuthSignupErrorKind.DeviceVerification,
            classifyEmailOtpError(isAppCheckUnavailable = false, functionsCode = "UNAUTHENTICATED"),
        )
    }

    @Test
    fun `email OTP rate limit is unchanged`() {
        assertEquals(
            AuthSignupErrorKind.EmailOtpRateLimited,
            classifyEmailOtpError(isAppCheckUnavailable = false, functionsCode = "RESOURCE_EXHAUSTED"),
        )
    }

    @Test
    fun `email OTP kill switch is unchanged`() {
        assertEquals(
            AuthSignupErrorKind.EmailOtpUnavailable,
            classifyEmailOtpError(isAppCheckUnavailable = false, functionsCode = "FAILED_PRECONDITION"),
        )
    }

    @Test
    fun `email OTP suspended is unchanged`() {
        assertEquals(
            AuthSignupErrorKind.AccountSuspended,
            classifyEmailOtpError(isAppCheckUnavailable = false, functionsCode = "PERMISSION_DENIED"),
        )
    }

    @Test
    fun `email OTP bad code is unchanged`() {
        assertEquals(
            AuthSignupErrorKind.InvalidCode,
            classifyEmailOtpError(isAppCheckUnavailable = false, functionsCode = "NOT_FOUND"),
        )
    }

    @Test
    fun `email OTP unknown error stays generic`() {
        assertEquals(
            AuthSignupErrorKind.Generic,
            classifyEmailOtpError(isAppCheckUnavailable = false, functionsCode = null),
        )
    }

    @Test
    fun `Google path missing token is device verification not generic`() {
        assertEquals(
            AuthSignupErrorKind.DeviceVerification,
            classifyProviderSignInError(isAppCheckUnavailable = true),
        )
    }

    @Test
    fun `Google path ordinary failure stays generic so existing copy is used`() {
        assertEquals(
            AuthSignupErrorKind.Generic,
            classifyProviderSignInError(isAppCheckUnavailable = false),
        )
    }
}
