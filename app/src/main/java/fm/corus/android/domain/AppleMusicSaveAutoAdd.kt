package fm.corus.android.domain

import android.util.Log
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MusicService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Mirrors a successful Corus song save to an already connected Apple Music library. */
@Singleton
class AppleMusicSaveAutoAdd @Inject constructor(
    private val preferences: PreferencesDataStore,
    private val musicService: MusicServicePreference,
    private val appleMusic: AppleMusicPlayback,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handleSaved(post: CymbalPost, userId: String) {
        if (post.isMovie || musicService.current.value != MusicService.APPLE_MUSIC) return
        scope.launch {
            try {
                if (isEnabled()) appleMusic.addToLibrary(post.toQueuedTrack(), userId, ::isEnabled)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Log.w("AppleMusicSaveAutoAdd", "Could not add the saved song to Apple Music.")
            }
        }
    }

    private suspend fun isEnabled(): Boolean =
        musicService.current.value == MusicService.APPLE_MUSIC && preferences.autoAddSavedToAppleMusic.first()
}
