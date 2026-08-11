package fm.corus.android.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FullPlayerScrubberKnobTest {

    @Test
    fun `knob starts flush with the leading edge at zero`() {
        assertEquals(0f, scrubberKnobStartPx(300f, fraction = 0f, knobSizePx = 12f), 0.001f)
    }

    @Test
    fun `knob ends flush with the trailing edge at one — no overshoot`() {
        val track = 300f
        val knob = 12f
        val start = scrubberKnobStartPx(track, fraction = 1f, knobSizePx = knob)
        assertEquals(track - knob, start, 0.001f)
        assertEquals(track, start + knob, 0.001f)
    }

    @Test
    fun `knob center tracks mid-track at half`() {
        val track = 300f
        val knob = 12f
        val start = scrubberKnobStartPx(track, fraction = 0.5f, knobSizePx = knob)
        assertEquals(track / 2f, start + knob / 2f, 0.001f)
    }
}
