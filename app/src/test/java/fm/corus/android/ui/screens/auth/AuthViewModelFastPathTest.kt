package fm.corus.android.ui.screens.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AuthViewModel.shouldFastPathLaunch] — the pure decision that
 * gates the cold-launch fast path. The fast path requires BOTH a restored
 * Firebase session and the local onboarding flag, so a signed-out launch always
 * lands on login and a logged-in-but-not-yet-onboarded user never flashes the
 * feed. Mirrors iOS `AuthService.shouldFastPathLaunch`.
 */
class AuthViewModelFastPathTest {

    @Test fun `user present and onboarded fast-paths`() {
        assertTrue(AuthViewModel.shouldFastPathLaunch(hasUser = true, hasCompletedOnboardingLocally = true))
    }

    @Test fun `user present but not yet onboarded does NOT fast-path`() {
        // New sign-up, or an existing user's first launch before the flag is
        // backfilled — must take the careful slow path.
        assertFalse(AuthViewModel.shouldFastPathLaunch(hasUser = true, hasCompletedOnboardingLocally = false))
    }

    @Test fun `no restored session never fast-paths even if flag is set`() {
        // Signed out (e.g. after log out + relaunch) — the flag persists per-uid
        // but is irrelevant without a session; the user goes to login.
        assertFalse(AuthViewModel.shouldFastPathLaunch(hasUser = false, hasCompletedOnboardingLocally = true))
    }

    @Test fun `no session and no flag does NOT fast-path`() {
        assertFalse(AuthViewModel.shouldFastPathLaunch(hasUser = false, hasCompletedOnboardingLocally = false))
    }
}
