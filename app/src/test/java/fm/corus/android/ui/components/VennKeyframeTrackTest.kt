package fm.corus.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The venn collision loop's keyframe engine must behave like the CSS it ports
 * (corus-venn-* in the web app's globals.css): boundary values hold outside
 * the stop range (fill-mode both), segments ease-in-out between stops, and
 * animation-delay wraps every iteration of an infinite loop.
 */
class VennKeyframeTrackTest {

    @Test
    fun `values hold at the boundaries`() {
        val track = KeyframeTrack(0.2f to 10f, 0.8f to 20f)
        assertEquals(10f, track.at(0f), 1e-4f)
        assertEquals(10f, track.at(0.2f), 1e-4f)
        assertEquals(20f, track.at(0.8f), 1e-4f)
        assertEquals(20f, track.at(1f), 1e-4f)
    }

    @Test
    fun `segment midpoint eases through the halfway value`() {
        // cubic-bezier(0.42, 0, 0.58, 1) is symmetric — t=0.5 maps to 0.5.
        val track = KeyframeTrack(0f to 0f, 1f to 100f)
        assertEquals(50f, track.at(0.5f), 0.5f)
    }

    @Test
    fun `easing is slower than linear near the segment start`() {
        val track = KeyframeTrack(0f to 0f, 1f to 100f)
        val early = track.at(0.15f)
        // ease-in-out undershoots linear early in the segment.
        assert(early < 15f) { "expected ease-in (< linear), got $early" }
    }

    @Test
    fun `multi-stop track picks the correct segment`() {
        // The lobe slide: parked until 16%, together by 32%, holds after.
        val track = KeyframeTrack(0f to 0f, 0.16f to 0f, 0.32f to 1f, 1f to 1f)
        assertEquals(0f, track.at(0.10f), 1e-4f)
        assertEquals(1f, track.at(0.32f), 1e-4f)
        assertEquals(1f, track.at(0.60f), 1e-4f)
        val mid = track.at(0.24f)
        assert(mid > 0f && mid < 1f) { "mid-slide should interpolate, got $mid" }
    }

    @Test
    fun `zero-length segment jumps to the end value`() {
        val track = KeyframeTrack(0.5f to 1f, 0.5f to 2f, 1f to 2f)
        assertEquals(2f, track.at(0.5f), 1e-4f)
    }

    @Test
    fun `staggered fraction wraps the delay each iteration`() {
        // No delay = identity.
        assertEquals(0.3f, staggeredFraction(0.3f, 0f), 1e-4f)
        // Past the delay the element runs behind the master by the delay.
        assertEquals(0.28f, staggeredFraction(0.3f, 0.02f), 1e-4f)
        // Before the delay elapses the element sits at the END of the cycle
        // (the wrapped phase), matching an infinite CSS animation mid-stream.
        assertEquals(0.99f, staggeredFraction(0.01f, 0.02f), 1e-4f)
    }

    @Test
    fun `first cycle clamps delayed elements at their from-state instead of wrapping`() {
        // wrap=false = the FIRST loop (CSS fill-backwards): before its delay
        // elapses the element holds fraction 0 — the regression had match
        // avatars rendering their END-of-loop settled state (visible, mid-lens)
        // on the opening frames because the wrap put 0.01-0.02 at 0.99.
        assertEquals(0f, staggeredFraction(0.01f, 0.02f, wrap = false), 1e-4f)
        assertEquals(0f, staggeredFraction(0f, 0.18f, wrap = false), 1e-4f)
        // Past the delay, identical to the wrapped steady state.
        assertEquals(0.28f, staggeredFraction(0.3f, 0.02f, wrap = false), 1e-4f)
    }
}
