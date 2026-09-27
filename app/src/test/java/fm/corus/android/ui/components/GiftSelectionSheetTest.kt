package fm.corus.android.ui.components

import fm.corus.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftSelectionSheetTest {
    @Test
    fun `gift controls are disabled when inventory is exhausted`() {
        assertFalse(areGiftControlsEnabled(available = 0))
        assertTrue(areGiftControlsEnabled(available = 1))
    }

    @Test
    fun `exhausted inventory uses the empty Gift intro`() {
        assertEquals(R.string.gift_picker_empty_intro, giftPickerIntroResource(available = 0))
        assertEquals(R.string.gift_picker_intro, giftPickerIntroResource(available = 1))
        assertEquals(R.string.gift_picker_intro, giftPickerIntroResource(available = null))
    }

    @Test
    fun `club offer only appears below premium capacity`() {
        assertTrue(shouldShowGiftClubOffer(capacity = 1))
        assertFalse(shouldShowGiftClubOffer(capacity = 3))
    }
}
