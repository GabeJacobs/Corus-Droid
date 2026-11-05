package fm.corus.android.data.model

/** Server proof belongs to the full source recording, never just a title/id. */
data class QobuzMatchInput(
    val isrc: String, val name: String, val artist: String,
    val albumName: String, val durationMs: Int,
) {
    fun confirmedUrl(track: CymbalTrack): String? = track.qobuzURL?.takeIf {
        it.matches(Regex("https://open\\.qobuz\\.com/track/[1-9][0-9]*")) &&
            isrc == track.isrc.orEmpty().trim().uppercase(java.util.Locale.ROOT) &&
            name == track.name.trim() && artist == track.artistName.trim() &&
            albumName == track.albumName.trim() && durationMs == track.durationMs
    }
    companion object {
        fun fromMap(value: Any?): QobuzMatchInput? {
            val m = value as? Map<*, *> ?: return null
            return QobuzMatchInput(m["isrc"] as? String ?: return null,
                m["name"] as? String ?: return null, m["artist"] as? String ?: return null,
                m["albumName"] as? String ?: return null, (m["durationMs"] as? Number)?.toInt() ?: return null)
        }
    }
}
