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
}
