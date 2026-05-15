package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the denormalized `author` block parsing on `CymbalUser` and the
 * Phase 1 author-resolution path in `CymbalPost.fromCloudData`.
 *
 * Backed-up invariant: the feed must always be able to render PostCard
 * chrome (avatar, displayName, badges, flair) for a freshly-fetched post
 * without an extra users_v2 read, as long as the backend rollout has
 * stamped or backfilled the `author` block.
 */
class AuthorPreviewTest {

    @Test fun `fromAuthorPreview populates every chrome-render field`() {
        val user = CymbalUser.fromAuthorPreview(
            uid = "uid_alice",
            data = mapOf(
                "username" to "alice",
                "displayName" to "Alice Aardvark",
                "avatarURL" to "https://x/a.jpg",
                "avatarThumbURL" to "https://x/a-thumb.jpg",
                "isVerified" to true,
                "isClubMember" to true,
                "isBot" to false,
                "botType" to null,
                "profileFlair" to "vinyl",
                // Fields outside the preview surface must be ignored so a
                // future user-doc field rename can't accidentally leak into
                // posts via the wrong source. Without this guard the contract
                // would silently drift.
                "bio" to "do not include me",
                "followerCount" to 42,
            ),
        )
        assertNotNull(user)
        user!!
        assertEquals("uid_alice", user.id)
        assertEquals("alice", user.username)
        assertEquals("Alice Aardvark", user.displayName)
        assertEquals("https://x/a.jpg", user.avatarURL)
        assertEquals("https://x/a-thumb.jpg", user.avatarThumbURL)
        assertTrue(user.isVerified)
        assertTrue(user.isClubMember)
        assertFalse(user.isBot)
        assertNull(user.botType)
        assertEquals("vinyl", user.profileFlair)
        // Counts not on the preview default to zero (feed doesn't read them).
        assertEquals(0, user.followerCount)
        assertEquals("", user.bio)
    }

    @Test fun `fromAuthorPreview returns null when both name fields are empty`() {
        // Avoids rendering an avatar-less ghost card when the block is
        // structurally present but unpopulated (e.g. a partial write was
        // observed mid-flight). Callers fall back to per-uid fetch.
        val empty = CymbalUser.fromAuthorPreview(
            uid = "uid",
            data = mapOf("username" to "", "displayName" to ""),
        )
        assertNull(empty)
    }

    @Test fun `fromAuthorPreview returns null for null input`() {
        assertNull(CymbalUser.fromAuthorPreview("uid", null))
    }

    @Test fun `fromAuthorPreview defaults profileFlair to checkmark when absent`() {
        val user = CymbalUser.fromAuthorPreview(
            uid = "u",
            data = mapOf("username" to "u", "displayName" to "U"),
        )
        assertEquals("checkmark", user!!.profileFlair)
    }

    @Test fun `fromCloudData prefers user block over author block when both present`() {
        // Production path: server callable hydrates `user`, which mirrors the
        // author block in steady state but also carries fields the preview
        // doesn't (followerCount, bio) and is the canonical source for taste
        // surfaces. So `user` wins when both are populated.
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "post1",
                "userId" to "u1",
                "user" to mapOf(
                    "id" to "u1",
                    "username" to "from_user_block",
                    "displayName" to "From User Block",
                    "followerCount" to 100,
                ),
                "author" to mapOf(
                    "username" to "from_author_block",
                    "displayName" to "From Author Block",
                ),
                "trackId" to "t",
                "trackName" to "T",
                "artistName" to "A",
                "albumName" to "A",
            ),
        )
        assertEquals("from_user_block", post.user.username)
        assertEquals(100, post.user.followerCount)
    }

    @Test fun `fromCloudData falls back to author block when user is missing`() {
        // Defensive: future code paths might ship only the denorm block (e.g.
        // a direct-Firestore feed that bypasses the callable). The feed
        // should still render correctly with empty counts.
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "post1",
                "userId" to "u1",
                "author" to mapOf(
                    "username" to "alice",
                    "displayName" to "Alice",
                    "isVerified" to true,
                    "profileFlair" to "vinyl",
                ),
                "trackId" to "t",
                "trackName" to "T",
                "artistName" to "A",
                "albumName" to "A",
            ),
        )
        assertEquals("u1", post.user.id)
        assertEquals("alice", post.user.username)
        assertEquals("Alice", post.user.displayName)
        assertTrue(post.user.isVerified)
        assertEquals("vinyl", post.user.profileFlair)
    }

    @Test fun `fromCloudData falls through to empty user when neither block is populated`() {
        // Legacy pre-rollout posts that haven't hit the one-time backfill —
        // the backend's zombie filter normally drops these before they
        // reach the client, but defensively we want a stub rather than a
        // crash so a backend regression can't take down the feed.
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "post1",
                "userId" to "u1",
                "trackId" to "t",
                "trackName" to "T",
                "artistName" to "A",
                "albumName" to "A",
            ),
        )
        assertEquals("u1", post.user.id)
        assertEquals("", post.user.username)
    }
}
