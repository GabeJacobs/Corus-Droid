package fm.corus.android.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Vanilla Application so Robolectric doesn't boot CorusApplication / Firebase.
@Config(sdk = [34], application = Application::class)
class PlayerArtworkBackdropTest {

    @Test
    fun `backdrop identity includes art url and color scheme`() {
        assertEquals(
            "https://img/a.jpg|d",
            backdropIdentity("https://img/a.jpg", darkTheme = true),
        )
        assertEquals(
            "https://img/a.jpg|l",
            backdropIdentity("https://img/a.jpg", darkTheme = false),
        )
    }

    @Test
    fun `same art url in dark vs light is a different identity so wash rebakes`() {
        // iOS task id is "url|d" / "url|l" — scheme changes must not reuse the
        // other mode's frost/tint commit.
        assertNotEquals(
            backdropIdentity("https://img/a.jpg", darkTheme = true),
            backdropIdentity("https://img/a.jpg", darkTheme = false),
        )
    }

    @Test
    fun `nil art still has a stable identity`() {
        assertEquals("nil|d", backdropIdentity(null, darkTheme = true))
    }

    @Test
    fun `light mini art bloom stays close to iOS with a slight color boost`() {
        val mini = playerFrostedArtOpacity(darkTheme = false, expansion = 0f, heavyChromeReady = true)
        val full = playerFrostedArtOpacity(darkTheme = false, expansion = 1f, heavyChromeReady = true)
        assertEquals(0.16f, mini, 0.001f)
        assertEquals(0.66f, full, 0.001f)
    }

    @Test
    fun `light mini veil is slightly under iOS white so more color shows`() {
        val mini = playerVeilOpacity(darkTheme = false, expansion = 0f)
        val full = playerVeilOpacity(darkTheme = false, expansion = 1f)
        assertEquals(0.74f, mini, 0.001f)
        // Full is a hair above iOS 0.42 for post readability on Android frost.
        assertEquals(0.50f, full, 0.001f)
    }

    @Test
    fun `light material stand-in stays a translucent white frost`() {
        assertEquals(
            0.55f,
            playerMaterialOpacity(darkTheme = false, heavyChromeReady = true),
            0.001f,
        )
    }

    @Test
    fun `dark mini is slightly more colorful than iOS black-on-black stand-in`() {
        assertEquals(
            0.18f,
            playerFrostedArtOpacity(darkTheme = true, expansion = 0f, heavyChromeReady = true),
            0.001f,
        )
        assertEquals(
            1.0f,
            playerFrostedArtOpacity(darkTheme = true, expansion = 1f, heavyChromeReady = true),
            0.001f,
        )
        assertEquals(0.60f, playerMaterialOpacity(darkTheme = true, heavyChromeReady = true), 0.001f)
        assertEquals(0.50f, playerVeilOpacity(darkTheme = true, expansion = 0f), 0.001f)
        // Full is a hair above iOS 0.27 for post readability on Android frost.
        assertEquals(0.34f, playerVeilOpacity(darkTheme = true, expansion = 1f), 0.001f)
        assertEquals(0.62f, playerGradientOpacity(darkTheme = true, expansion = 0f), 0.001f)
    }

    @Test
    fun `baked frost wash preserves landscape aspect into wash side`() {
        val src = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.RED)
        }
        val frost = bakeFrostedWashBitmap(src, washSide = 280)
        assertEquals(280, frost.width)
        assertEquals(140, frost.height)
    }
}
