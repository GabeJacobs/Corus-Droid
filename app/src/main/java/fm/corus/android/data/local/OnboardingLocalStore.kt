package fm.corus.android.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous local record of which accounts have completed onboarding, so a cold
 * launch can route a returning user straight to the feed without waiting on any
 * network. Mirrors iOS's `hasCompletedOnboarding_<uid>` UserDefaults flag.
 *
 * Backed by [SharedPreferences] (not DataStore) specifically because the read
 * happens on the launch-critical path and must be **synchronous** — it seeds the
 * initial auth state in [fm.corus.android.ui.screens.auth.AuthViewModel] before
 * the first frame, so the returning user never sees the Loading spinner. The flag
 * is only ever an optimization hint: it is consulted solely when a Firebase
 * session is already restored, and the background re-validation can still eject.
 *
 * Persists across sign-out so a re-login is fast again.
 */
@Singleton
class OnboardingLocalStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("corus_prefs", Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(uid: String): Boolean =
        prefs.getBoolean(key(uid), false)

    fun markCompletedOnboarding(uid: String) {
        prefs.edit().putBoolean(key(uid), true).apply()
    }

    private fun key(uid: String) = "completed_onboarding_$uid"
}
