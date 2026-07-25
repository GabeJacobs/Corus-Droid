package fm.corus.android.data.model

import fm.corus.android.data.remote.parseAudiomackPreviewResponse
import fm.corus.android.data.repository.parseUnifiedTrack
import fm.corus.android.domain.audiomackIdFromTrackId
import fm.corus.android.ui.components.onGoToAlbumTap
import fm.corus.android.ui.components.onGoToArtistTap
import fm.corus.android.ui.components.showGoToAlbumRow
import fm.corus.android.ui.components.showGoToArtistRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audiomack tracks now play a ~30s in-app preview (resolved at play time via the
 * `resolveAudiomackPreview` callable), exactly like Spotify/Apple previews,
 * while full-song playback stays link-out only via the `audiomackUrl`
 * badge/capsule. These tests pin the load-bearing bits of that contract: the
 * `source` discriminator parses "audiomack" -> [TrackSource.AUDIOMACK], the
 * link-out target is the `audiomackUrl`, the raw Audiomack id is recoverable
 * from the `amk:` trackId prefix, and the preview response parses its URL.
 */
class AudiomackTrackTest {

    @Test
    fun `TrackSource fromRaw maps audiomack to AUDIOMACK`() {
        assertEquals(TrackSource.AUDIOMACK, TrackSource.fromRaw("audiomack"))
        assertEquals("audiomack", TrackSource.AUDIOMACK.raw)
    }

    @Test
    fun `parseUnifiedTrack maps an Audiomack searchSongs result`() {
        val track = parseUnifiedTrack(
            mapOf(
                "id" to "amk:12345",
                "name" to "Soundgasm",
                "artistName" to "Rema",
                "source" to "audiomack",
                "albumName" to "",
                "albumArtURL" to "https://assets.audiomack.com/x.jpg",
                "durationMs" to 132000,
                "audiomackId" to "12345",
                "audiomackUrl" to "https://audiomack.com/rema/song/soundgasm",
                // Backend contract: isrc + previewUrl are null for Audiomack.
            )
        )
        requireNotNull(track)
        assertEquals(TrackSource.AUDIOMACK, track.source)
        assertEquals("amk:12345", track.id)
        assertEquals("12345", track.audiomackId)
        assertEquals("https://audiomack.com/rema/song/soundgasm", track.audiomackUrl)
        // The search result carries no preview / isrc and no synthesized Spotify
        // link; the ~30s preview is resolved at play time from the amk: id.
        assertNull(track.previewUrl)
        assertNull(track.isrc)
        assertEquals("", track.spotifyURI)
    }

    @Test
    fun `parseUnifiedTrack maps the explicit flag when present`() {
        val track = parseUnifiedTrack(
            mapOf(
                "id" to "spot1",
                "name" to "Track",
                "artistName" to "Artist",
                "explicit" to true,
            )
        )
        requireNotNull(track)
        assertTrue(track.explicit)
    }

    @Test
    fun `parseUnifiedTrack defaults explicit to false when omitted`() {
        val track = parseUnifiedTrack(
            mapOf(
                "id" to "spot1",
                "name" to "Track",
                "artistName" to "Artist",
            )
        )
        requireNotNull(track)
        assertFalse(track.explicit)
    }

    @Test
    fun `link-out target for an Audiomack track is its audiomackUrl`() {
        val track = CymbalTrack(
            id = "amk:1",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.AUDIOMACK,
            audiomackId = "1",
            audiomackUrl = "https://audiomack.com/artist/song/track",
        )
        assertEquals("https://audiomack.com/artist/song/track", track.audiomackLinkOutUrl)
    }

    @Test
    fun `link-out target is null for non-Audiomack sources and blank urls`() {
        // A Spotify track that happens to carry an audiomackUrl must NOT link out.
        val spotify = CymbalTrack(
            id = "spot1",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.SPOTIFY,
            audiomackUrl = "https://audiomack.com/artist/song/track",
        )
        assertNull(spotify.audiomackLinkOutUrl)

        // Blank url on an Audiomack track is a graceful no-op, not a dead link.
        val blank = CymbalTrack(
            id = "amk:2",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.AUDIOMACK,
            audiomackUrl = "",
        )
        assertNull(blank.audiomackLinkOutUrl)
    }

    @Test
    fun `toSongDetailRoute carries audiomackUrl so a postless search track keeps its link-out`() {
        // Regression: opening a not-yet-posted Audiomack track from search used to
        // reach the song page without its link-out url (it was sourced only from a
        // loaded post), so no "Listen on Audiomack" button showed. The route must
        // now carry audiomackUrl through navigation.
        val track = CymbalTrack(
            id = "amk:1649",
            name = "Stick Freestyle (Dreamville Remix)",
            artistName = "Des",
            albumName = "",
            source = TrackSource.AUDIOMACK,
            audiomackId = "1649",
            audiomackUrl = "https://audiomack.com/des/song/stick-freestyle-dreamville-remix",
        )
        val route = track.toSongDetailRoute()
        assertEquals("amk:1649", route.trackId)
        assertEquals("audiomack", route.source)
        assertEquals(
            "https://audiomack.com/des/song/stick-freestyle-dreamville-remix",
            route.audiomackUrl,
        )
    }

    @Test
    fun `audiomackIdFromTrackId strips the amk prefix`() {
        // The preview resolver recovers the raw Audiomack id from the `amk:`
        // trackId prefix (the id isn't threaded separately through the queue).
        assertEquals("12345", audiomackIdFromTrackId("amk:12345"))
        // Ids can themselves contain a colon (e.g. slug-shaped ids) — only the
        // leading `amk:` prefix is stripped, the rest is preserved verbatim.
        assertEquals("rema:soundgasm", audiomackIdFromTrackId("amk:rema:soundgasm"))
    }

    @Test
    fun `audiomackIdFromTrackId returns null for non-audiomack or empty ids`() {
        // Wrong/missing prefix → not an Audiomack id.
        assertNull(audiomackIdFromTrackId("spot1"))
        assertNull(audiomackIdFromTrackId("am:9999")) // Apple, not Audiomack
        assertNull(audiomackIdFromTrackId(""))
        // Prefix present but nothing after it → nothing to resolve.
        assertNull(audiomackIdFromTrackId("amk:"))
    }

    @Test
    fun `a feed Audiomack post is a first-class auto-advance queue member`() {
        // Queue membership regression: the feed builds its auto-advance queue from
        // every TRACK post (any source — no `source != AUDIOMACK` filter), and the
        // resolver recovers the raw Audiomack id from the queued track's `amk:`
        // trackId at play time — mirroring how a queued SoundCloud track carries
        // its `soundcloudId` for resolution. This pins the feed decode path
        // (CymbalPost.fromCloudData) that feeds the queue, so an Audiomack post
        // stays a proper next/auto-advance member rather than a dead single-track.
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "post1",
                "user" to mapOf("id" to "u1", "username" to "rema"),
                "mediaType" to "track",
                "trackId" to "amk:12345",
                "trackName" to "Soundgasm",
                "artistName" to "Rema",
                "trackSource" to "audiomack",
                "audiomackId" to "12345",
                "audiomackUrl" to "https://audiomack.com/rema/song/soundgasm",
                // Backend contract: Audiomack posts carry no preview / isrc — the
                // ~30s preview is resolved at play time from the amk: id.
            )
        )
        // Survives the feed's `mediaType == TRACK` queue filter.
        assertEquals(MediaType.TRACK, post.mediaType)
        assertTrue(post.isTrack)
        // Queued track keeps its AUDIOMACK source, so playInternal routes it to
        // the Audiomack preview resolver (not the Apple/Spotify preview lookup).
        assertEquals(TrackSource.AUDIOMACK, post.track.source)
        // The auto-advance resolver recovers the raw id from the queued trackId —
        // no dedicated queue field needed (unlike SoundCloud's soundcloudId).
        assertEquals("amk:12345", post.track.id)
        assertEquals("12345", audiomackIdFromTrackId(post.track.id))
        // Nothing to play eagerly from the post itself; resolved at play time.
        assertNull(post.track.previewUrl)
        // Full song stays link-out only via the Audiomack badge.
        assertEquals("https://audiomack.com/rema/song/soundgasm", post.track.audiomackLinkOutUrl)
    }

    @Test
    fun `parseAudiomackPreviewResponse extracts a non-blank previewUrl`() {
        // Signed, short-lived audio/mp4 URL returned by resolveAudiomackPreview.
        assertEquals(
            "https://audiomack.example/signed/abc.m4a?exp=1",
            parseAudiomackPreviewResponse(
                mapOf("previewUrl" to "https://audiomack.example/signed/abc.m4a?exp=1"),
            ),
        )
    }

    @Test
    fun `parseAudiomackPreviewResponse degrades to null on missing or blank url`() {
        // Null payload, missing key, blank/whitespace, and wrong type all no-op
        // so playback degrades gracefully to the Audiomack link-out.
        assertNull(parseAudiomackPreviewResponse(null))
        assertNull(parseAudiomackPreviewResponse(emptyMap()))
        assertNull(parseAudiomackPreviewResponse(mapOf("previewUrl" to "")))
        assertNull(parseAudiomackPreviewResponse(mapOf("previewUrl" to "   ")))
        assertNull(parseAudiomackPreviewResponse(mapOf("previewUrl" to 42)))
    }

    // ── Audiomack artist / album page URLs (Go to Artist / Go to Album) ──
    //
    // Audiomack is source-locked with no Spotify artistIds/albumId, so the post
    // menu's "Go to Artist"/"Go to Album" rows and the tappable artist name link
    // out to Audiomack's own pages. The backend supplies `audiomackArtistUrl`
    // (always) and `audiomackAlbumUrl` ("" for loose singles) on both search
    // tracks and post payloads.

    private fun audiomackPost(
        artistUrl: String? = "https://audiomack.com/dancrewofficial",
        albumUrl: String? = "https://audiomack.com/burna-boy/album/no-sign-of-weakness-0152862",
    ) = CymbalPost(
        id = "p1",
        user = CymbalUser(id = "u1", username = "rema", displayName = "Rema"),
        track = CymbalTrack(
            id = "amk:1",
            name = "Track",
            artistName = "Artist",
            albumName = "Album",
            source = TrackSource.AUDIOMACK,
            audiomackId = "1",
            audiomackUrl = "https://audiomack.com/artist/song/track",
            audiomackArtistUrl = artistUrl,
            audiomackAlbumUrl = albumUrl,
        ),
        mediaType = MediaType.TRACK,
    )

    @Test
    fun `parseUnifiedTrack maps Audiomack artist and album urls`() {
        val track = parseUnifiedTrack(
            mapOf(
                "id" to "amk:12345",
                "name" to "Soundgasm",
                "artistName" to "Rema",
                "source" to "audiomack",
                "audiomackId" to "12345",
                "audiomackUrl" to "https://audiomack.com/rema/song/soundgasm",
                "audiomackArtistUrl" to "https://audiomack.com/rema",
                "audiomackAlbumUrl" to "https://audiomack.com/rema/album/rave-and-roses",
            )
        )
        requireNotNull(track)
        assertEquals("https://audiomack.com/rema", track.audiomackArtistUrl)
        assertEquals("https://audiomack.com/rema/album/rave-and-roses", track.audiomackAlbumUrl)
    }

    @Test
    fun `parseUnifiedTrack treats an empty Audiomack album url as absent`() {
        val track = parseUnifiedTrack(
            mapOf(
                "id" to "amk:1",
                "name" to "Freestyle",
                "artistName" to "Des",
                "source" to "audiomack",
                "audiomackArtistUrl" to "https://audiomack.com/des",
                "audiomackAlbumUrl" to "", // loose single / freestyle — no album
            )
        )
        requireNotNull(track)
        assertEquals("https://audiomack.com/des", track.audiomackArtistUrl)
        assertNull(track.audiomackAlbumUrl)
    }

    @Test
    fun `audiomack artist and album link-outs surface non-blank urls`() {
        val track = CymbalTrack(
            id = "amk:1",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.AUDIOMACK,
            audiomackArtistUrl = "https://audiomack.com/artist",
            audiomackAlbumUrl = "https://audiomack.com/artist/album/z",
        )
        assertEquals("https://audiomack.com/artist", track.audiomackArtistLinkOutUrl)
        assertEquals("https://audiomack.com/artist/album/z", track.audiomackAlbumLinkOutUrl)
    }

    @Test
    fun `audiomack link-outs are null for non-Audiomack sources and blank urls`() {
        // A Spotify track that happens to carry the urls must NOT link out.
        val spotify = CymbalTrack(
            id = "spot1",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.SPOTIFY,
            audiomackArtistUrl = "https://audiomack.com/artist",
            audiomackAlbumUrl = "https://audiomack.com/artist/album/z",
        )
        assertNull(spotify.audiomackArtistLinkOutUrl)
        assertNull(spotify.audiomackAlbumLinkOutUrl)

        // Loose single: artist links out, album is a graceful no-op (never a dead row).
        val loose = CymbalTrack(
            id = "amk:2",
            name = "Track",
            artistName = "Artist",
            albumName = "",
            source = TrackSource.AUDIOMACK,
            audiomackArtistUrl = "https://audiomack.com/artist",
            audiomackAlbumUrl = "",
        )
        assertEquals("https://audiomack.com/artist", loose.audiomackArtistLinkOutUrl)
        assertNull(loose.audiomackAlbumLinkOutUrl)
    }

    @Test
    fun `fromCloudData decodes Audiomack artist and album urls onto the post track`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "post1",
                "mediaType" to "track",
                "trackId" to "amk:12345",
                "trackSource" to "audiomack",
                "audiomackUrl" to "https://audiomack.com/rema/song/soundgasm",
                "audiomackArtistUrl" to "https://audiomack.com/rema",
                "audiomackAlbumUrl" to "https://audiomack.com/rema/album/rave-and-roses",
            )
        )
        assertEquals("https://audiomack.com/rema", post.track.audiomackArtistLinkOutUrl)
        assertEquals("https://audiomack.com/rema/album/rave-and-roses", post.track.audiomackAlbumLinkOutUrl)
    }

    @Test
    fun `fromCloudData treats an empty Audiomack album url as absent`() {
        val post = CymbalPost.fromCloudData(
            mapOf(
                "id" to "post1",
                "mediaType" to "track",
                "trackId" to "amk:1",
                "trackSource" to "audiomack",
                "audiomackArtistUrl" to "https://audiomack.com/rema",
                "audiomackAlbumUrl" to "",
            )
        )
        assertEquals("https://audiomack.com/rema", post.track.audiomackArtistLinkOutUrl)
        assertNull(post.track.audiomackAlbumLinkOutUrl)
    }

    @Test
    fun `audiomack go-to-artist row shows even with artist_pages_enabled off`() {
        // Link-out only — independent of the internal artist-pages flag.
        assertTrue(showGoToArtistRow(post = audiomackPost(), artistPagesEnabled = false))
    }

    @Test
    fun `audiomack go-to-artist row hidden when artist url is blank`() {
        assertFalse(showGoToArtistRow(post = audiomackPost(artistUrl = ""), artistPagesEnabled = false))
    }

    @Test
    fun `audiomack go-to-album row shows when album url present`() {
        assertTrue(showGoToAlbumRow(post = audiomackPost(), artistPagesEnabled = false))
    }

    @Test
    fun `audiomack go-to-album row hidden for a loose single`() {
        // "" album url -> never a dead row.
        assertFalse(showGoToAlbumRow(post = audiomackPost(albumUrl = ""), artistPagesEnabled = false))
    }

    @Test
    fun `audiomack internal-nav tap builders stay null so the link-out fallback fires`() {
        // Audiomack carries no Spotify artistIds/albumId, so the internal-nav
        // builders return null and the menu wiring falls through to the Audiomack
        // link-out branch.
        assertNull(
            onGoToArtistTap(
                audiomackPost(),
                onNavigateToArtist = {},
                scope = kotlinx.coroutines.test.TestScope(),
                resolveArtistId = { null },
                onArtistNotFound = {},
            )
        )
        assertNull(
            onGoToAlbumTap(
                audiomackPost(),
                onNavigateToAlbum = {},
                scope = kotlinx.coroutines.test.TestScope(),
                resolveAlbumId = { null },
                onAlbumNotFound = {},
            )
        )
    }
}
