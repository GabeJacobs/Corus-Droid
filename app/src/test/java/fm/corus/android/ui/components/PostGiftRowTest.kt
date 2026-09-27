package fm.corus.android.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostGiftRowTest {
    @Test
    fun `optional note is shown only when real note content exists`() {
        assertFalse(shouldShowGiftNote(null))
        assertFalse(shouldShowGiftNote("   "))
        assertTrue(shouldShowGiftNote("Thank you"))
    }
}
