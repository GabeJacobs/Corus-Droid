package fm.corus.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CountFormatTest {

    @Test
    fun `under ten thousand renders the full number`() {
        assertEquals("0", formattedCount(0))
        assertEquals("829", formattedCount(829))
        assertEquals("1436", formattedCount(1_436))
        assertEquals("7240", formattedCount(7_240))
        assertEquals("9999", formattedCount(9_999))
    }

    @Test
    fun `ten thousand and up abbreviates with one decimal place`() {
        assertEquals("10K", formattedCount(10_000))
        assertEquals("61.6K", formattedCount(61_600))
        assertEquals("500K", formattedCount(500_000))
    }

    @Test
    fun `millions keep one decimal place`() {
        assertEquals("1M", formattedCount(1_000_000))
        assertEquals("1.2M", formattedCount(1_200_000))
    }
}
