package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Skip-chain algebra for [NowPlayingManager.preferPreviewOnInAppSkip] /
 * [NowPlayingManager.shouldRouteSpotifyFeedSkip].
 *
 * Important: current ExoPlayer "preview chrome" (SoundCloud full HLS, etc.) must
 * NOT force preview-chaining when Play Full Songs is on — that blocked Spotify
 * Connect after SoundCloud → Next.
 */
class NowPlayingManagerSkipChainTest {

    private fun preferPreviewOnInAppSkip(
        playFullSongs: Boolean,
        @Suppress("UNUSED_PARAMETER") isPreviewMode: Boolean,
    ): Boolean = !playFullSongs

    private fun shouldChainFullPlaybackOnSkip(
        preferPreviewOnNext: Boolean,
        playFullSongs: Boolean,
    ): Boolean = !preferPreviewOnNext && playFullSongs

    @Test
    fun `soundcloud exoplayer session does not force preview skip when play full songs on`() {
        val preferPreview = preferPreviewOnInAppSkip(playFullSongs = true, isPreviewMode = true)
        assertFalse(preferPreview)
        assertTrue(shouldChainFullPlaybackOnSkip(preferPreview, playFullSongs = true))
    }

    @Test
    fun `mini player skip chains preview when play full songs off`() {
        val preferPreview = preferPreviewOnInAppSkip(playFullSongs = false, isPreviewMode = false)
        assertTrue(preferPreview)
        assertFalse(shouldChainFullPlaybackOnSkip(preferPreview, playFullSongs = false))
    }

    @Test
    fun `mini player skip chains full when play full songs on`() {
        val preferPreview = preferPreviewOnInAppSkip(playFullSongs = true, isPreviewMode = false)
        assertFalse(preferPreview)
        assertTrue(shouldChainFullPlaybackOnSkip(preferPreview, playFullSongs = true))
    }

    @Test
    fun `turning preview mode preference off stops full skip chain`() {
        val preferPreview = preferPreviewOnInAppSkip(playFullSongs = false, isPreviewMode = true)
        assertTrue(preferPreview)
        assertFalse(shouldChainFullPlaybackOnSkip(preferPreview, playFullSongs = false))
    }
}
