package fm.corus.android.domain

/**
 * Cold-start rules for App Remote when the Spotify app is not listening.
 * A hung silent connect means further silent retries will also hang; wake
 * Spotify (interactive auth) instead. After that wake, connect needs its
 * own budget — auth must not share the play timeout.
 */
internal object SpotifyConnectWake {
    const val POST_AUTH_CONNECT_TIMEOUT_MS = 20_000L
    const val POST_AUTH_CONNECT_MAX_ATTEMPTS = 3

    fun shouldAbandonSilentRetries(error: Exception): Boolean {
        val msg = error.message?.lowercase().orEmpty()
        return msg.contains("timed out connecting")
    }

    /**
     * Extra verify time when App Remote is not already live. A leftover
     * token / last-usage timestamp from hours ago is not a live session —
     * after a long freeze those still exist and a short verify plus
     * "keep if connected" left muted keep-alive silence on screen.
     */
    fun shouldUseExtendedVerify(isLiveConnected: Boolean): Boolean = !isLiveConnected

    /**
     * play() can return success while Spotify is still asleep (Samsung
     * Freecess / long idle). [isConnected] alone is not evidence the
     * requested track is audible — keep the session only when App Remote
     * reports playing or the expected URI.
     */
    fun shouldKeepUnverifiedSession(
        isPlaying: Boolean,
        hasMatchingTrackUri: Boolean,
    ): Boolean = isPlaying || hasMatchingTrackUri

    /**
     * After play(requested), App Remote often emits the leftover native-queue
     * next (the following Corus feed entry) before the requested track is
     * current. Adopting that URI moves chrome while the tapped song is still
     * audible — and a miniplayer Next used to disable this ignore for 5s,
     * which chained Medicine → Smoke → Sabali → Ladies.
     *
     * A real Control Center Next is a next URI *after* the requested track
     * has been current for [HANDOFF_NEXT_SETTLE_MS].
     */
    const val HANDOFF_NEXT_SETTLE_MS = 1_500L

    fun shouldIgnoreStaleNextDuringHandoff(
        playIntentInFlight: Boolean,
        reportedIsNextQueueEntry: Boolean,
        requestedTrackConfirmed: Boolean,
        confirmedForMs: Long = 0L,
    ): Boolean {
        if (!playIntentInFlight || !reportedIsNextQueueEntry) return false
        if (!requestedTrackConfirmed) return true
        return confirmedForMs < HANDOFF_NEXT_SETTLE_MS
    }

    /**
     * Miniplayer Next sets a 5s feed-skip window so the *target* can land.
     * An unexpected leftover URI in that window is not another Next — only
     * force-advance once the requested track has settled.
     */
    fun shouldForceAdvanceOnUnexpectedDuringFeedSkip(
        playIntentInFlight: Boolean,
        requestedTrackConfirmed: Boolean,
        confirmedForMs: Long = 0L,
    ): Boolean {
        if (!playIntentInFlight) return true
        if (!requestedTrackConfirmed) return false
        return confirmedForMs >= HANDOFF_NEXT_SETTLE_MS
    }
}
