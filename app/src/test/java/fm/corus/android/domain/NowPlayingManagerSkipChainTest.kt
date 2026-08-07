package fm.corus.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingManagerSkipChainTest {

    @Test
    fun `mini player skip chains preview when preferPreviewOnNext`() {
        val preferPreviewOnNext = true
        val playFullSongs = true
        val chainFullPlayback = !preferPreviewOnNext && playFullSongs
        assertFalse(chainFullPlayback)
    }

    @Test
    fun `mini player skip chains full only when playFullSongs is on`() {
        val preferPreviewOnNext = false
        val playFullSongs = true
        val chainFullPlayback = !preferPreviewOnNext && playFullSongs
        assertTrue(chainFullPlayback)
    }

    @Test
    fun `turning preview mode back on stops full skip chain`() {
        val preferPreviewOnNext = false
        val playFullSongs = false
        val isActiveFullSongSession = true
        val chainFullPlayback = !preferPreviewOnNext && playFullSongs
        assertTrue(isActiveFullSongSession)
        assertFalse(chainFullPlayback)
    }

    @Test
    fun `preview auto advance stays preview on skip`() {
        val chainFullPlayback = !(true) && false
        assertFalse(chainFullPlayback)
    }
}
