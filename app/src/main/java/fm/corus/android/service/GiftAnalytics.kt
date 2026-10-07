package fm.corus.android.service

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.functions.FirebaseFunctionsException
import fm.corus.android.data.repository.GiftRepositoryException

/** Matches iOS/Web gift_event. Never send note text, usernames, IDs or raw errors. */
object GiftAnalytics {
    fun params(action: String, source: String, giftType: String? = null,
               hasNote: Boolean? = null, available: Int? = null, capacity: Int? = null,
               result: String? = null, errorCode: String? = null): Map<String, Any> = buildMap {
        put("action", action); put("source", source)
        giftType?.let { put("gift_type", it) }
        hasNote?.let { put("has_note", it.toString()) }
        available?.let { put("available", it) }
        capacity?.let { put("capacity", it) }
        result?.let { put("result", it) }
        errorCode?.let { put("error_code", it) }
    }

    fun log(context: Context, action: String, source: String, giftType: String? = null, hasNote: Boolean? = null, result: String? = null, errorCode: String? = null) {
        val bundle = Bundle().apply {
            params(action, source, giftType, hasNote, result = result, errorCode = errorCode).forEach { (key, value) -> putString(key, value.toString()) }
        }
        FirebaseAnalytics.getInstance(context.applicationContext).logEvent("gift_event", bundle)
    }

    fun errorCode(error: Exception): String = when (error) {
        is GiftRepositoryException.NoSlots -> "resource-exhausted"
        is GiftRepositoryException.Unavailable -> "failed-precondition"
        is FirebaseFunctionsException -> error.code.name.lowercase().replace('_', '-')
        else -> "unknown"
    }
}
