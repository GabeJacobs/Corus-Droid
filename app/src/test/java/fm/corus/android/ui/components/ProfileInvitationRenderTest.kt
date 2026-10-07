package fm.corus.android.ui.components

import android.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileInvitationRenderTest {
    @Test fun `empty profiles center the invitation instead of leaving the old header`() = runBlocking {
        val bitmap = generateProfileStoriesCardBitmap(RuntimeEnvironment.getApplication(),
            ShareProfileSubject("fixture", "farleythethird", "farleythethird", null), ShareCardTheme.LIGHT, profileSharingV2 = true)
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(1920, bitmap.height)
            var headerInk = 0
            for (y in 160 until 420) for (x in 100 until 980) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) < 160 && Color.green(pixel) < 160 && Color.blue(pixel) < 160) headerInk++
            }
            assertEquals("The invitation should be one centered identity, with no duplicate header.", 0, headerInk)
        } finally { bitmap.recycle() }
    }
    @Test fun `flag off keeps the legacy identity header`() = runBlocking {
        val bitmap = generateProfileStoriesCardBitmap(RuntimeEnvironment.getApplication(),
            ShareProfileSubject("fixture", "farleythethird", "farleythethird", null), ShareCardTheme.LIGHT)
        try {
            var headerInk = 0
            for (y in 160 until 420) for (x in 100 until 980) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) < 160 && Color.green(pixel) < 160 && Color.blue(pixel) < 160) headerInk++
            }
            assertTrue("Unmarked exports must retain the legacy header.", headerInk > 1500)
        } finally { bitmap.recycle() }
    }

}
