package fm.corus.android.domain

import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

/**
 * Regression coverage for link-out routing.
 *
 * The badge/tap fall back to the source service ONLY when the backend has
 * confirmed the track isn't on Apple Music — signalled by an explicit empty
 * appleMusicId (""). A null id means "unknown" (legacy post, or the feed payload
 * hasn't carried it yet) and must NOT short-circuit to Spotify — otherwise an
 * Apple Music viewer's whole feed links out to Spotify. (Repro: New Order's
 * "Temptation", which is on Apple Music but whose feed payload didn't carry the
 * id, was wrongly badged Spotify.)
 *
 * For SPOTIFY and the confirmed-empty APPLE_MUSIC case the cloud datasource is
 * never touched (the decision is local), so a bare mock asserted unused suffices.
 */
class MusicServiceLinkOutTest {

    private fun spotifyTrack(
        appleMusicId: String? = null,
        spotifyWebURL: String = "https://open.spotify.com/track/61dv1VL5ZPj1L9p3Ko9QEC",
        spotifyURI: String = "spotify:track:61dv1VL5ZPj1L9p3Ko9QEC",
        id: String = "61dv1VL5ZPj1L9p3Ko9QEC",
    ) = CymbalTrack(
        id = id,
        name = "Vou Recomeçar",
        artistName = "Gal Costa",
        albumName = "Gal Costa",
        spotifyURI = spotifyURI,
        spotifyWebURL = spotifyWebURL,
        source = TrackSource.SPOTIFY,
        appleMusicId = appleMusicId,
    )

    @Test
    fun `spotify preference returns null so caller opens the post's own uri`() = runTest {
        val cloud = mock<CloudFunctionsDataSource>()
        val url = MusicServiceLinkOut.resolveLinkOutUrl(spotifyTrack(), MusicService.SPOTIFY, cloud)
        assertNull(url)
        verifyNoInteractions(cloud)
    }

    @Test
    fun `apple music viewer opens the apple music page when an equivalent exists`() = runTest {
        val cloud = mock<CloudFunctionsDataSource>()
        val track = spotifyTrack(appleMusicId = "960299338")
        val url = MusicServiceLinkOut.resolveLinkOutUrl(track, MusicService.APPLE_MUSIC, cloud)
        assertEquals("https://music.apple.com/us/song/960299338", url)
        verifyNoInteractions(cloud)
    }

    @Test
    fun `apple music viewer on a CONFIRMED spotify-only track falls back to the spotify web url`() = runTest {
        val cloud = mock<CloudFunctionsDataSource>()
        // appleMusicId == "" → backend confirmed no Apple Music match.
        val track = spotifyTrack(appleMusicId = "", id = "confirmedSpotifyOnly1")
        val url = MusicServiceLinkOut.resolveLinkOutUrl(track, MusicService.APPLE_MUSIC, cloud)
        assertEquals("https://open.spotify.com/track/61dv1VL5ZPj1L9p3Ko9QEC", url)
        // No dead Apple Music lookup — the decision is made locally.
        verifyNoInteractions(cloud)
    }

    @Test
    fun `confirmed spotify-only fallback uses the spotify uri when no web url is present`() = runTest {
        val cloud = mock<CloudFunctionsDataSource>()
        val track = spotifyTrack(appleMusicId = "", spotifyWebURL = "", id = "confirmedSpotifyOnly2")
        val url = MusicServiceLinkOut.resolveLinkOutUrl(track, MusicService.APPLE_MUSIC, cloud)
        assertEquals("spotify:track:61dv1VL5ZPj1L9p3Ko9QEC", url)
        verifyNoInteractions(cloud)
    }

    @Test
    fun `confirmed spotify-only fallback returns null when the track carries no spotify link`() = runTest {
        val cloud = mock<CloudFunctionsDataSource>()
        val track = spotifyTrack(appleMusicId = "", spotifyWebURL = "", spotifyURI = "", id = "confirmedSpotifyOnly3")
        val url = MusicServiceLinkOut.resolveLinkOutUrl(track, MusicService.APPLE_MUSIC, cloud)
        assertNull(url)
        verifyNoInteractions(cloud)
    }

    @Test
    fun `unknown (null) appleMusicId does NOT short-circuit to spotify - it falls through to the live lookup`() = runTest {
        // The whole-feed-shows-Spotify regression: an Apple Music viewer on a
        // track whose id is unknown (null) must NOT be sent to Spotify. It falls
        // through to the Apple Music lookup. With the cloud mock unstubbed that
        // lookup yields null — the key assertion is that we did NOT return the
        // Spotify web URL (which a wrong short-circuit would have produced).
        val cloud = mock<CloudFunctionsDataSource>()
        val track = spotifyTrack(appleMusicId = null, id = "unknownAppleId1")
        val url = MusicServiceLinkOut.resolveLinkOutUrl(track, MusicService.APPLE_MUSIC, cloud)
        assertNull("null id must fall through to the lookup, not short-circuit to Spotify", url)
    }
}
