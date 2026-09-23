package fm.corus.android.ui.components

import android.graphics.Bitmap
import android.graphics.Color
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
class InstagramV2RenderTest {
    @Test fun `all layouts export exact story size with chosen background`() {
        val context = RuntimeEnvironment.getApplication()
        val art = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val subject = InstagramV2Subject("A long song title that needs to wrap onto another line", "An artist", null, "https://corus.fm/song/test", "https://corus.fm/post/test", "tester", "Caption with emoji 🎵 and português")
        for (layout in listOf("cover", "vinyl")) for (background in listOf("artwork", "gradient", "light", "dark")) {
            val result = renderInstagramV2(context, subject, art, 0xffb81f29.toInt(), layout, background, true)
            assertEquals(1080, result.width)
            assertEquals(1920, result.height)
            if (background == "light") assertEquals(Color.WHITE, result.getPixel(1, 1))
            if (background == "dark") assertEquals(0xff131313.toInt(), result.getPixel(1, 1))
            if (background == "artwork") assertEquals(0xffb81f29.toInt(), result.getPixel(1, 1))
            if (background == "gradient") assertNotEquals(result.getPixel(1, 1), result.getPixel(1, 1918))
            result.recycle()
        }
        art.recycle()
    }
}
