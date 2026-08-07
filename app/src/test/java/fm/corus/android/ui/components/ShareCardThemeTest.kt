package fm.corus.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardThemeTest {

    @Test
    fun `preview url omits params for dark default card`() {
        assertEquals(
            "https://corus.fm/u/gabe/preview",
            shareCardPreviewUrl("https://corus.fm/u/gabe"),
        )
    }

    @Test
    fun `preview url carries light theme and version`() {
        assertEquals(
            "https://corus.fm/u/gabe/preview?v=12-post1&theme=light",
            shareCardPreviewUrl(
                shareableLink = "https://corus.fm/u/gabe",
                version = "12-post1",
                theme = ShareCardTheme.LIGHT,
            ),
        )
    }

    @Test
    fun `profile subject knows when local preview is available`() {
        val thin = ShareProfileSubject("1", "gabe", "Gabe", null)
        assertFalse(thin.hasLocalPreview)

        val rich = thin.copy(bio = "hello", artworkUrls = listOf("https://example.com/art.jpg"))
        assertTrue(rich.hasLocalPreview)
    }

    @Test
    fun `stories grid layout matches iOS table`() {
        assertEquals(ProfileStoriesGridLayout(3, 3, 9), profileStoriesGridLayout(12))
        assertEquals(ProfileStoriesGridLayout(2, 3, 5), profileStoriesGridLayout(5))
        assertEquals(ProfileStoriesGridLayout(0, 0, 0), profileStoriesGridLayout(0))
    }
}
