package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the "same song posted by two people" overlay bug.
 *
 * Trending can show the same track posted by different users. Each post is a
 * distinct card sharing `track.id` but with its own `post.id`. The play/pause
 * (and loading) overlay used to match on track id alone, so tapping one post lit
 * the pause icon on every duplicate of that song.
 * [PostPlaybackHighlight.shouldHighlight] now gates on the source post, falling
 * back to a track-id match when the source post is unknown.
 */
class PostPlaybackHighlightTest {

    /** The core bug: two posts of the same song; only the tapped one lights up. */
    @Test
    fun onlyTheTappedDuplicateHighlights() {
        val tapped = PostPlaybackHighlight.shouldHighlight(
            activeTrackId = "reckoning",
            activeSourcePostId = "post-a",
            playbackActive = true,
            postTrackId = "reckoning",
            postId = "post-a",
        )
        val duplicate = PostPlaybackHighlight.shouldHighlight(
            activeTrackId = "reckoning",
            activeSourcePostId = "post-a",
            playbackActive = true,
            postTrackId = "reckoning",
            postId = "post-b",
        )
        assertTrue(tapped)
        assertFalse(duplicate) // pre-fix this was true — the bug
    }

    /** Single-track playback with no originating post — song-id match still drives it. */
    @Test
    fun fallsBackToTrackIdWhenNoSourcePost() {
        assertTrue(
            PostPlaybackHighlight.shouldHighlight(
                activeTrackId = "reckoning",
                activeSourcePostId = null,
                playbackActive = true,
                postTrackId = "reckoning",
                postId = "post-a",
            )
        )
    }

    /** No overlay when the relevant state isn't live (paused / not loading). */
    @Test
    fun nothingWhenPlaybackInactive() {
        assertFalse(
            PostPlaybackHighlight.shouldHighlight(
                activeTrackId = "reckoning",
                activeSourcePostId = "post-a",
                playbackActive = false,
                postTrackId = "reckoning",
                postId = "post-a",
            )
        )
    }

    /** A different song never lights this card, even for the same source post. */
    @Test
    fun nothingForADifferentSong() {
        assertFalse(
            PostPlaybackHighlight.shouldHighlight(
                activeTrackId = "other-song",
                activeSourcePostId = "post-a",
                playbackActive = true,
                postTrackId = "reckoning",
                postId = "post-a",
            )
        )
    }

    @Test
    fun playingOverlayHiddenWhileResolvingFullSong() {
        assertFalse(
            PostPlaybackHighlight.shouldShowPlayingOverlay(
                activeTrackId = "reckoning",
                activeSourcePostId = "post-a",
                isPlaying = true,
                isResolvingFullSong = true,
                postTrackId = "reckoning",
                postId = "post-a",
            )
        )
    }
}
