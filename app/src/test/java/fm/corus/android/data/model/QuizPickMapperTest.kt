package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pick→payload mapper must mirror web `quizPicksToTastePicks`
 * (Corus-Web/app/lib/firestore/onboarding-taste-picks.ts) EXACTLY — both
 * clients hand the getOnboardingTasteMatches matcher identical shapes.
 */
class QuizPickMapperTest {

    private fun track(
        id: String = "t1",
        artist: String = "Sufjan Stevens",
        artistIds: List<String> = listOf("sp-artist-1"),
    ) = CymbalTrack(
        id = id,
        name = "Chicago",
        artistName = artist,
        artistIds = artistIds,
        albumName = "Illinois",
    )

    private fun movie(
        id: String = "m1",
        director: String = "Bong Joon-ho",
        directorIds: List<String> = listOf("tmdb-1"),
    ) = CymbalMovie(
        id = id,
        title = "Parasite",
        directorName = director,
        directorIds = directorIds,
        year = "2019",
    )

    @Test
    fun `song pick maps to song payload with track id and artist ids`() {
        val payload = quizPicksToTastePicks(listOf(QuizPick.Song(track())))
        assertEquals(
            listOf(
                mapOf(
                    "type" to "song",
                    "trackId" to "t1",
                    "artistName" to "Sufjan Stevens",
                    "artistIds" to listOf("sp-artist-1"),
                ),
            ),
            payload,
        )
    }

    @Test
    fun `film pick maps to film payload with movie id and director ids`() {
        val payload = quizPicksToTastePicks(listOf(QuizPick.Film(movie())))
        assertEquals(
            listOf(
                mapOf(
                    "type" to "film",
                    "movieId" to "m1",
                    "directorName" to "Bong Joon-ho",
                    "directorIds" to listOf("tmdb-1"),
                ),
            ),
            payload,
        )
    }

    @Test
    fun `director pick maps to a film payload with no movieId`() {
        val payload = quizPicksToTastePicks(
            listOf(QuizPick.Director(directorId = "tmdb-9", name = "Greta Gerwig", imageUrl = null)),
        )
        assertEquals(
            listOf(
                mapOf(
                    "type" to "film",
                    "directorName" to "Greta Gerwig",
                    "directorIds" to listOf("tmdb-9"),
                ),
            ),
            payload,
        )
        // Structural guarantee: the matcher's film branch must see NO movieId key.
        assertNull(payload.single()["movieId"])
    }

    @Test
    fun `director pick with empty id maps to empty directorIds not a blank entry`() {
        val payload = quizPicksToTastePicks(
            listOf(QuizPick.Director(directorId = "", name = "Someone", imageUrl = null)),
        )
        assertEquals(emptyList<String>(), payload.single()["directorIds"])
    }

    @Test
    fun `artist pick maps to artist payload wrapping the single id`() {
        val payload = quizPicksToTastePicks(
            listOf(QuizPick.Artist(artistId = "sp-9", name = "Radiohead", imageUrl = null)),
        )
        assertEquals(
            listOf(
                mapOf(
                    "type" to "artist",
                    "artistName" to "Radiohead",
                    "artistIds" to listOf("sp-9"),
                ),
            ),
            payload,
        )
    }

    @Test
    fun `artist pick with empty id still contributes its name`() {
        val payload = quizPicksToTastePicks(
            listOf(QuizPick.Artist(artistId = "", name = "Unknown Local Band", imageUrl = null)),
        )
        assertEquals("Unknown Local Band", payload.single()["artistName"])
        assertEquals(emptyList<String>(), payload.single()["artistIds"])
    }

    @Test
    fun `album pick is taste-wise its artist - name only, no ids`() {
        val payload = quizPicksToTastePicks(
            listOf(QuizPick.Album(albumId = "al-1", title = "Blonde", artistName = "Frank Ocean", coverUrl = null)),
        )
        assertEquals(
            listOf(
                mapOf(
                    "type" to "artist",
                    "artistName" to "Frank Ocean",
                    "artistIds" to emptyList<String>(),
                ),
            ),
            payload,
        )
    }

    @Test
    fun `mapper drops nothing and preserves order`() {
        val picks = listOf(
            QuizPick.Song(track()),
            QuizPick.Album(albumId = "al", title = "A", artistName = "B", coverUrl = null),
            QuizPick.Film(movie()),
            QuizPick.Director(directorId = "d", name = "D", imageUrl = null),
            QuizPick.Artist(artistId = "a", name = "A", imageUrl = null),
        )
        val payload = quizPicksToTastePicks(picks)
        assertEquals(
            listOf("song", "artist", "film", "film", "artist"),
            payload.map { it["type"] },
        )
    }

    // ── Pick identity + helpers ──

    @Test
    fun `pick ids are namespaced per kind so cross-kind ids never collide`() {
        assertEquals("t1", QuizPick.Song(track(id = "t1")).id)
        assertEquals("m1", QuizPick.Film(movie(id = "m1")).id)
        assertEquals("artist:x", QuizPick.Artist("x", "A", null).id)
        assertEquals("album:x", QuizPick.Album("x", "T", "A", null).id)
        assertEquals("director:x", QuizPick.Director("x", "D", null).id)
    }

    @Test
    fun `analytics kind matches the web values`() {
        assertEquals("song", QuizPick.Song(track()).kind)
        assertEquals("film", QuizPick.Film(movie()).kind)
        assertEquals("artist", QuizPick.Artist("x", "A", null).kind)
        assertEquals("album", QuizPick.Album("x", "T", "A", null).kind)
        assertEquals("director", QuizPick.Director("x", "D", null).kind)
    }

    @Test
    fun `postablePicks keeps only songs and films - taste-signal kinds never post`() {
        val picks = listOf(
            QuizPick.Artist("a", "A", null),
            QuizPick.Song(track()),
            QuizPick.Album("al", "T", "B", null),
            QuizPick.Film(movie()),
            QuizPick.Director("d", "D", null),
        )
        assertEquals(listOf("t1", "m1"), postablePicks(picks).map { it.id })
    }

    @Test
    fun `film pick subtitle falls back to release year when the director is unresolved`() {
        val pick = QuizPick.Film(movie(director = ""))
        assertEquals("2019", pick.pickSubtitle())
    }

    @Test
    fun `blank art is treated as missing`() {
        val pick = QuizPick.Artist("a", "A", imageUrl = "")
        assertNull(pick.pickArt())
    }
}
