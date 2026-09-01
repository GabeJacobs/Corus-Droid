package fm.corus.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.mock

class PlaylistResponseParserTest {

    private val dataSource = CloudFunctionsDataSource(mock<FirebaseFunctions>(), mock(), mock())

    @Test
    fun `success response returns PlaylistResult`() {
        val result = dataSource.parsePlaylistResponse(
            mapOf(
                "playlistURI" to "spotify:playlist:abc",
                "playlistWebURL" to "https://open.spotify.com/playlist/abc",
                "cached" to true,
            )
        )
        assertEquals("spotify:playlist:abc", result.playlistURI)
        assertEquals("https://open.spotify.com/playlist/abc", result.playlistWebURL)
        assertTrue(result.cached)
    }

    @Test
    fun `success response without cached defaults to false`() {
        val result = dataSource.parsePlaylistResponse(
            mapOf(
                "playlistURI" to "spotify:playlist:abc",
                "playlistWebURL" to "https://open.spotify.com/playlist/abc",
            )
        )
        assertFalse(result.cached)
    }

    @Test
    fun `PAYWALL code throws PaywallRequiredException`() {
        try {
            dataSource.parsePlaylistResponse(
                mapOf(
                    "error" to true,
                    "code" to "PAYWALL",
                    "message" to "Free generation already used",
                )
            )
            fail("Expected PaywallRequiredException")
        } catch (_: CloudFunctionsDataSource.PaywallRequiredException) {
            // expected
        }
    }

    @Test
    fun `generic error without PAYWALL code throws plain Exception`() {
        try {
            dataSource.parsePlaylistResponse(
                mapOf(
                    "error" to true,
                    "message" to "Spotify auth failed",
                )
            )
            fail("Expected Exception")
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            fail("Should not be PaywallRequiredException for non-PAYWALL errors")
        } catch (e: Exception) {
            assertEquals("Spotify auth failed", e.message)
        }
    }

    @Test
    fun `error with non-PAYWALL code is treated as generic error`() {
        try {
            dataSource.parsePlaylistResponse(
                mapOf(
                    "error" to true,
                    "code" to "SOME_OTHER_CODE",
                    "message" to "Boom",
                )
            )
            fail("Expected Exception")
        } catch (e: CloudFunctionsDataSource.PaywallRequiredException) {
            fail("Should only throw PaywallRequiredException for code=PAYWALL")
        } catch (e: Exception) {
            assertEquals("Boom", e.message)
        }
    }
}
