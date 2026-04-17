package fm.corus.android.data.remote

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyFirstPosterTest {

    private fun post(userId: String, storedIsFirstPoster: Boolean = false): CymbalPost =
        CymbalPost(
            id = "post-$userId",
            user = CymbalUser(id = userId, username = userId, displayName = userId),
            track = CymbalTrack(id = "t", name = "n", artistName = "a", albumName = "al"),
            isFirstPoster = storedIsFirstPoster,
        )

    @Test
    fun `only the post whose userId matches firstPosterId gets isFirstPoster=true`() {
        val posts = listOf(post("victor"), post("newbie"), post("bot"))

        val result = applyFirstPoster(posts, firstPosterId = "victor")

        assertEquals(3, result.size)
        assertTrue(result.single { it.user.id == "victor" }.isFirstPoster)
        assertFalse(result.single { it.user.id == "newbie" }.isFirstPoster)
        assertFalse(result.single { it.user.id == "bot" }.isFirstPoster)
    }

    @Test
    fun `stale stored isFirstPoster=true is cleared when user is not the resolved first poster`() {
        // Regression: Amélie bug. A post was persisted with isFirstPoster=true from a racy
        // client-side count check. The server resolved the real first poster as someone else.
        // Without this clearing behavior, both posts rendered a 1ST trophy.
        val posts = listOf(
            post("victor", storedIsFirstPoster = false),
            post("newbie", storedIsFirstPoster = true),
        )

        val result = applyFirstPoster(posts, firstPosterId = "victor")

        assertTrue(result.single { it.user.id == "victor" }.isFirstPoster)
        assertFalse(result.single { it.user.id == "newbie" }.isFirstPoster)
    }

    @Test
    fun `null firstPosterId clears every isFirstPoster flag`() {
        val posts = listOf(
            post("a", storedIsFirstPoster = true),
            post("b", storedIsFirstPoster = true),
        )

        val result = applyFirstPoster(posts, firstPosterId = null)

        assertTrue(result.none { it.isFirstPoster })
    }

    @Test
    fun `at most one post can have isFirstPoster=true`() {
        val posts = listOf(
            post("a", storedIsFirstPoster = true),
            post("b", storedIsFirstPoster = true),
            post("c", storedIsFirstPoster = true),
            post("d", storedIsFirstPoster = true),
        )

        val result = applyFirstPoster(posts, firstPosterId = "b")

        assertEquals(1, result.count { it.isFirstPoster })
        assertEquals("b", result.single { it.isFirstPoster }.user.id)
    }
}
