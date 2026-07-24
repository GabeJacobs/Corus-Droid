package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for [resolveRecordingAppleId] — the fold that keeps an
 * Apple Music viewer on "Open in Apple Music" for a recording that IS on Apple,
 * even when the pinned first post carries a stale `appleMusicId == ""`.
 *
 * Real case: Steve Lacy "nice shoes / in your world" (ISRC USRC12601066). The
 * 1ST poster posted it on release day (2026-07-16) before Apple indexed the
 * track, so the backend stamped `appleMusicId = ""` (confirmed-miss) on that
 * post. Every later pressing (2026-07-17+) resolved the real id "6773775306".
 * The song page pins the 1ST poster at posts[0] (moveFirstPosterToTop), so
 * reading only posts[0] made Android show the Apple viewer "Play in Spotify"
 * while iOS — which reads the freshly-resolved tapped seed — showed
 * "Open in Apple Music". This fold recovers the resolved id from a later post.
 */
class SongDetailAppleIdResolutionTest {

    private fun post(
        id: String,
        appleMusicId: String?,
        storefront: String? = null,
    ): CymbalPost = CymbalPost(
        id = id,
        user = CymbalUser(id = "u_$id", username = "user_$id", displayName = "User $id"),
        track = CymbalTrack(
            id = "t_$id",
            name = "nice shoes / in your world",
            artistName = "Steve Lacy",
            albumName = "Oh yeah?",
            appleMusicId = appleMusicId,
            appleMusicStorefront = storefront,
        ),
    )

    @Test
    fun `resolved id on the first post is used`() {
        val posts = listOf(post("1", "6773775306", "us"))
        assertEquals("6773775306" to "us", resolveRecordingAppleId(posts, "us"))
    }

    @Test
    fun `stale empty-string miss on the pinned first post is overridden by a later resolved pressing`() {
        // THE BUG: posts[0] posted on release day -> appleMusicId "" (confirmed
        // miss); a later pressing carries the real id. The viewer can open Apple,
        // so the fold must surface the resolved id, not the stale "".
        val posts = listOf(
            post("1", "", ""),            // 1ST poster, release-day miss
            post("2", "6773775306", "us"), // later pressing, resolved
            post("3", "6773775306", "us"),
        )
        assertEquals("6773775306" to "us", resolveRecordingAppleId(posts, "us"))
    }

    @Test
    fun `prefers a viewer-reachable id over a foreign-storefront one`() {
        val posts = listOf(
            post("1", "111", "br"), // Brazil-only, unreachable for a US viewer
            post("2", "222", "us"), // reachable
        )
        assertEquals("222" to "us", resolveRecordingAppleId(posts, "us"))
    }

    @Test
    fun `falls back to a foreign-only id when nothing reachable exists`() {
        // No reachable pressing, but one resolved (foreign) id. Better to surface
        // it and let the downstream storefront gate decide than to drop to a miss.
        val posts = listOf(post("1", "", ""), post("2", "999", "br"))
        assertEquals("999" to "br", resolveRecordingAppleId(posts, "us"))
    }

    @Test
    fun `returns null when every pressing is a confirmed miss (genuinely Spotify-only)`() {
        // No pressing resolved an id -> keep the base track's "" so the Apple
        // viewer correctly stays on Spotify. No regression for Spotify-only songs.
        val posts = listOf(post("1", "", ""), post("2", "", ""))
        assertNull(resolveRecordingAppleId(posts, "us"))
    }

    @Test
    fun `returns null when every pressing is unknown`() {
        val posts = listOf(post("1", null), post("2", null))
        assertNull(resolveRecordingAppleId(posts, "us"))
    }

    @Test
    fun `returns null for no posts`() {
        assertNull(resolveRecordingAppleId(emptyList(), "us"))
    }
}
