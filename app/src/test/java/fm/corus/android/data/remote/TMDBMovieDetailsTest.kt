package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TMDBMovieDetailsTest {

    private fun details(
        runtime: Int? = null,
        genres: List<TMDBGenre> = emptyList(),
        productionCountries: List<TMDBCountry> = emptyList(),
        originalLanguage: String = "",
        status: String = "",
        imdbId: String? = null,
        homepage: String? = null,
        cast: List<TMDBCastMember> = emptyList(),
        crew: List<TMDBCrewMember> = emptyList(),
    ) = TMDBMovieDetails(
        id = 1,
        title = "Test Movie",
        runtime = runtime,
        genres = genres,
        productionCountries = productionCountries,
        originalLanguage = originalLanguage,
        status = status,
        imdbId = imdbId,
        homepage = homepage,
        credits = TMDBCredits(cast = cast, crew = crew),
    )

    // ── runtimeText ──

    @Test
    fun `runtimeText formats hours and minutes`() {
        assertEquals("2h 28m", details(runtime = 148).runtimeText)
    }

    @Test
    fun `runtimeText formats minutes only when under an hour`() {
        assertEquals("45m", details(runtime = 45).runtimeText)
    }

    @Test
    fun `runtimeText returns null when runtime is null`() {
        assertNull(details(runtime = null).runtimeText)
    }

    @Test
    fun `runtimeText returns null when runtime is zero`() {
        assertNull(details(runtime = 0).runtimeText)
    }

    // ── genreNames ──

    @Test
    fun `genreNames extracts names`() {
        val genres = listOf(
            TMDBGenre(1, "Western"),
            TMDBGenre(2, "Comedy"),
            TMDBGenre(3, "Crime"),
        )
        assertEquals(listOf("Western", "Comedy", "Crime"), details(genres = genres).genreNames)
    }

    @Test
    fun `genreNames returns empty for no genres`() {
        assertEquals(emptyList<String>(), details().genreNames)
    }

    // ── countryName ──

    @Test
    fun `countryName returns first country`() {
        val countries = listOf(
            TMDBCountry("US", "United States of America"),
            TMDBCountry("GB", "United Kingdom"),
        )
        assertEquals("United States of America", details(productionCountries = countries).countryName)
    }

    @Test
    fun `countryName returns null when empty`() {
        assertNull(details().countryName)
    }

    // ── languageDisplayName ──

    @Test
    fun `languageDisplayName converts iso code`() {
        assertEquals("English", details(originalLanguage = "en").languageDisplayName)
    }

    @Test
    fun `languageDisplayName returns null for blank`() {
        assertNull(details(originalLanguage = "").languageDisplayName)
    }

    // ── castWithCharacters ──

    @Test
    fun `castWithCharacters takes top 5`() {
        val cast = (1..8).map { TMDBCastMember(it, "Actor $it", "Char $it") }
        val result = details(cast = cast).castWithCharacters
        assertEquals(5, result.size)
        assertEquals("Actor 1", result.first().name)
        assertEquals("Char 1", result.first().character)
    }

    @Test
    fun `castWithCharacters returns empty when no credits`() {
        assertEquals(emptyList<TMDBCastEntry>(), details().castWithCharacters)
    }

    // ── writers ──

    @Test
    fun `writers filters by screenplay and writer jobs`() {
        val crew = listOf(
            TMDBCrewMember(1, "Ari Aster", "Director"),
            TMDBCrewMember(2, "Ari Aster", "Screenplay"),
            TMDBCrewMember(3, "Jane Doe", "Writer"),
            TMDBCrewMember(4, "John Smith", "Producer"),
            TMDBCrewMember(5, "Bob Jones", "Story"),
        )
        val result = details(crew = crew).writers
        assertEquals(listOf("Ari Aster", "Jane Doe", "Bob Jones"), result)
    }

    @Test
    fun `writers deduplicates names`() {
        val crew = listOf(
            TMDBCrewMember(1, "Ari Aster", "Screenplay"),
            TMDBCrewMember(2, "Ari Aster", "Story"),
        )
        assertEquals(listOf("Ari Aster"), details(crew = crew).writers)
    }

    // ── imdbURL ──

    @Test
    fun `imdbURL builds url from id`() {
        assertEquals(
            "https://www.imdb.com/title/tt1234567",
            details(imdbId = "tt1234567").imdbURL,
        )
    }

    @Test
    fun `imdbURL returns null when no id`() {
        assertNull(details(imdbId = null).imdbURL)
    }

    // ── homepageURL ──

    @Test
    fun `homepageURL returns url when present`() {
        assertEquals("https://example.com", details(homepage = "https://example.com").homepageURL)
    }

    @Test
    fun `homepageURL returns null for blank`() {
        assertNull(details(homepage = "").homepageURL)
    }

    @Test
    fun `homepageURL returns null for null`() {
        assertNull(details(homepage = null).homepageURL)
    }
}
