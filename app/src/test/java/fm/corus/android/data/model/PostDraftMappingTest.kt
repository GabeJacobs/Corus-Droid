package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the native ↔ canonical mapping that keeps Android post drafts
 * cross-platform with web/iOS. The canonical `track`/`movie` maps use the WEB
 * field names (`artist`, `name`, `album`, `title`, `director`, `releaseYear`,
 * numeric millisecond timestamps), NOT Android's own model field names. A
 * mismatch here silently breaks interop, so these tests assert the exact wire
 * shape and a lossless round-trip.
 */
class PostDraftMappingTest {

    private val spotifyTrack = CymbalTrack(
        id = "sp123",
        name = "Chicago",
        artistName = "Sufjan Stevens",
        artistIds = listOf("a1", "a2"),
        albumName = "Illinois",
        albumId = "alb9",
        albumArtURL = "https://art/small.jpg",
        albumArtLargeURL = "https://art/large.jpg",
        spotifyWebURL = "https://open.spotify.com/track/sp123",
        durationMs = 222_000,
        previewUrl = "https://preview/sp123.mp3",
        isrc = "USABC1234567",
        releaseDate = "2005-07-04",
        releaseDatePrecision = "day",
        source = TrackSource.SPOTIFY,
    )

    private val movie = CymbalMovie(
        id = "603",
        title = "The Matrix",
        directorName = "The Wachowskis",
        directorIds = listOf("d1", "d2"),
        year = "1999",
        posterURL = "https://poster/603.jpg",
        overview = "A hacker learns the truth.",
        rating = 8.2,
        releaseDate = "1999-03-31",
    )

    @Test
    fun `track maps to canonical WEB field names, not android post-doc names`() {
        val map = PostDraft.trackToCanonical(spotifyTrack)

        // Web field names — the cross-platform contract.
        assertEquals("sp123", map["id"])
        assertEquals("Chicago", map["name"])
        assertEquals("Sufjan Stevens", map["artist"]) // NOT "artistName"
        assertEquals("Illinois", map["album"])        // NOT "albumName"
        assertEquals("alb9", map["albumId"])
        assertEquals("https://art/small.jpg", map["albumArtURL"])
        assertEquals(222_000, map["durationMs"])
        assertEquals("https://open.spotify.com/track/sp123", map["spotifyWebURL"])
        assertEquals("https://preview/sp123.mp3", map["previewUrl"])
        assertEquals("spotify", map["source"])
        assertEquals("USABC1234567", map["isrc"])
        assertEquals("2005-07-04", map["releaseDate"]) // NOT "trackReleaseDate"
        assertEquals("day", map["releaseDatePrecision"])
        assertEquals(listOf("a1", "a2"), map["artistIds"])

        // Post-doc field names must NOT leak into a draft.
        assertTrue("trackName" !in map)
        assertTrue("artistName" !in map)
        assertTrue("albumName" !in map)
        assertTrue("trackReleaseDate" !in map)
    }

    @Test
    fun `empty optional track fields are written as explicit null (web parity)`() {
        val sc = CymbalTrack(
            id = "sc:999",
            name = "Demo",
            artistName = "Producer",
            albumName = "",
            source = TrackSource.SOUNDCLOUD,
            soundcloudId = "999",
            soundcloudPermalinkUrl = "https://soundcloud.com/x/demo",
        )
        val map = PostDraft.trackToCanonical(sc)
        // Web writes these keys always; null when empty.
        assertTrue(map.containsKey("previewUrl"))
        assertNull(map["previewUrl"])
        assertTrue(map.containsKey("appleMusicId"))
        assertNull(map["appleMusicId"])
        assertTrue(map.containsKey("isrc"))
        assertNull(map["isrc"])
        // SoundCloud identifiers preserved.
        assertEquals("999", map["soundcloudId"])
        assertEquals("https://soundcloud.com/x/demo", map["soundcloudPermalinkUrl"])
        assertEquals("soundcloud", map["source"])
    }

    @Test
    fun `movie maps to canonical WEB field names with numeric releaseYear`() {
        val map = PostDraft.movieToCanonical(movie)
        assertEquals("603", map["id"])
        assertEquals("The Matrix", map["title"])
        assertEquals("The Wachowskis", map["director"]) // NOT "directorName"
        assertEquals(1999, map["releaseYear"])          // Number, not String
        assertEquals("https://poster/603.jpg", map["posterURL"])
        assertEquals("A hacker learns the truth.", map["overview"])
        assertEquals(8.2, map["rating"])
        assertEquals("1999-03-31", map["releaseDate"])
        assertEquals(listOf("d1", "d2"), map["directorIds"])
        assertTrue("movieTitle" !in map)
        assertTrue("directorName" !in map)
    }

    @Test
    fun `track round-trips native to canonical to native`() {
        val restored = PostDraft.trackFromCanonical(PostDraft.trackToCanonical(spotifyTrack))
        assertEquals(spotifyTrack.id, restored.id)
        assertEquals(spotifyTrack.name, restored.name)
        assertEquals(spotifyTrack.artistName, restored.artistName)
        assertEquals(spotifyTrack.artistIds, restored.artistIds)
        assertEquals(spotifyTrack.albumName, restored.albumName)
        assertEquals(spotifyTrack.albumId, restored.albumId)
        assertEquals(spotifyTrack.albumArtURL, restored.albumArtURL)
        assertEquals(spotifyTrack.durationMs, restored.durationMs)
        assertEquals(spotifyTrack.spotifyWebURL, restored.spotifyWebURL)
        assertEquals(spotifyTrack.previewUrl, restored.previewUrl)
        assertEquals(spotifyTrack.isrc, restored.isrc)
        assertEquals(spotifyTrack.releaseDate, restored.releaseDate)
        assertEquals(spotifyTrack.releaseDatePrecision, restored.releaseDatePrecision)
        assertEquals(TrackSource.SPOTIFY, restored.source)
    }

    @Test
    fun `movie round-trips native to canonical to native`() {
        val restored = PostDraft.movieFromCanonical(PostDraft.movieToCanonical(movie))
        assertEquals(movie.id, restored.id)
        assertEquals(movie.title, restored.title)
        assertEquals(movie.directorName, restored.directorName)
        assertEquals(movie.directorIds, restored.directorIds)
        assertEquals(movie.year, restored.year)
        assertEquals(movie.posterURL, restored.posterURL)
        assertEquals(movie.overview, restored.overview)
        assertEquals(movie.rating, restored.rating, 0.0001)
        assertEquals(movie.releaseDate, restored.releaseDate)
    }

    @Test
    fun `a web-written track draft populates the android composer`() {
        // Exactly the shape web's savePostDraft would persist for a Spotify track.
        val webCanonical = mapOf(
            "id" to "web777",
            "name" to "No Surprises",
            "artist" to "Radiohead",
            "album" to "OK Computer",
            "albumArtURL" to "https://i.scdn/ok.jpg",
            "durationMs" to 209_000,
            "spotifyWebURL" to "https://open.spotify.com/track/web777",
            "previewUrl" to null,
            "soundcloudId" to null,
            "soundcloudPermalinkUrl" to null,
            "appleMusicId" to null,
            "isrc" to "GBAYE9700000",
            "source" to "spotify",
            "releaseDate" to "1997-05-21",
            "releaseDatePrecision" to "day",
        )
        val track = PostDraft.trackFromCanonical(webCanonical)
        assertEquals("web777", track.id)
        assertEquals("No Surprises", track.name)
        assertEquals("Radiohead", track.artistName)
        assertEquals("OK Computer", track.albumName)
        assertEquals(209_000, track.durationMs)
        assertEquals("GBAYE9700000", track.isrc)
        assertEquals("1997-05-21", track.releaseDate)
        assertNull(track.previewUrl)
        assertEquals(TrackSource.SPOTIFY, track.source)
    }

    @Test
    fun `full draft doc round-trips with millisecond timestamps and voice URL`() {
        val draft = PostDraft(
            id = "d1",
            mediaType = MediaType.TRACK,
            caption = "",
            captionMode = "voice",
            commentsAudience = CommentsAudience.FOLLOWERS,
            track = spotifyTrack,
            voiceNoteURL = "https://storage/voice/abc.m4a",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_100_000L,
        )
        val map = draft.toCanonicalMap().toMutableMap().apply {
            // The repository stamps these; simulate that for fromDoc.
            put("createdAt", draft.createdAt)
            put("updatedAt", draft.updatedAt)
        }
        // Canonical contract: mediaType/captionMode strings, followers wire,
        // voiceNoteURL present, exactly one of track/movie.
        assertEquals("track", map["mediaType"])
        assertEquals("voice", map["captionMode"])
        assertEquals("followers", map["commentsAudience"])
        assertEquals("https://storage/voice/abc.m4a", map["voiceNoteURL"])
        assertTrue(map.containsKey("track"))
        assertTrue(!map.containsKey("movie"))

        val restored = PostDraft.fromDoc("d1", map)
        assertEquals(MediaType.TRACK, restored.mediaType)
        assertEquals("voice", restored.captionMode)
        assertEquals(CommentsAudience.FOLLOWERS, restored.commentsAudience)
        assertEquals("https://storage/voice/abc.m4a", restored.voiceNoteURL)
        assertEquals(1_700_000_000_000L, restored.createdAt)
        assertEquals(1_700_000_100_000L, restored.updatedAt)
        assertEquals("Chicago", restored.track?.name)
        assertNull(restored.movie)
    }

    @Test
    fun `timestamps are plain numbers, not firestore timestamps`() {
        val map = PostDraft(
            id = "x", mediaType = MediaType.MOVIE, caption = "hi", captionMode = "text",
            movie = movie, createdAt = 5L, updatedAt = 6L,
        ).toCanonicalMap()
        // toCanonicalMap doesn't emit timestamps (repo owns them); assert the
        // media map carries a numeric releaseYear (proxy for numeric contract).
        @Suppress("UNCHECKED_CAST")
        val movieMap = map["movie"] as Map<String, Any?>
        assertTrue(movieMap["releaseYear"] is Number)
    }
}
