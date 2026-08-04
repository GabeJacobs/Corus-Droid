package fm.corus.android.domain

/**
 * What a Spotify player-state URI actually points at.
 *
 * Corus only ever posts songs, so App Remote reporting anything other than a
 * `spotify:track:` is proof the user started it themselves in the Spotify app —
 * a podcast episode, an audiobook chapter, a local file. None of those can be a
 * Corus feed entry, a stale Corus queue hop, or a misrouted lock-screen skip, so
 * the feed must get out of the way instead of reclaiming the queue over it.
 */
enum class SpotifyContentKind {
    /** `spotify:track:…` — the only thing Corus can post, queue, or play. */
    TRACK,

    /** `spotify:ad:…` — Spotify's own interstitial. Not a user pick, not ours. */
    AD,

    /** Episode, audiobook chapter, local file — content Corus can never have started. */
    FOREIGN,

    /** Empty or not a Spotify URI at all. No verdict; leave existing behaviour alone. */
    UNKNOWN,
}

object SpotifyContentUri {
    fun kindOf(uri: String?): SpotifyContentKind {
        val trimmed = uri?.trim().orEmpty()
        if (trimmed.isEmpty()) return SpotifyContentKind.UNKNOWN
        // spotify:track:ID, spotify:episode:ID, spotify:user:NAME:playlist:ID
        val parts = trimmed.lowercase().split(":")
        if (parts.size < 3 || parts[0] != "spotify" || parts[2].isEmpty()) {
            return SpotifyContentKind.UNKNOWN
        }
        return when (parts[1]) {
            "track" -> SpotifyContentKind.TRACK
            "ad" -> SpotifyContentKind.AD
            else -> SpotifyContentKind.FOREIGN
        }
    }

    /**
     * True when Spotify is playing something Corus could never have started, so
     * the user must have picked it in the Spotify app. Podcasts are the common
     * case: there are no podcasts on Corus.
     */
    fun isUserChosenNonCorusContent(uri: String?): Boolean =
        kindOf(uri) == SpotifyContentKind.FOREIGN
}
