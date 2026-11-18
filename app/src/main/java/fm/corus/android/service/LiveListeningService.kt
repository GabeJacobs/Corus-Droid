package fm.corus.android.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import fm.corus.android.MainActivity
import fm.corus.android.R

/** Keeps Corus following the active Live room while the listener is in the background. */
class LiveListeningService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openCorus = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, "corus_playback")
            .setSmallIcon(R.drawable.ic_stat_corus)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.live_listening_live))
            .setContentIntent(openCorus)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setSilent(true)
            .build()
        try {
            ServiceCompat.startForeground(this, 1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } catch (error: IllegalStateException) {
            Log.w("LiveListening", "Could not promote Live playback service", error)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
