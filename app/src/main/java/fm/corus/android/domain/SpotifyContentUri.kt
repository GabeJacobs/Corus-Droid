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

    /** Track id from a `spotify:track:` URI, or null for non-track URIs. */
    fun trackId(uri: String?): String? {
        val trimmed = uri?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":")
        if (parts.size < 3) return null
        if (!parts[0].equals("spotify", ignoreCase = true)) return null
        if (!parts[1].equals("track", ignoreCase = true)) return null
        return parts[2].takeIf { it.isNotEmpty() }
    }

    fun trackUrisMatch(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        if (a == b) return true
        val aId = trackId(a) ?: return false
        val bId = trackId(b) ?: return false
        return aId == bId
    }
}

/**
 * Recovery rules for a failed Spotify Connect authorize/bounce handoff.
 *
 * Outdoor / flaky-network bug: authorizeAndPlay starts audio in Spotify, App
 * Remote never connects, Corus falls back to the 30s preview, then foreground
 * reconcile adopts the still-playing Spotify stream as "external" — Spotify
 * logo + dual scrubber writers (preview + App Remote).
 */
object SpotifyHandoffRecovery {
    /** How long to refuse adopting the failed handoff URI as independent Spotify listening. */
    const val EXTERNAL_ADOPTION_SUPPRESS_MS: Long = 45_000L

    fun shouldSuppressExternalAdoption(
        reportedUri: String?,
        failedHandoffUri: String?,
        suppressUntilMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (reportedUri.isNullOrEmpty() || failedHandoffUri.isNullOrEmpty()) return false
        if (suppressUntilMs == null || nowMs >= suppressUntilMs) return false
        return SpotifyContentUri.trackUrisMatch(reportedUri, failedHandoffUri)
    }
}
