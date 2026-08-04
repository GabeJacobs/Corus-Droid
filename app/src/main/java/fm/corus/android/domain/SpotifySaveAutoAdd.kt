package fm.corus.android.domain

import android.util.Log
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.SpotifyLibraryService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.share.ShareResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort trigger for the "Add Saved Songs to Library" opt-in: once a
 * Spotify user has flipped the settings toggle on (and completed the
 * `user-library-modify` OAuth consent via [SpotifyLibraryAuthService]), every
 * subsequent song save also adds the track to their Spotify library ("Liked
 * Songs") via the Web API.
 *
 * Fire-and-forget — mirrors iOS: never blocks [PostEngagementManager.toggleSave],
 * never surfaces an error to the user. Every failure path is caught and logged.
 */
@Singleton
class SpotifySaveAutoAdd @Inject constructor(
    private val remoteConfig: RemoteConfigService,
    private val preferencesDataStore: PreferencesDataStore,
    private val musicServicePreference: MusicServicePreference,
    private val spotifyLibraryAuthService: SpotifyLibraryAuthService,
    private val spotifyLibraryService: SpotifyLibraryService,
    private val shareResolver: ShareResolver,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called right after a NEW save (not an unsave) succeeds. Non-suspend,
     * fire-and-forget — safe to call from anywhere without awaiting it.
     */
    fun handleSaved(post: CymbalPost) {
        scope.launch {
            try {
                if (post.isMovie) return@launch
                if (!remoteConfig.spotifyLibrarySaveEnabled) return@launch
                if (musicServicePreference.current.value != MusicService.SPOTIFY) return@launch
                if (!preferencesDataStore.autoAddSavedToSpotify.first()) return@launch

                val spotifyTrackId = resolveSpotifyTrackId(post) ?: return@launch
                val token = spotifyLibraryAuthService.accessToken() ?: return@launch
                spotifyLibraryService.addTrackToLibrary(spotifyTrackId, token)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add saved track to Spotify library: ${e.message}")
            }
        }
    }

    /** Spotify-source posts already carry a usable id; everything else needs
     *  an ISRC cross-reference through the same resolver the share flow uses.
     *  Null (no ISRC, or no validated match) means "not on Spotify" — silently
     *  skip, no error. */
    private suspend fun resolveSpotifyTrackId(post: CymbalPost): String? {
        val track = post.track
        if (track.source == TrackSource.SPOTIFY) {
            return track.id.takeIf { it.isNotBlank() }
        }
        val isrc = track.isrc?.takeIf { it.isNotBlank() } ?: return null
        return shareResolver.resolveMatch(isrc, track.name, track.artistName)
            ?.id
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "SpotifySaveAutoAdd"
    }
}
