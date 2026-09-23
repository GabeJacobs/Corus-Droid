package fm.corus.android.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorusDraggableSheetTest {
    @Test
    fun leftoverFlingDoesNotMoveSheetAfterChildScroll() {
        assertFalse(corusSheetTakesLeftoverFling(childConsumedVelocityY = 800f))
        assertFalse(corusSheetTakesLeftoverFling(childConsumedVelocityY = -800f))
    }

    @Test
    fun flingAtChildEdgeCanMoveSheet() {
        assertTrue(corusSheetTakesLeftoverFling(childConsumedVelocityY = 0f))
        assertTrue(corusSheetTakesLeftoverFling(childConsumedVelocityY = 0.5f))
    }
}
