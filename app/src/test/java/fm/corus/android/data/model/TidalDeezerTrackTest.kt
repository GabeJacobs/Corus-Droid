package fm.corus.android.data.model

import fm.corus.android.ui.components.onGoToAlbumTap
import fm.corus.android.ui.components.onGoToArtistTap
import fm.corus.android.ui.components.showGoToAlbumRow
import fm.corus.android.ui.components.showGoToArtistRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TIDAL/Deezer-EXCLUSIVE posts (backend-created; songs with no Spotify/Apple
 * match) get the Audiomack treatment: they post AS their source and are
 * LINK-OUT ONLY — there is no in-app playback resolver, so badge/CTA taps open
 * `tidalURL` / `deezerURL` externally. These tests pin the load-bearing bits of
 * that contract: the `trackSource` discriminator parses to [TrackSource.TIDAL] /
 * [TrackSource.DEEZER], the link-out target is the source page URL, the Spotify
 * fields stay blank (no synthesized broken links), and — unlike Audiomack,
 * which carries its own artist/album page URLs — the "Go to Artist"/"Go to
 * Album" rows simply stay hidden (empty artistIds / albumId).
 */
class TidalDeezerTrackTest {

    @Test
    fun `TrackSource fromRaw maps tidal and deezer`() {
        assertEquals(TrackSource.TIDAL, TrackSource.fromRaw("tidal"))
        assertEquals("tidal", TrackSource.TIDAL.raw)
        assertEquals(TrackSource.DEEZER, TrackSource.fromRaw("deezer"))
        assertEquals("deezer", TrackSource.DEEZER.raw)
        // Unknown/missing sources keep defaulting to SPOTIFY.
        assertEquals(TrackSource.SPOTIFY, TrackSource.fromRaw("something-new"))
        assertEquals(TrackSource.SPOTIFY, TrackSource.fromRaw(null))
    }

    @Test
    fun `fromCloudData decodes a TIDAL-exclusive post as link-out only`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "post1",
                "user" to mapOf("id" to "u1", "username" to "gabe"),
                "mediaType" to "track",
                "trackId" to "tdl:12345",
                "trackName" to "Deep Cut",
                "artistName" to "Obscure Artist",
                "trackSource" to "tidal",
                "tidalId" to "12345",
                "tidalURL" to "https://tidal.com/browse/track/12345",
                // Backend contract: artistIds is empty and there is no
                // spotifyURI/spotifyWebURL/appleMusicId on exclusive posts.
                "isrc" to "USX9P2500001",
            )
        )
        assertEquals(TrackSource.TIDAL, post.track.source)
        assertEquals("tdl:12345", post.track.id)
        assertEquals("12345", post.track.tidalId)
        assertEquals("https://tidal.com/browse/track/12345", post.track.tidalURL)
        assertEquals("https://tidal.com/browse/track/12345", post.track.tidalLinkOutUrl)
        // No synthesized Spotify link for a non-Spotify trackId.
        assertEquals("", post.track.spotifyURI)
        assertEquals("", post.track.spotifyWebURL)
        // Nothing to play in-app — there is no preview resolver for exclusives.
        assertNull(post.track.previewUrl)
    }

    @Test
    fun `fromCloudData decodes a Deezer-exclusive post as link-out only`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "post2",
                "user" to mapOf("id" to "u1", "username" to "gabe"),
                "mediaType" to "track",
                "trackId" to "dzr:67890",
                "trackName" to "Rare Groove",
                "artistName" to "Hidden Gem",
                "trackSource" to "deezer",
                "deezerId" to "67890",
                "deezerURL" to "https://www.deezer.com/track/67890",
            )
        )
        assertEquals(TrackSource.DEEZER, post.track.source)
        assertEquals("dzr:67890", post.track.id)
        assertEquals("67890", post.track.deezerId)
        assertEquals("https://www.deezer.com/track/67890", post.track.deezerURL)
        assertEquals("https://www.deezer.com/track/67890", post.track.deezerLinkOutUrl)
        assertEquals("", post.track.spotifyURI)
        assertEquals("", post.track.spotifyWebURL)
        assertNull(post.track.previewUrl)
    }

    @Test
    fun `CymbalTrack fromMap parses the exclusive post-doc shape`() {
        val track = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "tdl:555",
                "trackName" to "Deep Cut",
                "artistName" to "Obscure Artist",
                "trackSource" to "tidal",
                "tidalId" to "555",
                "tidalURL" to "https://tidal.com/browse/track/555",
                // A stray Spotify URI must be blanked for non-Spotify sources.
                "spotifyURI" to "spotify:track:tdl:555",
            )
        )
        assertEquals(TrackSource.TIDAL, track.source)
        assertEquals("555", track.tidalId)
        assertEquals("https://tidal.com/browse/track/555", track.tidalURL)
        assertEquals("", track.spotifyURI)
        // Empty strings parse to null, mirroring the audiomack fields.
        val empty = CymbalTrack.fromMap(
            mapOf(
                "trackId" to "dzr:1",
                "trackName" to "T",
                "artistName" to "A",
                "trackSource" to "deezer",
                "deezerId" to "",
                "deezerURL" to "",
            )
        )
        assertNull(empty.deezerId)
        assertNull(empty.deezerURL)
    }

    @Test
    fun `link-out targets are null for non-matching sources and blank urls`() {
        // A Spotify track that happens to carry the urls must NOT link out.
        val spotify = CymbalTrack(
            id = "spot1",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.SPOTIFY,
            tidalURL = "https://tidal.com/browse/track/1",
            deezerURL = "https://www.deezer.com/track/1",
        )
        assertNull(spotify.tidalLinkOutUrl)
        assertNull(spotify.deezerLinkOutUrl)

        // Blank url on an exclusive track is a graceful no-op, not a dead link.
        val blankTidal = CymbalTrack(
            id = "tdl:2", name = "Track", artistName = "Artist", albumName = "",
            source = TrackSource.TIDAL, tidalURL = "",
        )
        assertNull(blankTidal.tidalLinkOutUrl)
        val blankDeezer = CymbalTrack(
            id = "dzr:2", name = "Track", artistName = "Artist", albumName = "",
            source = TrackSource.DEEZER, deezerURL = "",
        )
        assertNull(blankDeezer.deezerLinkOutUrl)

        // Cross-source urls don't leak: a TIDAL track never Deezer-links out.
        val cross = CymbalTrack(
            id = "tdl:3", name = "Track", artistName = "Artist", albumName = "",
            source = TrackSource.TIDAL,
            tidalURL = "https://tidal.com/browse/track/3",
            deezerURL = "https://www.deezer.com/track/3",
        )
        assertEquals("https://tidal.com/browse/track/3", cross.tidalLinkOutUrl)
        assertNull(cross.deezerLinkOutUrl)
    }

    @Test
    fun `toSongDetailRoute carries the exclusive link-out urls`() {
        // Mirrors the audiomackUrl regression: the song page renders its
        // "Open in TIDAL/Deezer" CTA from the route hint before posts load.
        val tidal = CymbalTrack(
            id = "tdl:12345", name = "Deep Cut", artistName = "Obscure Artist",
            albumName = "", source = TrackSource.TIDAL,
            tidalId = "12345", tidalURL = "https://tidal.com/browse/track/12345",
        )
        val tidalRoute = tidal.toSongDetailRoute()
        assertEquals("tdl:12345", tidalRoute.trackId)
        assertEquals("tidal", tidalRoute.source)
        assertEquals("https://tidal.com/browse/track/12345", tidalRoute.tidalURL)
        assertNull(tidalRoute.deezerURL)

        val deezer = CymbalTrack(
            id = "dzr:67890", name = "Rare Groove", artistName = "Hidden Gem",
            albumName = "", source = TrackSource.DEEZER,
            deezerId = "67890", deezerURL = "https://www.deezer.com/track/67890",
        )
        val deezerRoute = deezer.toSongDetailRoute()
        assertEquals("deezer", deezerRoute.source)
        assertEquals("https://www.deezer.com/track/67890", deezerRoute.deezerURL)
        assertNull(deezerRoute.tidalURL)
    }

    // ── Go to Artist / Go to Album (post "…" menu) ──
    //
    // Unlike Audiomack (which links out to its own artist/album pages), TIDAL/
    // Deezer exclusives carry no artist/album URLs, no Spotify artistIds, and no
    // albumId — so both rows must simply not appear, never a dead item.

    private fun exclusivePost(source: TrackSource) = CymbalPost(
        id = "p1",
        user = CymbalUser(id = "u1", username = "gabe", displayName = "Gabe"),
        track = CymbalTrack(
            id = if (source == TrackSource.TIDAL) "tdl:1" else "dzr:1",
            name = "Track",
            artistName = "Artist",
            albumName = "Album",
            source = source,
            tidalId = if (source == TrackSource.TIDAL) "1" else null,
            tidalURL = if (source == TrackSource.TIDAL) "https://tidal.com/browse/track/1" else null,
            deezerId = if (source == TrackSource.DEEZER) "1" else null,
            deezerURL = if (source == TrackSource.DEEZER) "https://www.deezer.com/track/1" else null,
        ),
        mediaType = MediaType.TRACK,
    )

    @Test
    fun `go-to-artist and go-to-album rows stay hidden for exclusives`() {
        for (source in listOf(TrackSource.TIDAL, TrackSource.DEEZER)) {
            val post = exclusivePost(source)
            // Hidden with the flag off AND on — no artist/album destination exists.
            assertFalse(showGoToArtistRow(post = post, artistPagesEnabled = false))
            assertFalse(showGoToArtistRow(post = post, artistPagesEnabled = true))
            assertFalse(showGoToAlbumRow(post = post, artistPagesEnabled = false))
            assertFalse(showGoToAlbumRow(post = post, artistPagesEnabled = true))
        }
    }

    @Test
    fun `internal-nav tap builders are null for exclusives`() {
        // No Spotify artistIds/albumId → the builders return null, and (unlike
        // Audiomack) there is no external-url fallback, so the menu wiring's
        // final `?: {}` no-op is what remains behind the (hidden) rows.
        for (source in listOf(TrackSource.TIDAL, TrackSource.DEEZER)) {
            val post = exclusivePost(source)
            assertNull(
                onGoToArtistTap(
                    post,
                    onNavigateToArtist = {},
                    scope = kotlinx.coroutines.test.TestScope(),
                    resolveArtistId = { null },
                    onArtistNotFound = {},
                )
            )
            assertNull(
                onGoToAlbumTap(
                    post,
                    onNavigateToAlbum = {},
                    scope = kotlinx.coroutines.test.TestScope(),
                    resolveAlbumId = { null },
                    onAlbumNotFound = {},
                )
            )
        }
    }
}
