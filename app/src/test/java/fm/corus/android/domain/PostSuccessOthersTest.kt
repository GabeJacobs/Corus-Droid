package fm.corus.android.domain

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostSuccessOthersTest {

    private fun user(id: String, username: String = id) =
        CymbalUser(id = id, username = username, displayName = username)

    private fun post(userId: String, caption: String? = null) = CymbalPost(
        id = "p-$userId",
        user = user(userId),
        track = CymbalTrack(id = "t1", name = "Song", artistName = "Artist", albumName = "Album"),
        caption = caption,
    )

    @Test
    fun `flag-off style gates — trophy, first post, and lifetime cap`() {
        assertFalse(PostSuccessOthers.shouldAttempt(isFirstPoster = true, totalPostCount = 2))
        assertFalse(PostSuccessOthers.shouldAttempt(isFirstPoster = false, totalPostCount = 1))
        assertFalse(PostSuccessOthers.shouldAttempt(isFirstPoster = false, totalPostCount = 99))
        assertFalse(
            PostSuccessOthers.shouldAttempt(
                isFirstPoster = false,
                totalPostCount = 5,
                enforceLifetimeCap = true,
            ),
        )
        assertTrue(
            PostSuccessOthers.shouldAttempt(
                isFirstPoster = false,
                totalPostCount = 2,
                enforceLifetimeCap = true,
            ),
        )
        assertTrue(
            PostSuccessOthers.shouldAttempt(
                isFirstPoster = false,
                totalPostCount = 4,
                enforceLifetimeCap = true,
            ),
        )
    }

    @Test
    fun `pickEligible drops self, already-following, and duplicate users`() {
        val posts = listOf(
            post("me", "mine"),
            post("a", "hello"),
            post("b", null),
            post("a", "second from a"),
            post("c", "caption"),
            post("followed", "nope"),
        )
        val picked = PostSuccessOthers.pickEligible(
            posts = posts,
            currentUserId = "me",
            followingIds = setOf("followed"),
        )
        assertEquals(listOf("a", "c", "b"), picked.map { it.user.id })
        assertEquals("hello", picked[0].caption)
        assertEquals("p-a", picked[0].postId)
    }

    @Test
    fun `otherCount prefers unique posters minus self`() {
        assertEquals(4, PostSuccessOthers.otherCount(uniquePosterCount = 5, visibleCount = 3))
        assertEquals(2, PostSuccessOthers.otherCount(uniquePosterCount = null, visibleCount = 2))
    }
}
