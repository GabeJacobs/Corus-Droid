package fm.corus.android.ui.screens.auth

import fm.corus.android.service.AppCheckUnavailableException

/**
 * User-facing signup/sign-in error kinds. Pure so JVM tests can cover the
 * App Check vs generic mapping without constructing Firebase exceptions.
 */
internal enum class AuthSignupErrorKind {
    DeviceVerification,
    EmailOtpRateLimited,
    EmailOtpUnavailable,
    AccountSuspended,
    InvalidCode,
    Generic,
}

internal fun isAppCheckUnavailable(error: Throwable): Boolean =
    error is AppCheckUnavailableException

internal fun classifyEmailOtpError(
    isAppCheckUnavailable: Boolean,
    functionsCode: String?,
): AuthSignupErrorKind {
    if (isAppCheckUnavailable || functionsCode == "UNAUTHENTICATED") {
        return AuthSignupErrorKind.DeviceVerification
    }
    return when (functionsCode) {
        "RESOURCE_EXHAUSTED" -> AuthSignupErrorKind.EmailOtpRateLimited
        "FAILED_PRECONDITION" -> AuthSignupErrorKind.EmailOtpUnavailable
        "PERMISSION_DENIED" -> AuthSignupErrorKind.AccountSuspended
        "INVALID_ARGUMENT", "NOT_FOUND" -> AuthSignupErrorKind.InvalidCode
        else -> AuthSignupErrorKind.Generic
    }
}

internal fun classifyProviderSignInError(
    isAppCheckUnavailable: Boolean,
    functionsCode: String? = null,
): AuthSignupErrorKind {
    if (isAppCheckUnavailable || functionsCode == "UNAUTHENTICATED") {
        return AuthSignupErrorKind.DeviceVerification
    }
    return AuthSignupErrorKind.Generic
}
