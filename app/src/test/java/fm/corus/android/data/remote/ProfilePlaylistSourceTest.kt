package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The backend (`generateProfilePlaylist` Cloud Function) does an exact string
 * compare against `"likes"` and `"saves"` and falls back to posts on any other
 * value. A silent rename here (e.g. `"Likes"`) would not crash — it would just
 * regress the feature to always-posts. Pin the wire values so a refactor that
 * changes them forces a test failure instead.
 */
class ProfilePlaylistSourceTest {

    @Test
    fun `wire values match backend contract`() {
        assertEquals("posts", CloudFunctionsDataSource.ProfilePlaylistSource.Posts.wire)
        assertEquals("likes", CloudFunctionsDataSource.ProfilePlaylistSource.Likes.wire)
        assertEquals("saves", CloudFunctionsDataSource.ProfilePlaylistSource.Saves.wire)
    }
}
