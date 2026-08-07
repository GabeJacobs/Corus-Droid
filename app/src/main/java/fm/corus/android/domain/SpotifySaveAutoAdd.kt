package fm.corus.android.domain

import android.util.Log
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.share.ShareResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Best-effort trigger for the "Add Saved Songs to Library" opt-in: once a
 * Spotify user has flipped the settings toggle on, every subsequent song save
 * also adds the track to their Spotify library ("Liked Songs").
 *
 * Delivery goes through App Remote's user API, not the Web API: the Spotify
 * app performs the write itself, so the feature works for every user with
 * Spotify installed instead of the 25 allowlisted by development mode.
 *
 * The tradeoff is that App Remote only exists while the IPC session is live.
 * A save made with Spotify closed is parked in [SpotifyLibraryQueue] and
 * delivered on the next connect, so nothing is lost but a save can land well
 * after the tap.
 *
 * Fire-and-forget — mirrors iOS: never blocks [PostEngagementManager.toggleSave],
 * never surfaces an error to the user. Every failure path is caught and logged.
 */
@Singleton
class SpotifySaveAutoAdd @Inject constructor(
    private val remoteConfig: RemoteConfigService,
    private val preferencesDataStore: PreferencesDataStore,
    private val musicServicePreference: MusicServicePreference,
    private val spotifyAuthService: SpotifyAuthService,
    // Provider mirrors the one on SpotifyPlaybackService: the two services
    // reference each other (it flushes us on connect, we deliver through it).
    private val spotifyPlaybackService: Provider<SpotifyPlaybackService>,
    private val shareResolver: ShareResolver,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Serialises queue read-modify-write. Both a fresh save and a connect-
     * triggered drain can run at once, and DataStore edits here are not
     * transactional across the read.
     */
    private val queueMutex = Mutex()

    /**
     * Called right after a NEW save (not an unsave) succeeds. Non-suspend,
     * fire-and-forget — safe to call from anywhere without awaiting it.
     */
    fun handleSaved(post: CymbalPost) {
        scope.launch {
            try {
                if (post.isMovie) return@launch
                if (!isEnabled()) return@launch

                val spotifyTrackId = resolveSpotifyTrackId(post) ?: return@launch
                deliver("spotify:track:$spotifyTrackId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add saved track to Spotify library: ${e.message}")
            }
        }
    }

    /**
     * Drain whatever is parked. Called on every App Remote connect; cheap and
     * silent when the queue is empty, which is the common case.
     */
    fun flushPendingLibraryAdds() {
        scope.launch {
            try {
                if (!isEnabled()) return@launch
                if (!spotifyAuthService.appRemoteCanModifyLibrary()) return@launch
                if (pending().isEmpty()) return@launch

                // Let playback settle first. A connect is almost always a user
                // pressing play, and the queue shouldn't compete with the play
                // command for the IPC channel.
                delay(FLUSH_DELAY_MS)

                for (uri in pending()) {
                    val playback = spotifyPlaybackService.get()
                    if (!playback.isConnected) return@launch
                    try {
                        playback.addToLibrary(uri)
                        dequeue(uri)
                    } catch (e: Exception) {
                        // Still connected means the session is fine and this
                        // URI is the problem (bad id, unavailable in market).
                        // Drop it rather than let one entry wedge the queue.
                        if (playback.isConnected) {
                            Log.w(TAG, "Dropping $uri: ${e.message}")
                            dequeue(uri)
                        } else {
                            return@launch
                        }
                    }
                    delay(FLUSH_SPACING_MS)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Flush of pending Spotify library saves failed: ${e.message}")
            }
        }
    }

    /** True when the feature should run for the current user + settings. */
    private suspend fun isEnabled(): Boolean {
        if (!remoteConfig.spotifyLibrarySaveEnabled) return false
        if (musicServicePreference.current.value != MusicService.SPOTIFY) return false
        return preferencesDataStore.autoAddSavedToSpotify.first()
    }

    /** Deliver now if App Remote can take it, otherwise park it. Never throws. */
    private suspend fun deliver(uri: String) {
        val playback = spotifyPlaybackService.get()
        // A live session authorized before the library scopes existed can play
        // but not save. Queue rather than fail: that token expires within the
        // hour and the next authorization picks the scopes up.
        if (!playback.isConnected || !spotifyAuthService.appRemoteCanModifyLibrary()) {
            enqueue(uri)
            return
        }
        try {
            playback.addToLibrary(uri)
        } catch (e: Exception) {
            Log.w(TAG, "Add failed, queueing $uri: ${e.message}")
            // The Spotify app refused despite the marker saying it shouldn't.
            // Forget the grant so the next authorization re-asks, and keep the
            // save so it lands once it does.
            spotifyAuthService.clearLibraryScopeGrant()
            enqueue(uri)
        }
    }

    // ── Queue persistence ──────────────────────────────────────────────────

    private suspend fun pending(): List<String> =
        SpotifyLibraryQueue.decode(preferencesDataStore.pendingSpotifyLibraryUrisJson.first())

    private suspend fun enqueue(uri: String) = queueMutex.withLock {
        val next = SpotifyLibraryQueue.enqueue(pending(), uri)
        preferencesDataStore.setPendingSpotifyLibraryUrisJson(SpotifyLibraryQueue.encode(next))
    }

    private suspend fun dequeue(uri: String) = queueMutex.withLock {
        val next = SpotifyLibraryQueue.remove(pending(), uri)
        preferencesDataStore.setPendingSpotifyLibraryUrisJson(SpotifyLibraryQueue.encode(next))
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

        /** Matches iOS: 2s after connect, 150ms between queued adds. */
        private const val FLUSH_DELAY_MS = 2_000L
        private const val FLUSH_SPACING_MS = 150L
    }
}
