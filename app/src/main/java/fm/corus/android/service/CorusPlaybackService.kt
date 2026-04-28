package fm.corus.android.service

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import fm.corus.android.domain.NowPlayingManager
import javax.inject.Inject

/**
 * Foreground service that hosts the [MediaSession] backing the lock-screen
 * and notification media controls. Mirrors the iOS `MPRemoteCommandCenter`
 * integration — when audio is playing, system surfaces (lock screen,
 * Control Center, Wear, Auto, headphone-button events) can pause / resume
 * / skip / scrub via the session.
 *
 * media3 spins this service up automatically when playback starts and
 * keeps it alive (with a notification) for as long as the player is
 * playing. We don't own the [androidx.media3.exoplayer.ExoPlayer] or
 * [MediaSession] here — [NowPlayingManager] does — so this class is a
 * thin bridge.
 */
@AndroidEntryPoint
class CorusPlaybackService : MediaSessionService() {

    @Inject
    lateinit var nowPlayingManager: NowPlayingManager

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // NowPlayingManager builds the session lazily when the first track
        // begins playing. If a system controller (e.g. Bluetooth headset)
        // queries before any playback has started, returning null tells
        // media3 there's nothing to control yet.
        return nowPlayingManager.mediaSession
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // App swiped away from recents while audio is playing. Match the
        // common-case music-app behavior: tear down playback so we don't
        // keep the foreground service alive without UI to manage it.
        nowPlayingManager.stop()
        stopSelf()
    }

    override fun onDestroy() {
        // The service is being killed (e.g. system resource pressure or
        // task removal). NowPlayingManager owns the session/player
        // lifecycle, so we just clear our reference here.
        super.onDestroy()
    }
}
