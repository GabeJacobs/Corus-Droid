package fm.corus.android.ui.components

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
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

    @Test
    fun `gift count and sender names are bold while connecting copy stays regular`() {
        val styled = emphasizedGiftAttribution(
            "__COUNT__ from __FIRST__ and __SECOND__",
            linkedMapOf(
                "__COUNT__" to "2 gifts",
                "__FIRST__" to "farleythethird",
                "__SECOND__" to "gabe",
            ),
        )

        assertEquals("2 gifts from farleythethird and gabe", styled.text)
        assertEquals(
            listOf("2 gifts", "farleythethird", "gabe"),
            styled.spanStyles
                .filter { it.item.fontWeight == FontWeight.Bold }
                .map { styled.text.substring(it.start, it.end) },
        )
    }
}
