package fm.corus.android.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CymbalMessageTest {

    @Test
    fun `attachedSong builds from track fields when trackId is present`() {
        val message = trackMessage(
            trackId = "abc123",
            trackName = "Song",
            artistName = "Artist",
            spotifyURL = "https://open.spotify.com/track/abc123",
            spotifyURI = "spotify:track:abc123",
            previewUrl = "https://p3.cdn.com/preview.m4a",
            durationMs = 180_000,
        )

        val song = message.attachedSong
        assertEquals("abc123", song?.trackId)
        assertEquals("Song", song?.trackName)
        assertEquals("Artist", song?.artistName)
        assertEquals("spotify:track:abc123", song?.spotifyURI)
        assertEquals("https://open.spotify.com/track/abc123", song?.spotifyWebURL)
        assertEquals(180_000, song?.durationMs)
    }

    @Test
    fun `attachedSong derives trackId from spotifyURL for legacy messages`() {
        // Legacy message: pre-trackId persistence. Derive trackId from the canonical URL.
        val message = trackMessage(
            trackId = null,
            trackName = "Old Song",
            artistName = "Old Artist",
            spotifyURL = "https://open.spotify.com/track/leg4cy?si=xyz",
        )
        assertEquals("leg4cy", message.attachedSong?.trackId)
    }

    @Test
    fun `attachedSong returns null when no trackId can be derived`() {
        val message = trackMessage(
            trackId = null,
            trackName = "Mystery",
            artistName = "?",
            spotifyURL = null,
        )
        assertNull(message.attachedSong)
    }

    @Test
    fun `attachedSongSource reports SOUNDCLOUD when source field is set`() {
        val message = trackMessage(
            trackId = "sc:99",
            trackName = "Cloud Song",
            artistName = "Cloud Artist",
            trackSource = "soundcloud",
        )
        assertEquals(TrackSource.SOUNDCLOUD, message.attachedSongSource)
    }

    @Test
    fun `attachedSongSource falls back to sc prefix when source field is missing`() {
        val message = trackMessage(
            trackId = "sc:42",
            trackName = "Cloud Song",
            artistName = "Cloud Artist",
            trackSource = null,
        )
        assertEquals(TrackSource.SOUNDCLOUD, message.attachedSongSource)
    }

    @Test
    fun `attachedFilm builds from movie fields when movieId is present`() {
        val message = filmMessage(
            movieId = "550",
            movieTitle = "Fight Club",
            directorName = "David Fincher",
            releaseYear = "1999",
            tmdbWebURL = "https://www.themoviedb.org/movie/550",
        )
        val film = message.attachedFilm
        assertEquals("550", film?.movieId)
        assertEquals("Fight Club", film?.movieTitle)
        assertEquals("David Fincher", film?.directorName)
        assertEquals("1999", film?.releaseYear)
    }

    @Test
    fun `attachedFilm derives movieId from tmdbWebURL with slug suffix`() {
        // Legacy message: derive movieId from URL. TMDB sometimes appends a slug
        // after the numeric id (e.g. /movie/550-fight-club).
        val message = filmMessage(
            movieId = null,
            movieTitle = "Fight Club",
            tmdbWebURL = "https://www.themoviedb.org/movie/550-fight-club",
        )
        assertEquals("550", message.attachedFilm?.movieId)
    }

    private fun trackMessage(
        trackId: String?,
        trackName: String,
        artistName: String,
        spotifyURL: String? = null,
        spotifyURI: String? = null,
        previewUrl: String? = null,
        durationMs: Int? = null,
        trackSource: String? = null,
    ): CymbalMessage = CymbalMessage(
        id = "m1",
        threadId = "t1",
        fromUserId = "u1",
        type = MessageType.SHARED_TRACK,
        trackId = trackId,
        trackName = trackName,
        artistName = artistName,
        spotifyURI = spotifyURI,
        spotifyURL = spotifyURL,
        previewUrl = previewUrl,
        durationMs = durationMs,
        trackSource = trackSource,
    )

    @Test
    fun `fromMap parses editedAt from millis and reports isEdited`() {
        val editedMs = 1_700_000_000_000L
        val msg = CymbalMessage.fromMap(
            "m1",
            mapOf(
                "fromUserId" to "u1",
                "text" to "hello",
                "type" to "text",
                "createdAt" to 1_699_999_000_000L,
                "editedAt" to editedMs,
            ),
        )
        assertEquals(Date(editedMs), msg.editedAt)
        assertTrue(msg.isEdited)
    }

    @Test
    fun `fromMap leaves editedAt null when absent`() {
        val msg = CymbalMessage.fromMap(
            "m1",
            mapOf("fromUserId" to "u1", "text" to "hello", "type" to "text"),
        )
        assertNull(msg.editedAt)
        assertFalse(msg.isEdited)
    }

    @Test
    fun `fromFirestoreDoc parses editedAt from Timestamp`() {
        val editedMs = 1_700_000_000_000L
        val msg = CymbalMessage.fromFirestoreDoc(
            "m1",
            "t1",
            mapOf(
                "fromUserId" to "u1",
                "text" to "hello",
                "type" to "text",
                "editedAt" to Timestamp(Date(editedMs)),
            ),
        )
        assertEquals(Date(editedMs), msg.editedAt)
        assertTrue(msg.isEdited)
    }

    private fun filmMessage(
        movieId: String?,
        movieTitle: String,
        directorName: String = "",
        releaseYear: String = "",
        tmdbWebURL: String? = null,
    ): CymbalMessage = CymbalMessage(
        id = "m1",
        threadId = "t1",
        fromUserId = "u1",
        type = MessageType.SHARED_FILM,
        movieId = movieId,
        movieTitle = movieTitle,
        directorName = directorName,
        releaseYear = releaseYear,
        tmdbWebURL = tmdbWebURL,
    )
}
