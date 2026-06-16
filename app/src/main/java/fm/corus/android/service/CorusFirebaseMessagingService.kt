package fm.corus.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import fm.corus.android.MainActivity
import fm.corus.android.R
import fm.corus.android.domain.PostEngagementManager
import javax.inject.Inject

@AndroidEntryPoint
class CorusFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var postEngagementManager: PostEngagementManager

    companion object {
        private const val CHANNEL_ID = "corus_default"
        private const val CHANNEL_NAME = "Corus"
        // Dedicated channel for play-milestone pushes so users can mute just
        // these from system settings without silencing all of Corus.
        private const val CHANNEL_ID_PLAYS = "corus_plays"
        private const val CHANNEL_NAME_PLAYS = "Plays"
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_NOTIF_PREFIX = "notif_"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users_v2")
            .document(uid)
            .update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val notification = message.notification
        val data = message.data

        refreshPostCountsIfNeeded(data)

        if (notification != null) {
            // System would auto-display this, but we need to attach navigation data.
            // Build a custom notification with a PendingIntent that carries the data payload.
            showNotification(
                title = notification.title ?: "",
                body = notification.body ?: "",
                data = data,
            )
        } else if (data.isNotEmpty()) {
            // Data-only message — build notification from data fields.
            showNotification(
                title = data["title"] ?: "",
                body = data["body"] ?: "",
                data = data,
            )
        }
    }

    /** When an engagement-type notification (comment/like/repost/etc.) arrives
     *  for one of the viewer's posts, eagerly refresh that post's denormalized
     *  counts so the feed badge updates without waiting for pull-to-refresh.
     *  Mirrors iOS AppDelegate.refreshPostCountsIfNeeded. */
    private fun refreshPostCountsIfNeeded(data: Map<String, String>) {
        val type = data["type"].orEmpty()
        val postId = data["postId"].orEmpty()
        if (postId.isEmpty()) return
        when (type) {
            "comment", "reply", "mention", "like", "repost" -> {
                postEngagementManager.refreshCountsFromServer(postId)
            }
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_FROM_NOTIFICATION, true)
            data.forEach { (key, value) -> putExtra("$EXTRA_NOTIF_PREFIX$key", value) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Route play-milestone pushes to their own channel so they can be muted
        // independently in system settings.
        val isPlay = data["type"] == "play_milestone"
        val channelId = if (isPlay) CHANNEL_ID_PLAYS else CHANNEL_ID
        val channelName = if (isPlay) CHANNEL_NAME_PLAYS else CHANNEL_NAME

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.logo_no_background)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (required for Android O+, no-op if already exists)
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)

        manager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
