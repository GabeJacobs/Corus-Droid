package fm.corus.android.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveVoiceMeterTest {
    @Test
    fun conversationalLevelsUseMoreOfTheMeter() {
        assertEquals(4, liveVoiceMeterBars(.025f))
        assertEquals(6, liveVoiceMeterBars(.070f))
        assertEquals(7, liveVoiceMeterBars(.125f))
    }

    @Test
    fun silenceStaysUnlitAndLoudInputStopsAtEightBars() {
        assertEquals(0, liveVoiceMeterBars(0f))
        assertEquals(0, liveVoiceMeterBars(.001f))
        assertEquals(8, liveVoiceMeterBars(.3f))
        assertEquals(8, liveVoiceMeterBars(1f))
    }
}
