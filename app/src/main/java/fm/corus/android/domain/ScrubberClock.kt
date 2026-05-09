package fm.corus.android.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the mini-player scrubber's position and
 * duration. Lives on its own singleton so the high-frequency time updates
 * (~4×/s while music plays) only invalidate Composables that *actually*
 * observe the scrubber — namely the mini-player bar — instead of every
 * Composable observing [NowPlayingManager.state] (feed cards, profile
 * cards, etc.) and stalling scroll.
 *
 * Writer: [NowPlayingManager]'s position-polling coroutine, driven by
 * ExoPlayer's `currentPosition` and `duration`.
 *
 * Reader: `MiniPlayerBar` (only).
 *
 * Mirrors `Corus-iOS/Cymbal-Remake/Services/ScrubberClock.swift`.
 */
object ScrubberClock {
    private val _time = MutableStateFlow(0L)
    private val _duration = MutableStateFlow(0L)

    /** Current playback position in milliseconds. */
    val time: StateFlow<Long> = _time.asStateFlow()

    /** Duration of the currently-playing item in milliseconds. */
    val duration: StateFlow<Long> = _duration.asStateFlow()

    /**
     * Update both fields. No-ops on stale values to avoid waking observers
     * unnecessarily — and on non-finite/negative values defensively.
     */
    fun update(time: Long, duration: Long) {
        if (time >= 0 && _time.value != time) _time.value = time
        if (duration > 0 && _duration.value != duration) _duration.value = duration
    }

    /** Set just the time — used after a seek so the scrubber doesn't flash. */
    fun setTime(t: Long) {
        if (t >= 0 && _time.value != t) _time.value = t
    }

    /** Wipe both fields — used on stop, track change snap-to-0, etc. */
    fun reset() {
        if (_time.value != 0L) _time.value = 0L
        if (_duration.value != 0L) _duration.value = 0L
    }
}
