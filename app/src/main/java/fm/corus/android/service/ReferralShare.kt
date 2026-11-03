package fm.corus.android.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID
import kotlinx.coroutines.*

/** The chooser callback reports a completed handoff; dismissing it records no completion. */
fun Context.shareReferral(text: String, source: String) {
    val callback =
        Intent(this, ReferralShareReceiver::class.java)
            .setAction("fm.corus.android.REFERRAL_SHARED.${UUID.randomUUID()}")
            .putExtra("owner", FirebaseAuth.getInstance().currentUser?.uid)
            .putExtra("source", source)
    val sender =
        PendingIntent.getBroadcast(
                this,
                0,
                callback,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )
            .intentSender
    startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            null,
            sender,
        )
    )
}

class ReferralShareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (intent.getStringExtra("owner") != uid || !ReferralService.enabled.value) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                ReferralService.track(
                    "share_completed",
                    source = intent.getStringExtra("source") ?: "unknown",
                )
            } finally {
                pending.finish()
            }
        }
    }
}
