package fm.corus.android.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the tab bar crowding the gesture-navigation handle on
 * non-Pixel devices.
 *
 * The bar used to apply `navigationBarsPadding()` verbatim, which hands the
 * spacing to whatever the OEM reports for the gesture strip. Pixel reports the
 * full 24dp and looked right; thinner OEM strips left the tab labels sitting on
 * top of the handle. [gestureNavBottomPadding] floors the gesture case without
 * disturbing the other two nav modes.
 */
class GestureNavBottomPaddingTest {

    @Test
    fun `thin OEM gesture strip is floored to the Pixel value`() {
        assertEquals(24.dp, gestureNavBottomPadding(16.dp))
    }

    @Test
    fun `Pixel gesture strip is left alone`() {
        assertEquals(24.dp, gestureNavBottomPadding(24.dp))
    }

    @Test
    fun `three-button nav bar is left alone`() {
        // ~48dp, already well clear of the floor. Gesture nav is the only mode
        // this fix is allowed to move.
        assertEquals(48.dp, gestureNavBottomPadding(48.dp))
    }

    @Test
    fun `no nav strip means no padding`() {
        // Gesture hint hidden: the system draws no handle and reports 0, so
        // there is nothing to clear. Padding anyway would open a dead gap.
        assertEquals(0.dp, gestureNavBottomPadding(0.dp))
    }
}
