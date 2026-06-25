package fm.corus.android.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: `generateFeedPlaylist` / `generateFeedPlaylistTracks` only attach
 * the live ranked-session token when the feed is one of the ranked modes. The
 * gate originally listed "trending" alone, so Taste Matches playlist exports
 * shipped no session token, the server had no list to build from, and the user
 * got a generic "Something went wrong." Pin the ranked modes so dropping
 * "tasteMatches" here fails the build instead of the export.
 */
class FeedPlaylistSessionParamTest {

    @Test
    fun `ranked modes carry the session token`() {
        assertTrue(feedModeUsesRankedSession("trending"))
        assertTrue(feedModeUsesRankedSession("tasteMatches"))
    }

    @Test
    fun `durable-source modes do not carry the session token`() {
        assertFalse(feedModeUsesRankedSession("following"))
        assertFalse(feedModeUsesRankedSession("favorites"))
        assertFalse(feedModeUsesRankedSession(""))
    }
}
