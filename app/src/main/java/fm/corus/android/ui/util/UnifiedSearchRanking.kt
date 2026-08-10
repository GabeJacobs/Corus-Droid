package fm.corus.android.ui.util

import java.text.Normalizer

/**
 * Section ordering for the blended ("All") results, shared by the Search tab
 * and the compose picker so both surfaces rank the same way.
 *
 * The blended view otherwise renders a fixed order with music above film,
 * which is right for the overwhelming majority of queries — most of what
 * people search for and post is music. It reads badly in one specific case:
 * the query is a film title, the film search has a perfect hit, and the song
 * results are only soundtrack covers and compilation pressings that happen to
 * carry the film's name ("Brokeback Mountain (From "Brokeback Mountain")" by
 * a workout-mix act). The right answer is on screen but sits under four wrong
 * ones.
 *
 * Film leads when a film title matches the query exactly and music has no
 * real hit of its own. Music hits are: an artist-row name match, a visible
 * song whose lead artist matches the query, or an exact song title by a
 * non-soundtrack artist — unless the visible song rows are soundtrack-heavy
 * for the query (orchestras, "Various Artists", "Hymn to the Fallen" / "From
 * …" pressings). That last escape lets "saving private ryan" lead with Film
 * even when a couple of catalog rows are titled exactly after the movie.
 * Dual song/film titles (Purple Rain, Dune, Rush) keep music first via a
 * real exact song credit. Bands whose name is also a film (Vampire Weekend,
 * The Doors) keep music first via the artist-row / song-artist checks.
 *
 * Crucially, checks look only at the rows the blended view actually shows,
 * not the whole loaded result set. Cover and tribute acts publish tracks
 * titled exactly after famous films further down the list; scanning
 * everything suppressed the swap on the Brokeback query it was written for.
 *
 * Kotlin port of iOS `UnifiedSearchRanking` — keep the two in sync.
 */
object UnifiedSearchRanking {

    /**
     * Rows each vertical shows in the blended view before "See All". Both
     * surfaces cap at 4.
     */
    const val BLENDED_PREVIEW_COUNT = 4

    /**
     * How many visible songs must look like soundtrack pressings for the
     * query before we treat the music section as OST noise and let Film lead
     * even when some rows share the film's exact title.
     */
    const val SOUNDTRACK_HEAVY_THRESHOLD = 2

    /** Unicode combining marks, stripped after NFD decomposition. */
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    private val PARENTHETICAL = Regex("[(\\[]([^)\\]]+)[)\\]]")

    private val WEAK_SOUNDTRACK_ARTIST_MARKERS = listOf(
        "various artists",
        "soundtrack",
        "orchestra",
        "symphony",
        "philharmonic",
        "motion picture",
        "film score",
        "movie score",
        "cinematic",
        "karaoke",
    )

    private val SOUNDTRACK_PARENTHETICAL_MARKERS = listOf(
        "from",
        "theme",
        "score",
        "soundtrack",
        "hymn",
        "movie",
        "motion picture",
        "ost",
        "workout",
    )

    /**
     * Folds a title or query to its comparable form: case, diacritics,
     * punctuation, and spacing all stop mattering, so "Amelie" matches
     * "Amélie" and "wall-e" matches "WALL·E".
     */
    fun normalizedTitle(value: String): String {
        val folded = COMBINING_MARKS
            .replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
            .lowercase()
        val stripped = buildString(folded.length) {
            for (char in folded) {
                append(if (char.isLetterOrDigit()) char else ' ')
            }
        }
        return stripped.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** True when [titles] holds an exact (normalized) match for [query]. */
    fun containsExactMatch(query: String, titles: List<String>): Boolean {
        val target = normalizedTitle(query)
        if (target.isEmpty()) return false
        return titles.any { normalizedTitle(it) == target }
    }

    /**
     * True when an artist credit matches the query closely enough to count as
     * music intent: exact equality, the artist starts with the query at a word
     * boundary ("bob marley" → "Bob Marley & The Wailers"), or the query sits
     * as whole words inside the credit ("jimi hendrix" → "The Jimi Hendrix
     * Experience"). Deliberately narrower than the backend's fuzzy
     * `artistMatchesQuery` — no typo budget — so soundtrack cover acts can't
     * accidentally suppress a Brokeback-style film lead.
     */
    fun artistNameMatchesQuery(name: String, query: String): Boolean {
        val target = normalizedTitle(query)
        val artist = normalizedTitle(name)
        if (target.isEmpty() || artist.isEmpty()) return false
        if (artist == target) return true
        if (artist.startsWith(target)) {
            if (artist.length == target.length) return true
            return artist[target.length] == ' '
        }
        return (" $artist ").contains(" $target ")
    }

    /** True when any name in [names] matches the query as an artist credit. */
    fun containsArtistMatch(query: String, names: List<String>): Boolean =
        names.any { artistNameMatchesQuery(it, query) }

    /**
     * Compilation / score artist credit rather than a real recording artist.
     * An empty/unknown credit is not treated as soundtrack noise — that would
     * make every row look like an OST when song artists weren't passed in.
     */
    fun isWeakSoundtrackArtist(name: String): Boolean {
        val artist = normalizedTitle(name)
        if (artist.isEmpty()) return false
        return WEAK_SOUNDTRACK_ARTIST_MARKERS.any { artist.contains(it) }
    }

    /** Whether a song row looks like an OST/score pressing for [query]. */
    fun songLooksLikeSoundtrackForQuery(title: String, artist: String, query: String): Boolean {
        if (isWeakSoundtrackArtist(artist)) return true
        val target = normalizedTitle(query)
        val foldedTitle = normalizedTitle(title)
        if (target.isEmpty() || foldedTitle.isEmpty()) return false
        if (foldedTitle.contains("from $target")
            || foldedTitle.contains("score to $target")
            || foldedTitle.contains("theme from $target")
        ) {
            return true
        }
        return hasSoundtrackParenthetical(title)
    }

    /**
     * True when the visible songs are dominated by OST/score pressings for
     * the query — enough to treat exact same-titled rows as catalog noise.
     */
    fun visibleSongsAreSoundtrackHeavy(
        query: String,
        songTitles: List<String>,
        songArtistNames: List<String>,
        previewCount: Int = BLENDED_PREVIEW_COUNT,
    ): Boolean {
        val titles = songTitles.take(previewCount)
        if (titles.isEmpty()) return false
        var soundtrackCount = 0
        for ((index, title) in titles.withIndex()) {
            val artist = songArtistNames.getOrElse(index) { "" }
            if (songLooksLikeSoundtrackForQuery(title, artist, query)) {
                soundtrackCount += 1
            }
        }
        return soundtrackCount >= SOUNDTRACK_HEAVY_THRESHOLD
    }

    /**
     * Whether the film section should lead the blended results.
     *
     * @param query what the user typed.
     * @param songTitles loaded song titles, in rank order.
     * @param filmTitles loaded film titles, in rank order.
     * @param artistNames loaded artist names shown in the music section, in
     *   rank order. Empty on surfaces that render no artist rows (the compose
     *   picker). An artist match ranks first, so the window is generous.
     * @param songArtistNames lead-artist credits for [songTitles], same order.
     *   A visible song by the queried artist counts as a music hit even when
     *   no song is titled the query and no artist rows are shown.
     * @param previewCount how many of each the blended view shows. Only these
     *   are considered — see the type's note on cover-act false matches.
     */
    fun filmsLeadBlendedResults(
        query: String,
        songTitles: List<String>,
        filmTitles: List<String>,
        artistNames: List<String> = emptyList(),
        songArtistNames: List<String> = emptyList(),
        previewCount: Int = BLENDED_PREVIEW_COUNT,
    ): Boolean {
        val visibleFilms = filmTitles.take(previewCount)
        val visibleSongs = songTitles.take(previewCount)
        val visibleArtists = artistNames.take(previewCount)
        val visibleSongArtists = songArtistNames.take(previewCount)
        if (!containsExactMatch(query, visibleFilms)) return false

        // Strong music intent — artist row or songs by the queried artist —
        // always keeps Music first (Hendrix, Vampire Weekend, Rush via Tom Sawyer).
        if (containsArtistMatch(query, visibleArtists)) return false
        if (containsArtistMatch(query, visibleSongArtists)) return false

        // OST/score-dominated previews (Saving Private Ryan, Brokeback) lead
        // with Film even when a couple of rows reuse the film's exact title.
        if (visibleSongsAreSoundtrackHeavy(
                query = query,
                songTitles = visibleSongs,
                songArtistNames = visibleSongArtists,
                previewCount = previewCount,
            )
        ) {
            return true
        }

        // A real same-titled song (Purple Rain, Grease) keeps Music first.
        // Exact titles credited only to weak soundtrack artists do not.
        if (hasExactSongByNonWeakArtist(query, visibleSongs, visibleSongArtists)) {
            return false
        }
        return true
    }

    private fun hasExactSongByNonWeakArtist(
        query: String,
        songTitles: List<String>,
        songArtistNames: List<String>,
    ): Boolean {
        // No artist credits available — keep the older title-only exact match
        // behavior so dual song/film titles still prefer Music.
        if (songArtistNames.isEmpty()) {
            return containsExactMatch(query, songTitles)
        }
        val target = normalizedTitle(query)
        for ((index, title) in songTitles.withIndex()) {
            if (normalizedTitle(title) != target) continue
            val artist = songArtistNames.getOrElse(index) { "" }
            // Unknown credit: don't treat as a strong same-titled song.
            if (normalizedTitle(artist).isEmpty()) continue
            if (!isWeakSoundtrackArtist(artist)) return true
        }
        return false
    }

    private fun hasSoundtrackParenthetical(title: String): Boolean {
        return PARENTHETICAL.findAll(title).any { match ->
            val inner = normalizedTitle(match.groupValues[1])
            SOUNDTRACK_PARENTHETICAL_MARKERS.any { inner.contains(it) }
        }
    }
}
