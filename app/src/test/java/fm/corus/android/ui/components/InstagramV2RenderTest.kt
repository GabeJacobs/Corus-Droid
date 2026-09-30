package fm.corus.android.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import fm.corus.android.data.model.FlairStyle
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
    @Test fun `frosted background uses a smooth story-ratio blur source`() {
        val art = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                setPixel(x, y, if (x < width / 2) Color.RED else Color.BLUE)
            }
        }

        val frost = bakeInstagramV2FrostedBackground(art)

        assertEquals(270, frost.width)
        assertEquals(480, frost.height)
        assertNotEquals(Color.RED, frost.getPixel(frost.width / 2 - 12, frost.height / 2))
        assertNotEquals(Color.BLUE, frost.getPixel(frost.width / 2 + 12, frost.height / 2))
        frost.recycle()
        art.recycle()
    }

    @Test fun `post identity flair and active tags are included in the author row`() {
        val context = RuntimeEnvironment.getApplication()
        val art = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val plain = InstagramV2Subject(
            "Song", "Artist", null, "https://corus.fm/song/test", "https://corus.fm/post/test", username = "tester",
        )
        val decorated = plain.copy(
            isVerified = true,
            flairStyle = FlairStyle.HEART,
            isFirstPoster = true,
            isNewRelease = true,
        )

        val plainStory = renderInstagramV2(context, plain, art, Color.RED, "cover", "solid")
        val decoratedStory = renderInstagramV2(context, decorated, art, Color.RED, "cover", "solid")

        assertFalse(plainStory.sameAs(decoratedStory))
        plainStory.recycle()
        decoratedStory.recycle()
        art.recycle()
    }

    @Test fun `username tags and flair share the avatar center in cover and vinyl`() {
        val context = RuntimeEnvironment.getApplication()
        val art = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val avatar = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val subject = InstagramV2Subject("Song", "Artist", null, "", "", username = "tester",
            isVerified = true, flairStyle = FlairStyle.HEART, isFirstPoster = true, isNewRelease = true)
        for (layout in listOf("cover", "vinyl")) {
            val story = renderInstagramV2(context, subject, art, Color.BLACK, layout, "solid", avatar,
                includesBackground = false)
            val textX = if (layout == "cover") 130 else 80
            val avatarTop = (0 until 1000).first { story.getPixel(textX + 40, it) == Color.GREEN }
            fun center(left: Int, right: Int, matches: (Int) -> Boolean): Double {
                val rows = (avatarTop until avatarTop + 80).filter { y ->
                    (left until right).any { x -> matches(story.getPixel(x, y)) }
                }
                assertTrue("Missing author element in $layout", rows.isNotEmpty())
                return (rows.first() + rows.last()) / 2.0
            }
            val usernameCenter = center(textX + 102, textX + 225) { it == Color.WHITE }
            val tagCenter = center(textX + 260, textX + 600) {
                Color.alpha(it) > 200 && Color.red(it) > 220 && Color.green(it) > 140 && Color.blue(it) < 40
            }
            assertEquals("Username must be centered with tags in $layout", tagCenter, usernameCenter, 5.0)
            val flairCenter = center(textX + 230, textX + 290) {
                Color.alpha(it) > 200 && Color.blue(it) > 180 && Color.red(it) < 160
            }
            assertEquals("Flair must be centered with tags in $layout", tagCenter + 1, flairCenter, 5.0)
            story.recycle()
        }
        avatar.recycle()
        art.recycle()
    }

    @Test fun `exported headphones use the post icon silhouette instead of a text glyph`() {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        drawInstagramV2Flair(android.graphics.Canvas(bitmap), RuntimeEnvironment.getApplication(),
            FlairStyle.HEADPHONES, 40f, 40f)
        // Headphones have a headband above an open center and no bar across the bottom.
        assertTrue(Color.alpha(bitmap.getPixel(40, 28)) > 200)
        assertEquals(0, Color.alpha(bitmap.getPixel(40, 51)))
        assertTrue(Color.alpha(bitmap.getPixel(29, 47)) > 200)
        assertTrue(Color.alpha(bitmap.getPixel(51, 47)) > 200)
        bitmap.recycle()
    }

    @Test fun `Corus logo flair appears in both cover and vinyl previews`() {
        val context = RuntimeEnvironment.getApplication()
        val art = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val plain = InstagramV2Subject("Song", "Artist", null, "", "", username = "aiden", isClubMember = true)
        for (layout in listOf("cover", "vinyl")) {
            val withoutFlair = renderInstagramV2(context, plain, art, Color.BLACK, layout, "solid")
            val withFlair = renderInstagramV2(context, plain.copy(flairStyle = FlairStyle.CORUS_LOGO),
                art, Color.BLACK, layout, "solid")
            assertFalse("Corus logo must be visible in $layout", withoutFlair.sameAs(withFlair))
            withoutFlair.recycle()
            withFlair.recycle()
        }
        art.recycle()
    }

    @Test fun `all layouts export exact story size with chosen background`() {
        val context = RuntimeEnvironment.getApplication()
        val art = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val subject = InstagramV2Subject("A long song title that needs to wrap onto another line", "An artist", null, "https://corus.fm/song/test", "https://corus.fm/post/test", "tester", "Caption with emoji 🎵 and português")
        for (layout in listOf("cover", "vinyl")) for (background in listOf("frosted", "solid", "gradient")) {
            val result = renderInstagramV2(context, subject, art, 0xffb81f29.toInt(), layout, background)
            assertEquals(1080, result.width)
            assertEquals(1920, result.height)
            if (background == "gradient") assertNotEquals(result.getPixel(1, 1), result.getPixel(1, 1918))
            if (background == "frosted") assertNotEquals(0xffb81f29.toInt(), result.getPixel(1, 1))
            if (background == "solid") {
                assertEquals(0xffb81f29.toInt(), result.getPixel(1, 1))
                assertEquals(0xffb81f29.toInt(), result.getPixel(1, 960))
                assertEquals(0xffb81f29.toInt(), result.getPixel(1, 1918))
            }
            result.recycle()
        }
        art.recycle()
    }

    @Test fun `cover uses a frame and vinyl removes it for every background`() {
        val context = RuntimeEnvironment.getApplication()
        val art = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val subject = InstagramV2Subject("Song", "Artist", null, "https://corus.fm/song/test", "https://corus.fm/post/test", "tester", "Caption")

        for (background in listOf("frosted", "solid", "gradient")) {
            val cover = renderInstagramV2(context, subject, art, 0xffb81f29.toInt(), "cover", background)
            val vinyl = renderInstagramV2(context, subject, art, 0xffb81f29.toInt(), "vinyl", background)
            assertFalse(cover.sameAs(vinyl))
            val bounds = instagramV2FrameBoundsForSubject(context, subject)
            assertTrue(InstagramV2Palette.luminance(cover.getPixel(bounds.left + 10, bounds.top + 10)) < .2)
            cover.recycle()
            vinyl.recycle()
        }
        art.recycle()
    }

    @Test fun `cover exports selected fixed background and movable sticker assets`() {
        val context = RuntimeEnvironment.getApplication()
        val art = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        val subject = InstagramV2Subject("Song", "Artist", null, "https://corus.fm/song/test", "https://corus.fm/post/test")
        val story = renderInstagramV2(context, subject, art, Color.YELLOW, "cover", "solid")
        val background = renderInstagramV2FrameBackground(art, Color.YELLOW, "solid")
        val bounds = instagramV2FrameBoundsForSubject(context, subject)
        val sticker = renderInstagramV2FrameSticker(story, bounds)

        assertEquals(1080, background.width)
        assertEquals(1920, background.height)
        assertEquals(Color.YELLOW, background.getPixel(1, 1))
        assertEquals(960, sticker.width)
        assertEquals(bounds.height(), sticker.height)
        assertEquals(0, Color.alpha(sticker.getPixel(0, 0)))
        assertEquals(255, Color.alpha(sticker.getPixel(480, 750)))

        story.recycle()
        background.recycle()
        sticker.recycle()
        art.recycle()
    }

    @Test fun `vinyl exports a transparent movable foreground over the selected background`() {
        val context = RuntimeEnvironment.getApplication()
        val art = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val subject = InstagramV2Subject(
            "Song", "Artist", null, "https://corus.fm/song/test", "https://corus.fm/post/test",
            username = "tester", caption = "Caption",
        )

        val foreground = renderInstagramV2(
            context, subject, art, Color.RED, "vinyl", "solid",
            includesBackground = false,
        )
        assertEquals(0, Color.alpha(foreground.getPixel(0, 0)))

        val sticker = cropInstagramV2TransparentSticker(foreground)
        assertNotNull(sticker)
        assertTrue(sticker!!.width < foreground.width)
        assertTrue(sticker.height < foreground.height)
        assertEquals(0, Color.alpha(sticker.getPixel(0, 0)))

        sticker.recycle()
        foreground.recycle()
        art.recycle()
    }

    @Test fun `captionless cover collapses unused frame space`() {
        val context = RuntimeEnvironment.getApplication()
        val art = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        val withoutCaption = InstagramV2Subject("Song", "Artist", null, "https://corus.fm/song/test", "https://corus.fm/post/test", "tester")
        val withCaption = withoutCaption.copy(caption = "Caption")
        val compactBounds = instagramV2FrameBoundsForSubject(context, withoutCaption)
        val captionBounds = instagramV2FrameBoundsForSubject(context, withCaption)
        assertTrue(compactBounds.height() < captionBounds.height())
        assertTrue(captionBounds.height() < 1500)
        assertTrue(compactBounds.top > captionBounds.top)

        val story = renderInstagramV2(context, withoutCaption, art, Color.YELLOW, "cover", "solid")
        assertEquals(Color.YELLOW, story.getPixel(compactBounds.left + 10, 220))
        assertTrue(
            InstagramV2Palette.luminance(story.getPixel(compactBounds.left + 10, compactBounds.top + 10)) <
                InstagramV2Palette.luminance(Color.YELLOW),
        )
        story.recycle()
        art.recycle()
    }

    @Test fun `films use the poster layout and ignore vinyl`() {
        val context = RuntimeEnvironment.getApplication()
        val poster = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val subject = InstagramV2Subject(
            "Film", "Director", null, "https://corus.fm/film/test", "https://corus.fm/film/test",
            isFilm = true,
        )

        val cover = renderInstagramV2(context, subject, poster, 0xff244a7c.toInt(), "cover", "solid")
        val vinyl = renderInstagramV2(context, subject, poster, 0xff244a7c.toInt(), "vinyl", "solid")

        assertTrue(cover.sameAs(vinyl))
        cover.recycle()
        vinyl.recycle()
        poster.recycle()
    }
}
