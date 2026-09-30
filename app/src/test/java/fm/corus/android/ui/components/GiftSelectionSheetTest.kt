package fm.corus.android.ui.components

import fm.corus.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
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

    @Test
    fun `user scroll hides the keyboard without consuming the gesture`() {
        var hidden = 0
        val connection = HideKeyboardOnScrollConnection(isDragging = { true }) { hidden++ }
        val consumed = connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(1, hidden)
        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `focus relocation labeled UserInput keeps the keyboard open`() {
        var hidden = 0
        val connection = HideKeyboardOnScrollConnection(isDragging = { false }) { hidden++ }
        val consumed = connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(0, hidden)
        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `programmatic or horizontal scroll leaves the keyboard alone`() {
        var hidden = 0
        val connection = HideKeyboardOnScrollConnection(isDragging = { true }) { hidden++ }
        connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.SideEffect)
        connection.onPreScroll(Offset(30f, 0f), NestedScrollSource.UserInput)
        assertEquals(0, hidden)
    }
}
