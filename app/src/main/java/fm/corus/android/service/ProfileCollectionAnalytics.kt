package fm.corus.android.service

import kotlin.math.roundToLong

/** Same privacy-safe contract as iOS/Web; UUID identifies a sheet, never a person. */
object ProfileCollectionAnalytics {
    const val EVENT = "profile_collection_event"
    private val allowed = mapOf(
        "action" to setOf("opened", "dismissed", "section_changed", "filter_changed", "load_succeeded", "load_failed", "empty_shown", "retry_tapped", "load_more_requested", "post_tapped", "returned_to_collection"),
        "profile_type" to setOf("self", "other"),
        "section" to setOf("trophies", "gifts", "all"),
        "media_type" to setOf("track", "movie", "book", "all"),
        "phase" to setOf("summary", "collection", "gift_details", "post"),
    )
    data class Context(val sessionId: String, val profileType: String, val section: String, val mediaType: String)
    private val uuid = Regex("^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    fun params(action: String, context: Context, details: Map<String, Any> = emptyMap()): Map<String, Any> = buildMap {
        fun safe(key: String, value: String) = if (value in allowed.getValue(key)) value else "unknown"
        put("collection_version", 1); put("action", safe("action", action))
        put("profile_type", safe("profile_type", context.profileType)); put("section", safe("section", context.section)); put("media_type", safe("media_type", context.mediaType))
        if (uuid.matches(context.sessionId)) put("collection_session_id", context.sessionId.lowercase())
        (details["phase"] as? String)?.takeIf { it in allowed.getValue("phase") }?.let { put("phase", it) }
        for (key in listOf("item_count", "trophy_count", "gift_count", "duration_ms")) {
            (details[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }?.let {
                put(key, it.coerceIn(0.0, if (key == "duration_ms") 86_400_000.0 else 1_000_000.0).roundToLong())
            }
        }
    }
}
