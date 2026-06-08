package fm.corus.android.service

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
 * We immediately call startForeground() with a minimal placeholder notification
 * inside onStartCommand so we *always* satisfy Android's 5-second
 * startForegroundService → startForeground contract, even when the player is
 * still buffering at the moment the service starts. media3's
 * DefaultMediaNotificationProvider then replaces this placeholder with the
 * rich media-style notification once the session has a playing player —
 * Samsung's QS inline media player picks that up just fine.
 *
 * Without this, switching tracks (which can briefly drop the player back into
 * BUFFERING) caused a `ForegroundServiceDidNotStartInTimeException` because
 * media3 couldn't post its notification quickly enough on the second
 * startForegroundService call.
 */
@AndroidEntryPoint
class CorusPlaybackService : MediaSessionService() {

    @Inject
    lateinit var nowPlayingManager: NowPlayingManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Corus")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
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
