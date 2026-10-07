package fm.corus.android.service

import kotlin.math.roundToLong

/** Port-ready profile_share_event V2 contract. Only wire to flagged own-profile
 * sharing. See Corus-Web/app/docs/profile-share-v2.md for action semantics. */
object ProfileShareAnalytics {
    const val EVENT = "profile_share_event"
    private val allowed = mapOf(
        "action" to setOf("opened", "dismissed", "preview_started", "preview_ready", "preview_failed", "preview_changed", "grid_changed", "background_changed", "effect_changed", "share_tapped", "export_started", "export_ready", "export_failed", "export_cancelled", "handoff_result"),
        "source" to setOf("action_row", "avatar_menu", "edit_profile", "overflow_menu", "unknown"),
        "layout" to setOf("full", "4x4", "3x3", "latest", "film", "book", "invitation"),
        "background" to setOf("blue", "invitation_blue", "purple", "rose", "orange", "green", "black", "white"),
        "effect" to setOf("none", "rain", "snow"),
        "destination" to setOf("instagram", "whatsapp", "x", "share_link", "copy_link"),
        "format" to setOf("story_image", "story_layers", "story_video", "x_image", "link"),
        "result" to setOf("accepted", "rejected", "unavailable", "copied", "activity_completed", "activity_cancelled", "activity_failed"),
        "error_code" to setOf("render_failed", "encoding_failed", "handoff_failed", "app_unavailable"),
    )
    private val uuid = Regex("^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

    data class Context(
        val sessionId: String, // Fresh random UUID per sheet, never a user ID.
        val source: String, val layout: String, val background: String, val effect: String,
        val postCount: Int? = null, val artworkCount: Int,
        val fullAvailable: Boolean, val largeAvailable: Boolean,
        val previewKind: String = "story",
    )

    private fun safe(key: String, value: String, fallback: String = "unknown") =
        if (value in allowed.getValue(key)) value else fallback

    /** Explicit allowlist: never include usernames, text, links or raw errors. */
    fun params(action: String, context: Context, extra: Map<String, Any> = emptyMap()): Map<String, Any> = buildMap {
        put("action", safe("action", action)); put("source", safe("source", context.source))
        put("share_version", 2)
        if (uuid.matches(context.sessionId)) put("share_session_id", context.sessionId.lowercase())
        put("layout", safe("layout", context.layout)); put("background", safe("background", context.background))
        put("effect", safe("effect", context.effect, "none"))
        put("preview_kind", if (context.previewKind == "link") "link" else "story")
        put("artwork_count", context.artworkCount.coerceIn(0, 28))
        context.postCount?.let { put("post_count", maxOf(0, it)) }
        put("full_available", context.fullAvailable.toString()); put("large_available", context.largeAvailable.toString())
        for (key in listOf("destination", "format", "result", "error_code")) {
            (extra[key] as? String)?.takeIf { it in allowed.getValue(key) }?.let { put(key, it) }
        }
        (extra["duration_ms"] as? Number)?.toDouble()?.takeIf { it.isFinite() }
            ?.let { put("duration_ms", maxOf(0L, it.roundToLong())) }
    }

    fun log(analytics: AnalyticsService, action: String, context: Context, extra: Map<String, Any> = emptyMap()) {
        analytics.logEvent(EVENT, params(action, context, extra))
    }

    fun discoveryParams(action: String, sessionId: String, source: String, layout: String,
        postCount: Int, postsNeeded: Int): Map<String, Any> = buildMap {
        if (action !in setOf("teaser_shown", "locked_layout_tapped")) return@buildMap
        put("action", action); put("share_version", 2)
        if (uuid.matches(sessionId)) put("share_session_id", sessionId.lowercase())
        put("source", safe("source", source))
        put("layout", if (layout in setOf("3x3", "4x4", "full")) layout else "3x3")
        put("post_count", maxOf(0, postCount)); put("posts_needed", maxOf(0, postsNeeded))
    }
}
