package fm.corus.android.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import fm.corus.android.R
import fm.corus.android.domain.NowPlayingManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the [MediaSession] backing the lock-screen
 * and notification media controls. Mirrors the iOS `MPRemoteCommandCenter`
 * integration — when audio is playing, system surfaces (lock screen,
 * Control Center, Wear, Auto, headphone-button events) can pause / resume
 * / skip / scrub via the session.
 *
 * We immediately call startForeground() inside onStartCommand so we *always*
 * satisfy Android's 5-second startForegroundService → startForeground contract,
 * even when the player is still buffering at the moment the service starts.
 *
 * That foreground notification is bound to the live MediaSession via MediaStyle,
 * which is what makes the system render the rich now-playing card instead of a
 * generic "Play music" output shortcut.
 *
 * media3's own DefaultMediaNotificationProvider does NOT drive this notification:
 * its service-side notification manager only engages when a controller connects
 * through this service, but SystemUI binds straight to the session, so media3
 * never re-posts. Playback position/state stay live on the card, but its
 * title/artist/artwork would freeze on whichever track was current when the card
 * first latched. We therefore re-post the notification ourselves on every track /
 * play-state change (see the state collector in onCreate).
 *
 * Without the immediate startForeground, switching tracks (which can briefly drop
 * the player back into BUFFERING) caused a `ForegroundServiceDidNotStartInTimeException`
 * because media3 couldn't post its notification quickly enough on the second
 * startForegroundService call.
 */
@AndroidEntryPoint
class CorusPlaybackService : MediaSessionService() {

    @Inject
    lateinit var nowPlayingManager: NowPlayingManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        // Branded provider for the case where media3 ever does drive the notification
        // (a controller connecting through this service). Harmless otherwise, and it
        // shares NOTIFICATION_ID with our own posts so there's never a duplicate card.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(PLAYBACK_CHANNEL_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_corus) },
        )

        // Keep the now-playing card's metadata in sync with the current track. media3
        // doesn't re-post here (SystemUI binds straight to the session, see class KDoc),
        // so without this the card's title/artist/artwork freeze on the first track even
        // as the audio and scrubber advance. Re-post the session-bound notification
        // whenever the track or play/pause state changes.
        serviceScope.launch {
            nowPlayingManager.state
                .map { it.trackId to it.isPlaying }
                .distinctUntilChanged()
                .collect { (trackId, _) ->
                    if (isForeground && trackId != null) {
                        getSystemService(NotificationManager::class.java)
                            ?.notify(NOTIFICATION_ID, buildNotification())
                    }
                }
        }
    }

    /**
     * MediaStyle foreground notification bound to the live session, carrying the
     * current track's title/artist. The system media card reads transport state live
     * from the session but only refreshes title/artist/artwork when this is re-posted.
     */
    private fun buildNotification(): Notification {
        val s = nowPlayingManager.state.value
        val builder = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            // Monochrome status-bar icon. The opaque launcher icon would be masked to a
            // solid white square in the status bar (Android tints small icons by alpha).
            .setSmallIcon(R.drawable.ic_stat_corus)
            .setContentTitle(s.trackName.takeIf { it.isNotBlank() } ?: "Corus")
            .setContentText(s.artistName.takeIf { it.isNotBlank() })
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        // NowPlayingManager builds the session before starting this service, so it's
        // present on the first start. Falls back to a bare notification if, for any
        // reason, the session isn't up yet.
        nowPlayingManager.mediaSession?.let { session ->
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session))
        }
        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        } catch (e: IllegalStateException) {
            // Android 12+ throws ForegroundServiceStartNotAllowedException (a
            // subclass of IllegalStateException) when we try to promote to the
            // foreground from a background-initiated start without an exemption —
            // e.g. playback kicked off while the app is already backgrounded.
            // We catch the superclass (not the API-31 type) so the clause stays
            // verifiable on our minSdk 26 devices, where it can never trigger.
            //
            // Background audio isn't legal without the foreground service, so
            // bail cleanly instead of crashing: tear this start down and let
            // NowPlayingManager re-promote on the next foreground play().
            isForeground = false
            nowPlayingManager.onForegroundStartDenied()
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        isForeground = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // NowPlayingManager builds the session lazily when the first track
        // begins playing. If a system controller (e.g. Bluetooth headset)
        // queries before any playback has started, returning null tells
        // media3 there's nothing to control yet.
        return nowPlayingManager.mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from recents while audio is playing. Match the
        // common-case music-app behavior: tear down playback so we don't
        // keep the foreground service alive without UI to manage it.
        nowPlayingManager.stop()
        stopSelf()
    }

    companion object {
        private const val PLAYBACK_CHANNEL_ID = "corus_playback"
        private const val NOTIFICATION_ID = 1001
    }
}
