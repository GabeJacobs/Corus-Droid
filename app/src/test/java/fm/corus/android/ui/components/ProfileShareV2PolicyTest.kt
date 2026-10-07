package fm.corus.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileShareV2PolicyTest {
    @Test fun `Android offers the same three collage layouts as iOS`() {
        assertEquals(listOf(9, 16, 28), ProfileStoryGridSize.entries.map { it.artworkLimit })
    }

    private fun profile(count: Int?, artworks: Int = 0) = ShareProfileSubject("fixture", "fixture", null, null,
        artworkUrls = List(artworks) { "" }, postCount = count)

    @Test fun `flag and known total decide eligibility before loading artwork`() {
        for (count in listOf(0, 3, 8)) assertFalse(ProfileShareEligibility.usesV2(true, profile(count, 28)))
        assertFalse(ProfileShareEligibility.usesV2(false, profile(100, 28)))
        for (count in listOf(9, 15, 16, 27, 28, 100)) assertTrue(ProfileShareEligibility.usesV2(true, profile(count)))
        assertTrue(ProfileShareEligibility.usesV2(true, profile(null, 9)))
    }

    @Test fun `full defaults synchronously only at 28 and locked layouts stay unavailable`() {
        for (count in listOf(9, 15, 16, 27)) assertEquals(ProfileStoryGridSize.STANDARD, ProfileShareEligibility.defaultLayout(profile(count)))
        assertEquals(ProfileStoryGridSize.FULL, ProfileShareEligibility.defaultLayout(profile(28)))
        assertFalse(ProfileShareEligibility.available(profile(15), ProfileStoryGridSize.LARGE))
        assertTrue(ProfileShareEligibility.available(profile(16), ProfileStoryGridSize.LARGE))
        assertFalse(ProfileShareEligibility.available(profile(27), ProfileStoryGridSize.FULL))
        assertTrue(ProfileShareEligibility.available(profile(28), ProfileStoryGridSize.FULL))
        val movie = profile(28).copy(featuredMoviePosterUrl = "poster")
        assertEquals(ProfileStoryGridSize.STANDARD, ProfileShareEligibility.defaultLayout(movie))
    }

    @Test fun `white is last and rain snow are exclusive with off on second tap`() {
        assertEquals(ProfileStoryBackground.CORUS_BLUE, profileShareBackgroundChoices.first())
        assertEquals(ProfileStoryBackground.LIGHT, profileShareBackgroundChoices.last())
        assertEquals(ProfileStoryBackground.DARK, profileShareBackgroundChoices[5])
        assertEquals(ProfileStoryWeather.SNOW, ProfileStoryWeather.RAIN.toggling(ProfileStoryWeather.SNOW))
        assertEquals(ProfileStoryWeather.NONE, ProfileStoryWeather.SNOW.toggling(ProfileStoryWeather.SNOW))
    }
}
