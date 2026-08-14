package fm.corus.android.domain

/**
 * When to pre-arm Spotify's delegate fast-path for the upcoming Corus feed
 * track. Mirrors iOS `refreshSpotifyFastPathSkipGuardForUpcomingTrackIfNeeded`:
 * lock screen, background, and the last few seconds of natural playback.
 */
internal object SpotifyConnectFastPath {
    const val NEAR_END_LEAD_SEC = 5.0
    const val LOCKED_GUARD_MS = 6_000L
    const val NEAR_END_GUARD_MS = 10_000L
    /** Wait for Liked Songs title/context before playing Corus next. */
    const val AWAY_SKIP_DECISION_MS = 80L

    fun isNearEnd(positionSec: Double, durationSec: Double): Boolean =
        durationSec > 0 && positionSec >= durationSec - NEAR_END_LEAD_SEC

    fun shouldArmUpcomingSkipGuard(
        connectPlaying: Boolean,
        hasNext: Boolean,
        feedSkipInFlight: Boolean,
        playIntentInFlight: Boolean,
        lockedOrBackgrounded: Boolean,
        positionSec: Double,
        durationSec: Double,
    ): Boolean {
        if (!connectPlaying || !hasNext) return false
        if (feedSkipInFlight || playIntentInFlight) return false
        return lockedOrBackgrounded || isNearEnd(positionSec, durationSec)
    }

    fun guardDurationMs(lockedOrBackgrounded: Boolean): Long =
        if (lockedOrBackgrounded) LOCKED_GUARD_MS else NEAR_END_GUARD_MS

    /**
     * Only the lock screen should immediately play the expected Corus URI on a
     * misrouted skip. Unlocked (including Spotify-app picks) wait for context
     * so a Liked Song isn't stolen.
     */
    fun shouldPlayExpectedOnMisroute(locked: Boolean): Boolean = locked

    /** Unlocked Control Center Next must mute immediately; lock screen plays expected. */
    fun shouldMuteUnexpectedWhenUnlocked(locked: Boolean): Boolean = !locked

    /**
     * Android ON_PAUSE (user opened Spotify) is still STARTED — iOS `.inactive`
     * is lock/Control Center, not an app switch. Never force-advance an
     * unexpected track just because Corus isn't resumed.
     */
    fun shouldForceAdvanceForAppState(locked: Boolean): Boolean = locked

    /**
     * Liked Songs / album taps change context. Both sides must be known —
     * a missing previous context is not enough to call it a pick (Control
     * Center Next keeps the Corus context and often delivers no new event).
     */
    fun contextLooksLikeManualPick(previous: String?, current: String?): Boolean {
        if (previous.isNullOrEmpty() || current.isNullOrEmpty()) return false
        return previous != current
    }

    /**
     * Same context **after** the post-track-change context event.
     * Do not pass the pre-change [currentContextUri] — that looks unchanged
     * and hijacks a Liked Song before the new context arrives.
     */
    fun contextUnchanged(previous: String?, incomingAfterTrackChange: String?): Boolean {
        if (previous.isNullOrEmpty() || incomingAfterTrackChange.isNullOrEmpty()) {
            return false
        }
        return previous == incomingAfterTrackChange
    }

    fun isTrackLevelContext(uri: String?): Boolean =
        uri?.startsWith("spotify:track:") == true

    /**
     * User tapped something in the Spotify app (Liked Songs, album, artist).
     * Control Center Next often lands in a `spotify:playlist:` mix — that is
     * not a library pick and must not roll back a Corus force-advance.
     */
    fun contextLooksLikeLibraryPick(
        uri: String?,
        type: String? = null,
        title: String? = null,
    ): Boolean {
        if (type.equals("collection", ignoreCase = true)) return true
        val named = title?.lowercase().orEmpty()
        if (named.contains("liked songs") ||
            named.contains("liked song") ||
            named.contains("canciones que te gustan") ||
            named.contains("músicas curtidas") ||
            named.contains("musicas curtidas")
        ) {
            return true
        }
        if (uri.isNullOrEmpty()) return false
        if (uri.contains(":collection")) return true
        if (uri.startsWith("spotify:album:")) return true
        if (uri.startsWith("spotify:artist:")) return true
        if (uri.contains(":search")) return true
        return false
    }

    /**
     * After the context wait: a changed context is a Spotify-app pick.
     * Same album/playlist, or no context event, is Control Center Next.
     * Same `spotify:track:` context is often a stale first event before
     * Liked Songs delivers `collection` — do not force on that yet.
     */
    fun shouldForceAdvanceWhenAway(
        awaitingContext: Boolean,
        previousContext: String?,
        incomingContext: String?,
    ): Boolean {
        if (awaitingContext) return false
        if (contextLooksLikeManualPick(previousContext, incomingContext)) return false
        if (incomingContext == null) return true
        if (isTrackLevelContext(previousContext) && previousContext == incomingContext) {
            return false
        }
        return true
    }
}
