package fm.corus.android.ui.screens.messaging

import android.content.Context
import com.google.firebase.functions.FirebaseFunctionsException
import fm.corus.android.R
import java.text.DateFormat
import java.util.Date

data class DMOutreachFailure(val reason: String, val retryAtMs: Long) {
    fun message(context: Context): String {
        if (reason == "messageRequestPending") return context.getString(R.string.dm_request_pending)
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, context.resources.configuration.locales[0]).format(Date(retryAtMs))
        return context.getString(if (reason == "newRecipientLimit") R.string.dm_new_recipient_limit
            else R.string.dm_outreach_restricted, time)
    }
    val canRetry: Boolean get() = reason != "messageRequestPending" && System.currentTimeMillis() >= retryAtMs

    companion object {
        fun fromDetails(details: Any?): DMOutreachFailure? {
            val map = details as? Map<*, *> ?: return null
            val reason = map["reason"] as? String ?: return null
            if (reason !in listOf("newRecipientLimit", "dmOutreachRestricted", "messageRequestPending")) return null
            if (reason == "messageRequestPending") return DMOutreachFailure(reason, 0)
            val retry = map["retryAtMs"] as? Number ?: return null
            if (!retry.toDouble().isFinite() || retry.toLong() <= 0) return null
            return DMOutreachFailure(reason, retry.toLong())
        }
        fun from(error: Throwable): DMOutreachFailure? {
            var current: Throwable? = error
            while (current != null) {
                if (current is FirebaseFunctionsException) fromDetails(current.details)?.let { return it }
                current = current.cause
            }
            return null
        }
    }
}

fun showDmSendError(context: Context, error: Throwable, fallback: Int): Long {
    val outreach = DMOutreachFailure.from(error)
    return fm.corus.android.ui.components.ToastManager.show(
        outreach?.message(context) ?: context.getString(fallback),
        durationMs = if (outreach != null) 8000 else 2000
    )
}
