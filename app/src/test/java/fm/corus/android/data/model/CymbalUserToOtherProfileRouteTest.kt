package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Search and feed both seed OtherProfileRoute from a CymbalUser they already
 * have, so the header can paint identity immediately. Counts stay null so
 * the profile shimmers stats until getProfileData lands (iOS parity).
 */
class CymbalUserToOtherProfileRouteTest {

    @Test
    fun `seeds identity and leaves counts unset`() {
        val user = CymbalUser(
            id = "u1",
            username = "cliftonhall1983",
            displayName = "Jacqueline Hall-Benchaib",
            avatarURL = "https://example.com/avatar.jpg",
            avatarThumbURL = "https://example.com/thumb.jpg",
            bio = "hello",
            isVerified = true,
            isClubMember = true,
            followerCount = 12,
            followingCount = 8,
            cymbalCount = 40,
        )

        val route = user.toOtherProfileRoute(isFollowing = true)

        assertEquals("u1", route.userId)
        assertEquals("https://example.com/avatar.jpg", route.avatarURL)
        assertEquals("https://example.com/thumb.jpg", route.avatarThumbURL)
        assertEquals("Jacqueline Hall-Benchaib", route.initialDisplayName)
        assertEquals("cliftonhall1983", route.initialUsername)
        assertEquals("hello", route.initialBio)
        assertEquals(true, route.initialIsVerified)
        assertEquals(true, route.initialIsClubMember)
        assertEquals(true, route.initialIsFollowing)
        assertNull(route.initialCymbalCount)
        assertNull(route.initialFollowerCount)
        assertNull(route.initialFollowingCount)
    }

    @Test
    fun `blank identity fields are omitted so the profile can still full-skeleton`() {
        val user = CymbalUser(id = "u2", username = "", displayName = "", bio = "")
        val route = user.toOtherProfileRoute()
        assertNull(route.initialDisplayName)
        assertNull(route.initialUsername)
        assertNull(route.initialBio)
        assertNull(route.initialIsFollowing)
    }
}
