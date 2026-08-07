package fm.corus.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.runBlocking

/**
 * One-time rollout: preview-first posts model — reset [play_full_songs] to off for
 * existing installs even if they previously opted into feed-wide full playback.
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

        prefs.edit().putBoolean(PLAY_FULL_SONGS_KEY, false).apply()
        runBlocking {
            context.corusPreferencesDataStore.edit {
                it[booleanPreferencesKey(PLAY_FULL_SONGS_KEY)] = false
            }
        }
    }

    private fun isExistingInstall(prefs: android.content.SharedPreferences): Boolean {
        return prefs.all.keys.any { it.startsWith(ONBOARDING_KEY_PREFIX) }
    }
}
