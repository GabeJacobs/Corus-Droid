package fm.corus.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking

/**
 * One-time rollout: move feed playback mode from Settings to the mini-player toggle.
 * Existing installs keep full playback when they had it on (1.5.6 default); new installs
 * start on 30s previews. [always_play_full_songs] stays off unless the user opts in.
 */
object PlayFullSongsPostsModelMigration {
    private const val PREFS_NAME = "corus_prefs"
    private const val MIGRATED_KEY = "play_full_songs_posts_model_v1"
    private const val PLAY_FULL_SONGS_KEY = "play_full_songs"
    private const val ONBOARDING_KEY_PREFIX = "completed_onboarding_"

    fun runIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATED_KEY, false)) return
        prefs.edit().putBoolean(MIGRATED_KEY, true).apply()
        if (!isExistingInstall(prefs)) return

        // 1.5.6 defaulted feed-wide full playback on; only explicit opt-outs stay on 30s.
        if (!prefs.contains(PLAY_FULL_SONGS_KEY)) {
            prefs.edit().putBoolean(PLAY_FULL_SONGS_KEY, true).apply()
            runBlocking {
                context.corusPreferencesDataStore.edit {
                    it[booleanPreferencesKey(PLAY_FULL_SONGS_KEY)] = true
                }
            }
        }
    }

    private fun isExistingInstall(prefs: android.content.SharedPreferences): Boolean {
        return prefs.all.keys.any { it.startsWith(ONBOARDING_KEY_PREFIX) }
    }
}
