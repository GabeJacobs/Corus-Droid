package fm.corus.android.ui.components

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `youTubeVideoID` uses `android.net.Uri`, so it needs Robolectric to run.
 * Use the stock [Application] (not the app's own) so we don't boot RevenueCat /
 * Firebase init in the unit test — matches the other Robolectric tests here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InlineYouTubePlayerTest {

    @Test
    fun `parses the v param from a watch url`() {
        assertEquals("inc0vS58ZKg", youTubeVideoID("https://www.youtube.com/watch?v=inc0vS58ZKg"))
    }

    @Test
    fun `parses a youtu_be short url`() {
        assertEquals("abc123", youTubeVideoID("https://youtu.be/abc123"))
    }

    @Test
    fun `parses an embed url`() {
        assertEquals("XYZ789", youTubeVideoID("https://www.youtube-nocookie.com/embed/XYZ789"))
    }

    @Test
    fun `keeps the v param when extra params are present`() {
        assertEquals("KEY", youTubeVideoID("https://www.youtube.com/watch?v=KEY&t=30s&list=PL123"))
    }

    @Test
    fun `returns null for null or blank input`() {
        assertNull(youTubeVideoID(null))
        assertNull(youTubeVideoID(""))
        assertNull(youTubeVideoID("   "))
    }

    @Test
    fun `returns null for a non-youtube url with no v param`() {
        assertNull(youTubeVideoID("https://example.com/page"))
    }
}
