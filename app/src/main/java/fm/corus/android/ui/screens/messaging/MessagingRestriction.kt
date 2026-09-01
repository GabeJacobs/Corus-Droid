package fm.corus.android.ui.screens.messaging

import android.content.Context
import com.google.firebase.functions.FirebaseFunctionsException
import fm.corus.android.R
import fm.corus.android.data.model.MessagingRestriction

fun messagingRestrictionFrom(error: Throwable): MessagingRestriction? {
    var current: Throwable? = error
    while (current != null) {
        if (current is FirebaseFunctionsException) {
            val who = (current.details as? Map<*, *>)?.get("whoCanMessage") as? String
            MessagingRestriction.from(who)?.let { return it }
        }
        if (current.message?.contains("turned off messaging") == true) {
            return MessagingRestriction.NOBODY
        }
        current = current.cause
    }
    return null
}

fun messagingRestrictionMessage(
    context: Context,
    restriction: MessagingRestriction,
    name: String?,
): String {
    val display = name?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.messaging_restriction_name_fallback)
    val resId = when (restriction) {
        MessagingRestriction.NOBODY -> R.string.messaging_restriction_nobody
        MessagingRestriction.FOLLOWERS -> R.string.messaging_restriction_followers
        MessagingRestriction.FOLLOWING -> R.string.messaging_restriction_following
    }
    return context.getString(resId, display)
}
