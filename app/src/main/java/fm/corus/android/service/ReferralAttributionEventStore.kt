package fm.corus.android.service

import android.content.Context
import java.util.UUID
import org.json.JSONObject

/** Retains a UDL result received before login until the authenticated event queue accepts it. */
internal class ReferralAttributionEventStore(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("corus_referrals_v1", Context.MODE_PRIVATE)
    data class Event(val id: String, val outcome: String, val deferred: Boolean)

    fun capture(outcome: String, deferred: Boolean) {
        val value = JSONObject().put("id", UUID.randomUUID().toString()).put("outcome", outcome)
            .put("deferred", deferred).put("capturedAt", now())
        prefs.edit().putString("attributionEvent", value.toString()).apply()
    }

    fun pending(): Event? {
        val raw = prefs.getString("attributionEvent", null) ?: return null
        return try {
            val value = JSONObject(raw)
            if (now() - value.getLong("capturedAt") !in 0 until 7 * 86400000L) {
                prefs.edit().remove("attributionEvent").apply()
                null
            } else Event(value.getString("id"), value.getString("outcome"), value.getBoolean("deferred"))
        } catch (_: Exception) {
            prefs.edit().remove("attributionEvent").apply()
            null
        }
    }

    fun acknowledge(id: String) {
        if (pending()?.id == id) prefs.edit().remove("attributionEvent").apply()
    }
}
