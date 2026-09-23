package fm.corus.android.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandingPlayerHostTest {
    @Test
    fun leftoverFlingDoesNotCollapsePlayerAfterContentScroll() {
        assertFalse(playerSheetTakesLeftoverFling(contentConsumedVelocityY = 800f))
        assertFalse(playerSheetTakesLeftoverFling(contentConsumedVelocityY = -800f))
    }

    @Test
    fun flingAtContentEdgeCanCollapsePlayer() {
        assertTrue(playerSheetTakesLeftoverFling(contentConsumedVelocityY = 0f))
        assertTrue(playerSheetTakesLeftoverFling(contentConsumedVelocityY = 0.5f))
    }
}
