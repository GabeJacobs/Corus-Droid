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
 * The rule is deliberately narrow: film leads only when a film title matches
 * the query exactly and neither a song title nor an artist name does. A title
 * that exists as both a song and a film (Purple Rain, Dune, Rush) keeps music
 * first, because the song is an exact hit too and the query can't
 * disambiguate. So does a band whose name is also a film title (Vampire
 * Weekend, The Doors): the blended music section leads with that exact artist
 * row, which is a perfect music hit even when no song is titled the query —
 * checking only song titles let the same-named film jump the exact artist.
 *
 * Crucially, both checks look only at the rows the blended view actually
 * shows, not the whole loaded result set. Cover and tribute acts publish
 * tracks titled exactly after famous films, so the full set nearly always
 * contains a false "exact" song match: the catalog returns ten tracks named
 * exactly "Brokeback Mountain" (Groovy 69, Lumberhorn, Rainbow…), the first
 * at position 7. Scanning everything therefore suppressed the swap on the
 * very query it was written for. Titles that are genuinely a famous song put
 * their exact match at the top instead (Purple Rain 1, Rush 2, Grease 3,
 * Frozen 3, Dune 2), so the preview window separates the two cases where the
 * full list can't.
 *
 * Kotlin port of iOS `UnifiedSearchRanking` — keep the two in sync.
 */
object UnifiedSearchRanking {

    /**
     * Rows each vertical shows in the blended view before "See All". Both
     * surfaces cap at 4.
     */
    const val BLENDED_PREVIEW_COUNT = 4

    /** Unicode combining marks, stripped after NFD decomposition. */
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

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
     * Whether the film section should lead the blended results.
     *
     * @param query what the user typed.
     * @param songTitles loaded song titles, in rank order.
     * @param filmTitles loaded film titles, in rank order.
     * @param artistNames loaded artist names shown in the music section, in
     *   rank order. Empty on surfaces that render no artist rows (the compose
     *   picker). An exact artist match ranks first, so the window is generous.
     * @param previewCount how many of each the blended view shows. Only these
     *   are considered — see the type's note on cover-act false matches.
     */
    fun filmsLeadBlendedResults(
        query: String,
        songTitles: List<String>,
        filmTitles: List<String>,
        artistNames: List<String> = emptyList(),
        previewCount: Int = BLENDED_PREVIEW_COUNT,
    ): Boolean {
        val visibleFilms = filmTitles.take(previewCount)
        val visibleSongs = songTitles.take(previewCount)
        val visibleArtists = artistNames.take(previewCount)
        if (!containsExactMatch(query, visibleFilms)) return false
        // Music leads if it has an exact hit of its own — a song title OR an
        // artist name (a band named exactly after the film).
        if (containsExactMatch(query, visibleSongs)) return false
        return !containsExactMatch(query, visibleArtists)
    }
}
