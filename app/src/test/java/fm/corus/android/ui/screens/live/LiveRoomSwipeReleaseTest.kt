package fm.corus.android.ui.screens.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRoomSwipeReleaseTest {
    @Test
    fun shortHeldPullKeepsRoomOpen() {
        assertFalse(shouldMinimizeLiveRoom(180f, 0f, 1600f, 1100f))
    }

    @Test
    fun pullPastPlayerThresholdMinimizesRoom() {
        assertFalse(shouldMinimizeLiveRoom(719f, 0f, 1600f, 1100f))
        assertTrue(shouldMinimizeLiveRoom(720f, 0f, 1600f, 1100f))
    }

    @Test
    fun quickDownwardFlickMinimizesAndUpwardFlickKeepsRoomOpen() {
        assertTrue(shouldMinimizeLiveRoom(80f, 1200f, 1600f, 1100f))
        assertFalse(shouldMinimizeLiveRoom(800f, -1200f, 1600f, 1100f))
    }
}
