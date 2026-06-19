package fm.corus.android.ui.components

import android.app.Application
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the Instagram Stories share intent is constructed correctly.
 *
 * History: `source_application` is our app id, passed as the Android package name. An attempt
 * to use the iOS Facebook app id (1364343535452154) here silently regressed real devices (the
 * share spun and did nothing), so the package name is what we ship — see [buildAddToStoryIntent].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InstagramShareIntentTest {

    private val uri: Uri = Uri.parse("content://fm.corus.android.provider/cache/instagram_share_p1.png")

    @Test
    fun `targets the instagram story action with a readable png`() {
        val intent = buildAddToStoryIntent(uri, "p1", "fm.corus.android")

        assertEquals("com.instagram.share.ADD_TO_STORY", intent.action)
        assertEquals("image/png", intent.type)
        assertEquals(uri, intent.data)
        assertTrue(
            "Instagram needs read access to the card URI",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    @Test
    fun `forwards the source application id`() {
        val intent = buildAddToStoryIntent(uri, "p1", "fm.corus.android")

        assertEquals("fm.corus.android", intent.getStringExtra("source_application"))
    }

    @Test
    fun `carries the post deep link as content url`() {
        val intent = buildAddToStoryIntent(uri, "abc123", "fm.corus.android")

        assertEquals("https://corus.fm/post/abc123", intent.getStringExtra("content_url"))
    }
}
