package fm.corus.android.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidButtonNavigationTest {

    @Test
    fun `gesture mode is never button style`() {
        assertFalse(
            isButtonStyleNavigation(
                interactionMode = NAV_BAR_INTERACTION_MODE_GESTURE,
                navInset = 48.dp,
            ),
        )
    }

    @Test
    fun `three and two button modes are button style`() {
        assertTrue(
            isButtonStyleNavigation(
                interactionMode = NAV_BAR_INTERACTION_MODE_THREE_BUTTON,
                navInset = 0.dp,
            ),
        )
        assertTrue(
            isButtonStyleNavigation(
                interactionMode = NAV_BAR_INTERACTION_MODE_TWO_BUTTON,
                navInset = 0.dp,
            ),
        )
    }

    @Test
    fun `inset fallback treats tall bars as buttons and thin as gesture`() {
        assertTrue(
            isButtonStyleNavigation(interactionMode = null, navInset = 48.dp),
        )
        assertFalse(
            isButtonStyleNavigation(interactionMode = null, navInset = 24.dp),
        )
        assertFalse(
            isButtonStyleNavigation(interactionMode = null, navInset = 0.dp),
        )
    }
}
