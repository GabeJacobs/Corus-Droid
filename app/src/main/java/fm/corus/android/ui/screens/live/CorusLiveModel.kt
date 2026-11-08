package fm.corus.android.ui.screens.live

internal data class LiveTrack(
    val id: String = "", val title: String = "", val artist: String = "",
    val source: String = "spotify", val durationMs: Long = 0,
    val artwork: String? = null, val isrc: String? = null,
    val appleMusicId: String? = null, val spotifyURI: String? = null,
) {
    fun payload(): Map<String, Any> = buildMap {
        put("id", id); put("title", title); put("artist", artist); put("source", source); put("durationMs", durationMs)
        artwork?.let { put("artwork", it) }; isrc?.let { put("isrc", it) }
        appleMusicId?.let { put("appleMusicId", it) }; spotifyURI?.let { put("spotifyURI", it) }
    }
    companion object {
        fun parse(data: Map<*, *>): LiveTrack = LiveTrack(
            data["id"] as? String ?: "", data["title"] as? String ?: "", data["artist"] as? String ?: "",
            data["source"] as? String ?: "spotify", (data["durationMs"] as? Number)?.toLong() ?: 0,
            data["artwork"] as? String, data["isrc"] as? String, data["appleMusicId"] as? String, data["spotifyURI"] as? String,
        )
    }
}
internal data class LiveRoom(
    val id: String, val hostId: String, val username: String, val avatar: String?, val title: String?,
    val status: String, val current: LiveTrack?, val queue: List<LiveTrack>, val revision: Long,
    val queueVersion: Long, val startedAt: Long, val deadline: Long, val leaseUntil: Long,
    val pausedAt: Long?, val voiceMode: String, val voiceEnabled: Boolean, val voiceSession: String?,
) {
    fun position(serverNow: Long): Double = (((pausedAt ?: serverNow) - startedAt) / 1000.0)
        .coerceIn(0.0, (current?.durationMs ?: 0) / 1000.0)
    fun musicShouldPlay(now: Long) = status == "playing" && current != null && pausedAt == null && voiceMode != "talk" && deadline > now && leaseUntil > now
    companion object {
        fun parse(id: String, d: Map<String, Any?>): LiveRoom {
            fun number(k: String) = (d[k] as? Number)?.toLong() ?: 0
            return LiveRoom(id, d["hostId"] as? String ?: "", d["hostUsername"] as? String ?: d["hostName"] as? String ?: "Corus",
                d["hostAvatar"] as? String, d["title"] as? String, d["status"] as? String ?: "ended",
                (d["current"] as? Map<*, *>)?.let(LiveTrack::parse), (d["queue"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(LiveTrack::parse) } ?: emptyList(),
                number("revision"), number("queueVersion"), number("startedAt"), number("deadline"), number("leaseUntil"),
                (d["pausedAt"] as? Number)?.toLong(), d["voiceMode"] as? String ?: "music", d["voiceEnabled"] == true, d["voiceSession"] as? String)
        }
    }
}
