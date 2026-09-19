package fm.corus.android.ui.screens.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageVideoLimitsTest {
    @Test
    fun `two minutes at 720p fits 30MB`() {
        val bytes = MessageVideoLimits.estimatedCompressedBytes(2 * 60 * 1000L)
        assertTrue(bytes < MessageVideoLimits.MAX_BYTES)
    }

    @Test
    fun `error copy is Discord-style`() {
        assertEquals("Videos need to be under 30 MB", MessageVideoException.TOO_LARGE)
        assertEquals("This video is too long. Please pick a video under 2 minutes.", MessageVideoException.TOO_LONG)
        assertEquals("You've sent a lot of videos today. Try again tomorrow.", MessageVideoException.DAILY_LIMIT)
    }

    @Test
    fun `fitsAsIs is the 30MB passthrough`() {
        assertTrue(MessageVideoLimits.fitsAsIs(20L * 1024 * 1024))
        assertEquals(false, MessageVideoLimits.fitsAsIs(31L * 1024 * 1024))
    }

    @Test
    fun `70MB original is ok when 720p estimate fits`() {
        val overCap = 70L * 1024 * 1024
        assertEquals(false, MessageVideoLimits.fitsAsIs(overCap))
        assertTrue(MessageVideoLimits.estimatedCompressedBytes(2 * 60 * 1000L) < MessageVideoLimits.MAX_BYTES)
    }
}
