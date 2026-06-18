package fm.corus.android.service

import android.app.Notification
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
 * That foreground notification is bound to the live MediaSession via MediaStyle.
 * This is load-bearing: it makes the system render the rich now-playing card AND
 * lets SystemUI connect a controller to the session, which is the trigger for
 * media3's DefaultMediaNotificationProvider to take over and post its full media
 * notification. A plain (non-MediaStyle) placeholder never triggers that handoff,
 * so the system only ever shows a generic "Play music" output shortcut.
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

    override fun onCreate() {
        super.onCreate()
        // Let media3 own the rich media notification (artwork, title, artist,
        // transport controls). Brand it with the Corus status-bar icon and post it
        // on our existing low-importance playback channel. The notification id
        // matches the placeholder below so media3's version replaces it in place
        // instead of stacking a second notification.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(PLAYBACK_CHANNEL_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_corus) },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            // Monochrome status-bar icon. The opaque launcher icon would be masked
            // to a solid white square in the status bar (Android tints small icons
            // by their alpha), so it must not be used here.
            .setSmallIcon(R.drawable.ic_stat_corus)
            .setContentTitle("Corus")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        // Bind the foreground notification to the live MediaSession. NowPlayingManager
        // builds the session before starting this service, so it's present on the first
        // start. MediaStyle is what makes the system render the rich now-playing card and
        // lets SystemUI attach a controller, the trigger for media3 to take over the
        // notification (see class KDoc). Falls back to the bare placeholder if, for any
        // reason, the session isn't up yet.
        nowPlayingManager.mediaSession?.let { session ->
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session))
        }

        val notification = builder.build()
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
            nowPlayingManager.onForegroundStartDenied()
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
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
