package fm.corus.android.ui.screens.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the profile PLAYLIST button enabled/disabled logic.
 * A playlist is a music playlist, so it should only be enabled on tabs that can
 * actually produce one.
 */
class ProfilePlaylistEnabledTest {

    private fun enabled(
        segment: Int,
        hasTrackPosts: Boolean = false,
        likedCount: Int = 0,
        savedCount: Int = 0,
        isLoadingLiked: Boolean = false,
        isLoadingSaved: Boolean = false,
    ) = profilePlaylistEnabled(
        selectedSegment = segment,
        hasTrackPosts = hasTrackPosts,
        likedCount = likedCount,
        savedCount = savedCount,
        isLoadingLiked = isLoadingLiked,
        isLoadingSaved = isLoadingSaved,
    )

    // --- Music tab (0) ---

    @Test
    fun `music tab enabled when user has posted tracks`() {
        assertTrue(enabled(segment = 0, hasTrackPosts = true))
    }

    @Test
    fun `music tab disabled when user has no tracks`() {
        assertFalse(enabled(segment = 0, hasTrackPosts = false))
    }

    // --- Film tab (1): a playlist is music, films can never build one ---

    @Test
    fun `film tab always disabled even with tracks present`() {
        assertFalse(enabled(segment = 1, hasTrackPosts = true))
    }

    // --- Likes tab (2) ---

    @Test
    fun `likes tab disabled when no liked posts`() {
        assertFalse(enabled(segment = 2, likedCount = 0))
    }

    @Test
    fun `likes tab enabled once liked posts loaded`() {
        assertTrue(enabled(segment = 2, likedCount = 3))
    }

    @Test
    fun `likes tab disabled while still loading even if a count is present`() {
        assertFalse(enabled(segment = 2, likedCount = 3, isLoadingLiked = true))
    }

    // --- Saves tab (3) ---

    @Test
    fun `saves tab disabled when no saved posts`() {
        assertFalse(enabled(segment = 3, savedCount = 0))
    }

    @Test
    fun `saves tab enabled once saved posts loaded`() {
        assertTrue(enabled(segment = 3, savedCount = 1))
    }

    @Test
    fun `saves tab disabled while still loading`() {
        assertFalse(enabled(segment = 3, savedCount = 5, isLoadingSaved = true))
    }

    // --- Unknown segment ---

    @Test
    fun `unknown segment disabled`() {
        assertFalse(enabled(segment = 99, hasTrackPosts = true))
    }
}
