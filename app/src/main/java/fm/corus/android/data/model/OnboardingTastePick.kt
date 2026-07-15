package fm.corus.android.data.model

/**
 * A pick collected by the taste-match onboarding quiz. Five kinds mirror the
 * web QuizPick union (app/onboarding/page.tsx): songs and films are postable
 * on the head-start step; artists, albums and directors are taste signal only.
 *
 * [id] is the dedup key across kinds — identical to web's `p.id` scheme so a
 * re-tapped search row is a no-op, not a duplicate pick.
 */
sealed class QuizPick {
    abstract val id: String

    /** The analytics `kind` param value — matches web exactly. */
    abstract val kind: String

    data class Song(val track: CymbalTrack) : QuizPick() {
        override val id: String get() = track.id
        override val kind: String get() = "song"
    }

    /** [movie] should carry a resolved directorName when possible (the quiz
     *  resolves it via getMovieDetails on add); the pick stays usable without
     *  one — it still posts, it just contributes nothing to matching. */
    data class Film(val movie: CymbalMovie) : QuizPick() {
        override val id: String get() = movie.id
        override val kind: String get() = "film"
    }

    data class Artist(
        val artistId: String,
        val name: String,
        val imageUrl: String?,
    ) : QuizPick() {
        override val id: String get() = "artist:$artistId"
        override val kind: String get() = "artist"
    }

    data class Album(
        val albumId: String,
        val title: String,
        val artistName: String,
        val coverUrl: String?,
    ) : QuizPick() {
        override val id: String get() = "album:$albumId"
        override val kind: String get() = "album"
    }

    data class Director(
        val directorId: String,
        val name: String,
        val imageUrl: String?,
    ) : QuizPick() {
        override val id: String get() = "director:$directorId"
        override val kind: String get() = "director"
    }
}

/** Art URL for the pick's tray tile / venn cover. Mirrors web `pickArt`. */
fun QuizPick.pickArt(): String? = when (this) {
    is QuizPick.Song -> track.albumArtURL
    is QuizPick.Film -> movie.posterURL
    is QuizPick.Artist -> imageUrl
    is QuizPick.Album -> coverUrl
    is QuizPick.Director -> imageUrl
}?.takeIf { it.isNotBlank() }

/** Primary line for the pick. Mirrors web `pickTitle`. */
fun QuizPick.pickTitle(): String = when (this) {
    is QuizPick.Song -> track.name
    is QuizPick.Film -> movie.title
    is QuizPick.Artist -> name
    is QuizPick.Album -> title
    is QuizPick.Director -> name
}

/**
 * Secondary line for the pick. Mirrors web `pickSubtitle` — including its
 * literal "Artist"/"Director" fallbacks; callers that render user-facing text
 * substitute the localized row labels for those two kinds instead.
 */
fun QuizPick.pickSubtitle(): String = when (this) {
    is QuizPick.Song -> track.artistName
    is QuizPick.Film -> movie.directorName.ifBlank { movie.year }
    is QuizPick.Artist -> "Artist"
    is QuizPick.Album -> artistName
    is QuizPick.Director -> "Director"
}

/**
 * Picks that can become posts on the head-start screen. Only songs and films
 * have a post type — artists/albums/directors are taste signal only. Mirrors
 * web `postablePicks`.
 */
fun postablePicks(picks: List<QuizPick>): List<QuizPick> =
    picks.filter { it is QuizPick.Song || it is QuizPick.Film }

/** Max picks the quiz accepts — matches web `MAX_PICKS`. */
const val MAX_QUIZ_PICKS = 8

/**
 * Quiz picks → the `getOnboardingTasteMatches` callable's `picks` payload.
 * Pure, and an EXACT mirror of web `quizPicksToTastePicks`
 * (lib/firestore/onboarding-taste-picks.ts) so both clients hand the matcher
 * identical shapes. Drops nothing: even an id-less artist still contributes
 * its name (the backend name-buckets cover non-Spotify). An album is
 * taste-wise its artist (name-only — album search carries no artist ids); a
 * director is taste-wise a film pick with no movie attached (the matcher's
 * film branch reads directorName/Ids and ignores the missing movieId).
 */
fun quizPicksToTastePicks(picks: List<QuizPick>): List<Map<String, Any?>> =
    picks.map { p ->
        when (p) {
            is QuizPick.Film -> mapOf(
                "type" to "film",
                "movieId" to p.movie.id,
                "directorName" to p.movie.directorName,
                "directorIds" to p.movie.directorIds,
            )
            is QuizPick.Director -> mapOf(
                "type" to "film",
                "directorName" to p.name,
                "directorIds" to if (p.directorId.isNotEmpty()) listOf(p.directorId) else emptyList<String>(),
            )
            is QuizPick.Artist -> mapOf(
                "type" to "artist",
                "artistName" to p.name,
                "artistIds" to if (p.artistId.isNotEmpty()) listOf(p.artistId) else emptyList<String>(),
            )
            is QuizPick.Album -> mapOf(
                "type" to "artist",
                "artistName" to p.artistName,
                "artistIds" to emptyList<String>(),
            )
            is QuizPick.Song -> mapOf(
                "type" to "song",
                "trackId" to p.track.id,
                "artistName" to p.track.artistName,
                "artistIds" to p.track.artistIds,
            )
        }
    }
