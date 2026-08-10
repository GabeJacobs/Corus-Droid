package fm.corus.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the blended-results section order shared by the Search tab and the
 * compose picker. Music leads by default; Film only takes the lead on an exact
 * film-title query that no song title matches exactly. The failure this
 * prevents is a perfect film hit sitting under four soundtrack-cover pressings
 * that merely carry the film's name.
 *
 * Mirrors iOS `UnifiedSearchRankingTests` — keep the two in sync.
 */
class UnifiedSearchRankingTest {

    // ── The case this rule exists for ──

    /**
     * The real Apple catalog order for "brokeback mountain". The visible rows
     * are all soundtrack covers, but ten tracks *further down* are titled
     * exactly "Brokeback Mountain" (cover acts: Groovy 69, Lumberhorn,
     * Rainbow…), the first at position 7. An earlier version of this rule
     * scanned the whole result set, found those, and refused to swap — on the
     * exact query it was written for. Only the preview window counts.
     */
    private val brokebackSongResults = listOf(
        "Ain't Goin' Down On Brokeback Mountain (From \"The Moment of Forever Sessions\")",
        "The Wings (Score to Brokeback Mountain)",
        "Brokeback Mountain (From \"Brokeback Mountain\")",
        "Brokeback Mountain (From \"Brokeback Mountain\")",
        "Brokeback Mountain (Wings) - \"Brokeback Mountain\" (Workout Remix)",
        "Brokeback Mountain (The Groovy Mix - Theme from Brokeback Mountain)",
        "Brokeback Mountain",              // 7: cover act, exact
        "Brokeback Mountain (From the Movie \"Brokeback Mountain\")",
        "Brokeback Mountain",              // 9
        "Brokeback Mountain",              // 10
        "Brokeback Mountain (Workout Remix)",
        "Brokeback Mountain",              // 12
    )

    @Test
    fun `film leads when visible songs are only soundtrack covers`() {
        assertTrue(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "brokeback mountain",
                songTitles = brokebackSongResults,
                filmTitles = listOf(
                    "Brokeback Mountain",
                    "Brokeback Mountain",
                    "Santa & Frosty, A Brokeback Mountain Christmas",
                ),
            )
        )
    }

    @Test
    fun `exact song matches below the fold do not suppress the swap`() {
        // Same data, stated as the property that regressed: the first exact
        // song title sits at position 7, outside the preview window.
        val firstExact = brokebackSongResults.indexOf("Brokeback Mountain")
        assertEquals(6, firstExact)
        assertTrue(firstExact >= UnifiedSearchRanking.BLENDED_PREVIEW_COUNT)
        assertTrue(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "brokeback mountain",
                songTitles = brokebackSongResults,
                filmTitles = listOf("Brokeback Mountain"),
            )
        )
    }

    @Test
    fun `exact film match below the preview window must not lead`() {
        // The film's exact match is past the preview window, so leading with
        // Film would put a section the user can't see on top.
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "up",
                songTitles = listOf("Down", "Sideways", "Over", "Under"),
                filmTitles = listOf("Up in the Air", "Up in Smoke", "Upgrade", "Uptown Girls", "Up"),
            )
        )
    }

    // ── Cases that must NOT flip ──

    /**
     * Titles that are genuinely a famous song AND a film. Real catalog
     * top-fours: the exact song match lands at position 1 to 3 in every one,
     * which is what lets the preview window tell them apart from the Brokeback
     * case. Music must keep the lead.
     */
    @Test
    fun `music keeps the lead when the title is both a song and a film`() {
        fun leads(query: String, songs: List<String>) =
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = query, songTitles = songs, filmTitles = listOf(query),
            )
        assertFalse(leads("purple rain", listOf("Purple Rain", "Purple Rain", "Purple Rain (Live)", "Purple Rain")))
        assertFalse(leads("rush", listOf("Tom Sawyer", "Rush", "Rush", "Rush")))
        assertFalse(leads("grease", listOf("You're the One That I Want", "Summer Nights", "Grease", "Grease (Remix)")))
        assertFalse(leads("frozen", listOf("Let It Go", "Do You Want to Build a Snowman?", "Frozen", "Frozen")))
        assertFalse(leads("dune", listOf("DUNE (feat. Hatsune Miku)", "Dune", "Dune Pt. II", "Dune")))
    }

    // A band whose name is also a film title (Vampire Weekend -> the 2005 short
    // film; The Doors -> the 1991 film). No song is *titled* the query, so the
    // song-title check alone would let the film jump the queue — but the music
    // section leads with an exact artist row, a perfect music hit. The exact
    // artist match must keep music first.
    @Test
    fun `music keeps the lead when the query is an exact artist name`() {
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "vampire weekend",
                songTitles = listOf("A-Punk", "This Life", "Harmony Hall", "Oxford Comma"),
                filmTitles = listOf("Vampire Weekend", "Vampire Weekend: Live from The Artists Den"),
                artistNames = listOf("Vampire Weekend"),
            )
        )
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "the doors",
                songTitles = listOf("Light My Fire", "Riders on the Storm", "Break On Through"),
                filmTitles = listOf("The Doors"),
                artistNames = listOf("The Doors"),
            )
        )
    }

    // Famous artist + same-named biopic (Jimi Hendrix -> the 1973 film). No
    // song is titled the query, and artist pages may be off so artistNames is
    // empty — but the visible tracks are *by* him. Song lead-artist credits
    // must keep music first.
    @Test
    fun `music keeps the lead when visible songs are by the queried artist`() {
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "jimi hendrix",
                songTitles = listOf(
                    "All Along the Watchtower",
                    "Voodoo Child (Slight Return)",
                    "Purple Haze",
                    "Angel",
                ),
                filmTitles = listOf(
                    "Jimi Hendrix",
                    "Jimi Hendrix: Experience",
                    "Jimi Hendrix",
                    "Untitled Jimi Hendrix Film",
                ),
                artistNames = emptyList(),
                songArtistNames = listOf(
                    "The Jimi Hendrix Experience",
                    "Jimi Hendrix",
                    "The Jimi Hendrix Experience",
                    "Jimi Hendrix",
                ),
            )
        )
    }

    // Brokeback must still flip to Film when the visible songs are soundtrack
    // covers by unrelated acts — even once we start reading song artists.
    @Test
    fun `film still leads when visible song artists do not match`() {
        assertTrue(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "brokeback mountain",
                songTitles = brokebackSongResults.take(4),
                filmTitles = listOf("Brokeback Mountain"),
                artistNames = emptyList(),
                songArtistNames = listOf(
                    "Groovy 69",
                    "Lumberhorn",
                    "Rainbow Workout Mix",
                    "Theme Orchestra",
                ),
            )
        )
    }

    // Famous film whose catalog is OST noise — including a couple of rows
    // titled exactly after the movie (Lyric, Various Artists). Soundtrack-
    // heavy previews must still lead with Film.
    @Test
    fun `film leads for soundtrack-heavy exact-title film`() {
        assertTrue(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "saving private ryan",
                songTitles = listOf(
                    "Saving Private Ryan (Hymn to the Fallen)",
                    "Saving Private Ryan (Hymn to the Fallen)",
                    "Saving private Ryan",
                    "Saving Private Ryan",
                ),
                filmTitles = listOf("Saving Private Ryan"),
                artistNames = emptyList(),
                songArtistNames = listOf(
                    "Royal Symphony Orchestra",
                    "Hollywood Movie Soundtrack Orchestra",
                    "Lyric",
                    "Various Artists",
                ),
            )
        )
    }

    // Dual song/film titles still keep Music first when the exact song is by
    // a real artist, not an OST pressing.
    @Test
    fun `music keeps the lead for a dual title with a real song artist`() {
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "purple rain",
                songTitles = listOf("Purple Rain", "Purple Rain", "Purple Rain (Live)", "Purple Rain"),
                filmTitles = listOf("Purple Rain"),
                songArtistNames = listOf("Prince", "Prince", "Prince", "Prince"),
            )
        )
    }

    @Test
    fun `artist name match accepts lead prefix and contained credit`() {
        assertTrue(
            UnifiedSearchRanking.artistNameMatchesQuery("Bob Marley & The Wailers", "bob marley")
        )
        assertTrue(
            UnifiedSearchRanking.artistNameMatchesQuery("The Jimi Hendrix Experience", "jimi hendrix")
        )
        assertFalse(UnifiedSearchRanking.artistNameMatchesQuery("Bobby Womack", "bob"))
        assertFalse(
            UnifiedSearchRanking.artistNameMatchesQuery("Groovy 69", "brokeback mountain")
        )
    }

    // The artist match must be inside the preview window, like the song and film
    // checks — an exact artist buried below the shown rows can't rescue a music
    // section the user can't see leading with it.
    @Test
    fun `an artist match below the preview window does not suppress the swap`() {
        assertTrue(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "coraline",
                songTitles = listOf("Unrelated"),
                filmTitles = listOf("Coraline"),
                // exact match at index 4, outside the 4-row window
                artistNames = listOf("A", "B", "C", "D", "Coraline"),
            )
        )
    }

    @Test
    fun `music keeps the lead without an exact film match`() {
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "alright",
                songTitles = listOf("Alright"),
                filmTitles = listOf("Alright Now"),
            )
        )
    }

    @Test
    fun `a partial title is not a match`() {
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "the god",
                songTitles = emptyList(),
                filmTitles = listOf("The Godfather"),
            )
        )
    }

    @Test
    fun `an empty query never flips`() {
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "   ",
                songTitles = emptyList(),
                filmTitles = listOf(""),
            )
        )
    }

    @Test
    fun `no results never flips`() {
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "brokeback mountain",
                songTitles = emptyList(),
                filmTitles = emptyList(),
            )
        )
    }

    @Test
    fun `film leads when only the soundtrack album matches`() {
        assertTrue(
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = "dune",
                songTitles = listOf("Dune (Original Motion Picture Soundtrack)"),
                filmTitles = listOf("Dune"),
            )
        )
    }

    // ── Normalization ──

    @Test
    fun `a typed query matches a stylized film title`() {
        fun leads(query: String, filmTitle: String) =
            UnifiedSearchRanking.filmsLeadBlendedResults(
                query = query, songTitles = emptyList(), filmTitles = listOf(filmTitle),
            )
        assertTrue(leads("amelie", "Amélie"))                    // diacritics
        assertTrue(leads("wall-e", "WALL·E"))                    // punctuation
        assertTrue(leads("  The   GODFATHER ", "The Godfather")) // case + spacing
    }

    @Test
    fun `normalization folds punctuation and case`() {
        assertEquals("wall e", UnifiedSearchRanking.normalizedTitle("WALL·E"))
        assertEquals("amelie", UnifiedSearchRanking.normalizedTitle("Amélie"))
        assertEquals(
            "brokeback mountain from brokeback mountain",
            UnifiedSearchRanking.normalizedTitle("Brokeback Mountain (From \"Brokeback Mountain\")"),
        )
    }

    @Test
    fun `the preview window is exactly four rows wide`() {
        // The window is load-bearing (see the Brokeback case above), so pin it.
        assertEquals(4, UnifiedSearchRanking.BLENDED_PREVIEW_COUNT)
        val songs = listOf("Other", "Other", "Other", "Other", "Dune")
        // The exact song match at index 4 is outside the window → film leads.
        assertTrue(
            UnifiedSearchRanking.filmsLeadBlendedResults("dune", songs, listOf("Dune"))
        )
        // Widen the window to include it and music keeps the lead.
        assertFalse(
            UnifiedSearchRanking.filmsLeadBlendedResults("dune", songs, listOf("Dune"), previewCount = 5)
        )
    }
}
