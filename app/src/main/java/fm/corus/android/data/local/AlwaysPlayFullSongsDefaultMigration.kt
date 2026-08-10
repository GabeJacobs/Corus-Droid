package fm.corus.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking

/**
 * Rollout: new installs default to Always Play Full Songs (no mini-player toggle).
 * Existing installs keep the prior toggle UX unless they already opted into always-full.
 * Mirrors iOS `AlwaysPlayFullSongsDefaultMigration`.
 */
object AlwaysPlayFullSongsDefaultMigration {
    private const val PREFS_NAME = "corus_prefs"
    private const val MIGRATED_KEY = "always_play_full_songs_default_v1"
    private const val ALWAYS_KEY = "always_play_full_songs"
    private const val ONBOARDING_KEY_PREFIX = "completed_onboarding_"

    fun runIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATED_KEY, false)) return
        prefs.edit().putBoolean(MIGRATED_KEY, true).apply()
        if (!isExistingInstall(prefs)) return

        // Pin legacy behavior: toggle visible until the user opts into always-full.
        if (!prefs.contains(ALWAYS_KEY)) {
            prefs.edit().putBoolean(ALWAYS_KEY, false).apply()
            runBlocking {
                context.corusPreferencesDataStore.edit {
                    it[booleanPreferencesKey(ALWAYS_KEY)] = false
                }
            }
        }
    }

    private fun isExistingInstall(prefs: android.content.SharedPreferences): Boolean {
        return prefs.all.keys.any { it.startsWith(ONBOARDING_KEY_PREFIX) }
    }
}
