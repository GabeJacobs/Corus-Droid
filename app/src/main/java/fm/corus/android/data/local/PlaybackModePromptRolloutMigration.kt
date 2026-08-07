package fm.corus.android.data.local

import android.content.Context

/**
 * One-time rollout: existing installs get the playback-mode chooser on their next
 * eligible catalog play even if they toggled Settings earlier (which marked chosen).
 * Fresh installs are untouched — chosen already defaults to false.
 */
object PlaybackModePromptRolloutMigration {
    private const val PREFS_NAME = "corus_prefs"
    private const val MIGRATED_KEY = "playback_mode_prompt_rollout_v1"
    private const val CHOSEN_KEY = "playback_mode_chosen"
    private const val ONBOARDING_KEY_PREFIX = "completed_onboarding_"

    fun runIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATED_KEY, false)) return
        prefs.edit().putBoolean(MIGRATED_KEY, true).apply()
        if (!isExistingInstall(prefs)) return
        prefs.edit().putBoolean(CHOSEN_KEY, false).apply()
    }

    private fun isExistingInstall(prefs: android.content.SharedPreferences): Boolean {
        return prefs.all.keys.any { it.startsWith(ONBOARDING_KEY_PREFIX) }
    }
}
