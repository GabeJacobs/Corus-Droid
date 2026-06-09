package fm.corus.android.data.local

import android.content.Context

/**
 * One-time freeze of the "unset theme" default, guarding the change of the default
 * appearance from Light to System.
 *
 * The theme default is a runtime fallback that was never written to storage, so
 * flipping it to System would also flip every existing user who never explicitly
 * picked a theme. To keep the change "future users only", the first time the app
 * resolves the default we record whether this is a pre-existing install (one that
 * had already completed onboarding) and pin those installs to Light. Genuinely
 * fresh installs are recorded as System.
 *
 * The decision is frozen on first call and never re-evaluated, so a brand-new user
 * who later completes onboarding still keeps System (the freeze happened at first
 * launch, before any onboarding flag existed).
 *
 * Stored in the synchronous `corus_prefs` SharedPreferences (same store as
 * [OnboardingLocalStore]) so it can be read on the launch-critical path without
 * awaiting DataStore.
 */
object AppearanceDefaultMigration {
    private const val PREFS_NAME = "corus_prefs"
    private const val UNSET_THEME_DEFAULT_KEY = "unset_theme_default"
    private const val ONBOARDING_KEY_PREFIX = "completed_onboarding_"

    /**
     * Returns the appearance storageValue ("light" or "system") to use when the
     * user has not explicitly chosen a theme. Freezes the decision on first call.
     */
    fun unsetThemeDefault(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(UNSET_THEME_DEFAULT_KEY, null)?.let { return it }

        val isExistingInstall = prefs.all.keys.any { it.startsWith(ONBOARDING_KEY_PREFIX) }
        val resolved = if (isExistingInstall) "light" else "system"
        prefs.edit().putString(UNSET_THEME_DEFAULT_KEY, resolved).apply()
        return resolved
    }
}
