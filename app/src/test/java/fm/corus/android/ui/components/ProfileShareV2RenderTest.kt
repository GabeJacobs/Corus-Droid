package fm.corus.android.ui.components

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import fm.corus.android.localization.CorusStrings
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileShareV2RenderTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val profile = ShareProfileSubject("fixture", "gabe", "Gabe", null, postCount = 28)
    private fun fixtureArt() = PreparedProfileShareArt(List(28) { index ->
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30 + index * 7, 40, 100)) }
    })

    @Test fun `username and branding are white on colored and black backgrounds across story and X`() {
        val art = fixtureArt()
        try {
            for (background in listOf(ProfileStoryBackground.CORUS_BLUE, ProfileStoryBackground.ROSE,
                ProfileStoryBackground.PURPLE, ProfileStoryBackground.ORANGE,
                ProfileStoryBackground.GREEN, ProfileStoryBackground.DARK)) {
                val story = renderProfileShareStory(context, profile, art, ProfileStoryGridSize.STANDARD, background, transparent = true)
                val full = renderProfileShareStory(context, profile, art, ProfileStoryGridSize.FULL, background)
                val x = renderProfileShareX(context, profile, art, ProfileStoryGridSize.STANDARD, background)
                try {
                    val handleTop = profileStoryGeometry(ProfileStoryGridSize.STANDARD).grid.bottom.toInt() + 64
                    assertTrue("$background Story username must be opaque white", whitePixels(story, 350, handleTop, 730, handleTop + 49) > 100)
                    assertTrue("$background Story branding must be white", whitePixels(story, 300, handleTop + 70, 780, handleTop + 180) > 100)
                    assertTrue("$background Full pill username must be white", whitePixels(full, 550, 1660, 800, 1760) > 100)
                    assertTrue("$background X pill username must be white", whitePixels(x, 550, 995, 800, 1052) > 50)
                } finally { story.recycle(); full.recycle(); x.recycle() }
            }
        } finally { art.slots.forEach { it?.recycle() } }
    }

    private fun whitePixels(image: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) for (x in left until right) {
            val pixel = image.getPixel(x, y)
            if (Color.alpha(pixel) > 240 && Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240) count++
        }
        return count
    }

    @Test fun `Full eagerly draws all 28 squares including the last one and reflows X`() {
        val art = fixtureArt()
        val story = renderProfileShareStory(context, profile, art, ProfileStoryGridSize.FULL, ProfileStoryBackground.CORUS_BLUE)
        val x = renderProfileShareX(context, profile, art, ProfileStoryGridSize.FULL, ProfileStoryBackground.CORUS_BLUE)
        try {
            assertEquals(1080, story.width); assertEquals(1920, story.height)
            assertEquals(1400, x.width); assertEquals(800, x.height)
            assertEquals(art.slots[27]!!.getPixel(0, 0), story.getPixel(960, 1850))
            assertEquals(art.slots[27]!!.getPixel(0, 0), x.getPixel(1350, 750))
            assertEquals("Full must not expose hairline background seams between tiles", 40, Color.green(story.getPixel(265, 400)))
            save("full-story", story); save("full-x", x)
        } finally { story.recycle(); x.recycle(); art.slots.forEach { it?.recycle() } }
    }

    @Test fun `standard preview flattened video base and compact sticker use identical artwork geometry`() {
        val art = fixtureArt()
        val flat = renderProfileShareStory(context, profile, art, ProfileStoryGridSize.STANDARD, ProfileStoryBackground.CORUS_BLUE)
        val foreground = renderProfileShareStory(context, profile, art, ProfileStoryGridSize.STANDARD, ProfileStoryBackground.CORUS_BLUE, transparent = true)
        val sticker = renderProfileShareStory(context, profile, art, ProfileStoryGridSize.STANDARD, ProfileStoryBackground.CORUS_BLUE, transparent = true, compactForeground = true)
        val composed = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply { eraseColor(ProfileStoryBackground.CORUS_BLUE.color) }
        Canvas(composed).drawBitmap(foreground, 0f, 0f, null)
        try {
            assertTrue(flat.sameAs(composed))
            val geometry = profileStoryGeometry(ProfileStoryGridSize.STANDARD)
            assertEquals(936f, geometry.grid.width())
            assertEquals(flat.getPixel(100, geometry.grid.top.toInt() + 100), sticker.getPixel(100, 172))
            assertEquals(0, Color.alpha(sticker.getPixel(0, 0)))
            assertEquals(Color.WHITE, profileShareInk(ProfileStoryBackground.CORUS_BLUE))
            save("3x3-story", flat); save("3x3-sticker", sticker)
        } finally { listOf(flat, foreground, sticker, composed).forEach { it.recycle() }; art.slots.forEach { it?.recycle() } }
    }

    @Test fun `missing artwork keeps its cell without shifting the last square`() {
        val art = fixtureArt()
        val missing = art.copy(slots = art.slots.mapIndexed { index, bitmap -> if (index == 6) null else bitmap })
        val image = renderProfileShareStory(context, profile, missing, ProfileStoryGridSize.LARGE, ProfileStoryBackground.LIGHT)
        try {
            val g = profileStoryGeometry(ProfileStoryGridSize.LARGE)
            assertEquals(0xffdedee3.toInt(), image.getPixel(72 + 2 * 234 + 100, g.grid.top.toInt() + 234 + 100))
            assertEquals(art.slots[15]!!.getPixel(0, 0), image.getPixel(900, g.grid.top.toInt() + 800))
        } finally { image.recycle(); art.slots.forEach { it?.recycle() } }
    }

    @Test fun `five by five uses exactly 25 squares with matching preview layers video base and X`() {
        val art = fixtureArt()
        val layout = ProfileStoryGridSize.EXTRA_LARGE
        val background = ProfileStoryBackground.CORUS_BLUE
        val geometry = profileStoryGeometry(layout)
        assertEquals(5, geometry.columns); assertEquals(5, geometry.rows)
        assertEquals(936f, geometry.grid.width())
        assertEquals(profileStoryGeometry(ProfileStoryGridSize.STANDARD).grid, geometry.grid)
        val flat = renderProfileShareStory(context, profile, art, layout, background)
        val foreground = renderProfileShareStory(context, profile, art, layout, background, transparent = true)
        val sticker = renderProfileShareStory(context, profile, art, layout, background, transparent = true, compactForeground = true)
        val x = renderProfileShareX(context, profile, art, layout, background)
        val composed = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply { eraseColor(background.color) }
        Canvas(composed).drawBitmap(foreground, 0f, 0f, null)
        try {
            assertTrue("Flattened video base is exactly the preview over its background", flat.sameAs(composed))
            val side = geometry.grid.width() / 5
            for (index in 0 until 25) {
                val column = index % 5; val row = index / 5
                val px = (geometry.grid.left + (column + .5f) * side).toInt()
                val py = (geometry.grid.top + (row + .5f) * side).toInt()
                val expected = art.slots[index]!!.getPixel(0, 0)
                assertEquals("Story square $index", expected, flat.getPixel(px, py))
                assertEquals("Sticker square $index", expected, sticker.getPixel(px, (72 + (row + .5f) * side).toInt()))
                assertEquals("X square $index", expected, x.getPixel(column * 216 + 108, row * 216 + 60))
            }
            assertEquals(1080, x.width); assertEquals(1080, x.height)
            assertEquals(0, Color.alpha(sticker.getPixel(0, 0)))
            save("5x5-story", flat); save("5x5-sticker", sticker); save("5x5-x", x)
        } finally {
            listOf(flat, foreground, sticker, x, composed).forEach { it.recycle() }
            art.slots.forEach { it?.recycle() }
        }
    }

    @Test fun `film poster is centered without cropping to a square`() {
        val poster = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val image = renderProfileShareStory(context, profile.copy(featuredMoviePosterUrl = "fixture"),
            PreparedProfileShareArt(listOf(poster)), ProfileStoryGridSize.STANDARD, ProfileStoryBackground.CORUS_BLUE)
        try {
            val g = profileStoryGeometry(ProfileStoryGridSize.STANDARD, film = true)
            assertEquals(ProfileStoryBackground.CORUS_BLUE.color, image.getPixel(220, g.grid.centerY().toInt()))
            assertEquals(Color.RED, image.getPixel(540, g.grid.centerY().toInt()))
            save("film-story", image)
        } finally { image.recycle(); poster.recycle() }
    }

    @Test fun `native Instagram handoff preserves layers and grants both URIs`() {
        val bg = Uri.parse("content://fixture/bg"); val sticker = Uri.parse("content://fixture/sticker")
        val intent = profileInstagramIntent(ProfileSharePayload(bg, sticker, mime = "image/png", format = "story_layers"), "https://corus.fm", "fixture")
        assertEquals("com.instagram.android", intent.`package`)
        assertEquals(bg, intent.data); assertEquals("image/png", intent.type)
        assertEquals(sticker, intent.getParcelableExtra("interactive_asset_uri", Uri::class.java))
        assertEquals(2, intent.clipData!!.itemCount)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun `X targets native composer with image caption and attributed link without accidental username mention`() {
        val image = Uri.parse("content://fixture/image")
        val link = profileV2ShareLink(profile, "x")
        val intent = profileXSendIntent(context.getString(CorusStrings.profile_share_x_caption), link, image)
        assertEquals("com.twitter.android", intent.`package`)
        assertEquals("image/jpeg", intent.type)
        assertEquals("Follow me on Corus\n$link", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("@gabe"))
        assertEquals(image, intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("x", Uri.parse(link).getQueryParameter("ref"))
        assertNull(Uri.parse(profileV2ShareLink(profile, "x", false)).getQueryParameter("profile_sharing_v2"))
    }

    @Test fun `weather starts filled and falls down in both preview and export coordinates`() {
        for (effect in listOf(ProfileStoryWeather.RAIN, ProfileStoryWeather.SNOW)) {
            val scene = ProfileStoryWeatherScene(effect)
            val before = scene.particlePositions()
            assertTrue(before.count { it.second in 30f..600f } > 10)
            scene.advance(1f / 60)
            val after = scene.particlePositions()
            before.indices.filter { before[it].second in 30f..600f }.forEach { index -> assertTrue(after[index].second > before[index].second) }
            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            scene.draw(Canvas(bitmap), 1080f, 1920f)
            val empty = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            assertFalse(bitmap.sameAs(empty))
            empty.recycle()
            bitmap.recycle()
        }
    }

    private fun save(name: String, bitmap: Bitmap) {
        val directory = System.getProperty("corus.profileShareEvidence") ?: return
        File(directory).mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
