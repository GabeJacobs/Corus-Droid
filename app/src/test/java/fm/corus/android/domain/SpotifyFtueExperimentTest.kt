package fm.corus.android.domain

import fm.corus.android.data.model.MusicService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyFtueExperimentTest {

    @Test
    fun `iOS Apple Music new users get Always Full and are ineligible`() {
        val result = SpotifyFtueExperiment.assignment(
            service = MusicService.APPLE_MUSIC,
            spotifyInstalled = true,
            rcVariant = "a",
            supportsAppleFullPlayback = true,
        )
        assertEquals(SpotifyFtueAssignment(SpotifyFtueVariant.INELIGIBLE, true), result)
    }

    @Test
    fun `Android Apple Music new users get previews`() {
        val result = SpotifyFtueExperiment.assignment(
            service = MusicService.APPLE_MUSIC,
            spotifyInstalled = false,
            rcVariant = "b",
        )
        assertEquals(SpotifyFtueAssignment(SpotifyFtueVariant.INELIGIBLE, false), result)
    }

    @Test
    fun `other services get previews and are ineligible`() {
        for (service in listOf(MusicService.TIDAL, MusicService.DEEZER, MusicService.YOUTUBE_MUSIC)) {
            val result = SpotifyFtueExperiment.assignment(
                service = service,
                spotifyInstalled = true,
                rcVariant = "b",
            )
            assertEquals(SpotifyFtueAssignment(SpotifyFtueVariant.INELIGIBLE, false), result)
        }
    }

    @Test
    fun `Spotify without the app is forced into A`() {
        val result = SpotifyFtueExperiment.assignment(
            service = MusicService.SPOTIFY,
            spotifyInstalled = false,
            rcVariant = "b",
        )
        assertEquals(SpotifyFtueAssignment(SpotifyFtueVariant.A, false), result)
    }

    @Test
    fun `RC a is preview-first`() {
        val result = SpotifyFtueExperiment.assignment(
            service = MusicService.SPOTIFY,
            spotifyInstalled = true,
            rcVariant = "a",
        )
        assertEquals(SpotifyFtueAssignment(SpotifyFtueVariant.A, false), result)
    }

    @Test
    fun `RC b is always-full`() {
        val result = SpotifyFtueExperiment.assignment(
            service = MusicService.SPOTIFY,
            spotifyInstalled = true,
            rcVariant = "B",
        )
        assertEquals(SpotifyFtueAssignment(SpotifyFtueVariant.B, true), result)
    }

    @Test
    fun `RC off keeps todays Always Full default`() {
        val result = SpotifyFtueExperiment.assignment(
            service = MusicService.SPOTIFY,
            spotifyInstalled = true,
            rcVariant = "off",
        )
        assertEquals(SpotifyFtueAssignment(SpotifyFtueVariant.OFF, true), result)
    }

    @Test
    fun `B prompts on first play until the chooser is consumed`() {
        assertTrue(SpotifyFtueExperiment.shouldPromptFirstPlay(SpotifyFtueVariant.B, false))
        assertFalse(SpotifyFtueExperiment.shouldPromptFirstPlay(SpotifyFtueVariant.B, true))
        assertFalse(SpotifyFtueExperiment.shouldPromptFirstPlay(SpotifyFtueVariant.A, false))
        assertFalse(SpotifyFtueExperiment.shouldPromptFirstPlay(SpotifyFtueVariant.OFF, false))
        assertFalse(SpotifyFtueExperiment.shouldPromptFirstPlay(null, false))
    }

    @Test
    fun `A prompts when enabling Full B only after choosing 30s`() {
        assertTrue(
            SpotifyFtueExperiment.shouldPromptEnableFull(
                assignedVariant = SpotifyFtueVariant.A,
                linkPromptConsumed = false,
                firstPlayChooserConsumed = false,
            ),
        )
        assertFalse(
            SpotifyFtueExperiment.shouldPromptEnableFull(
                assignedVariant = SpotifyFtueVariant.A,
                linkPromptConsumed = true,
                firstPlayChooserConsumed = false,
            ),
        )
        assertFalse(
            SpotifyFtueExperiment.shouldPromptEnableFull(
                assignedVariant = SpotifyFtueVariant.B,
                linkPromptConsumed = false,
                firstPlayChooserConsumed = false,
            ),
        )
        assertTrue(
            SpotifyFtueExperiment.shouldPromptEnableFull(
                assignedVariant = SpotifyFtueVariant.B,
                linkPromptConsumed = false,
                firstPlayChooserConsumed = true,
            ),
        )
        assertFalse(
            SpotifyFtueExperiment.shouldPromptEnableFull(
                assignedVariant = SpotifyFtueVariant.OFF,
                linkPromptConsumed = false,
                firstPlayChooserConsumed = false,
            ),
        )
        assertFalse(
            SpotifyFtueExperiment.shouldPromptEnableFull(
                assignedVariant = null,
                linkPromptConsumed = false,
                firstPlayChooserConsumed = false,
            ),
        )
    }

    @Test
    fun `Always Full on defers the A link sheet until the next play`() {
        assertTrue(
            SpotifyFtueExperiment.shouldPromptAlwaysFullPlay(
                assignedVariant = SpotifyFtueVariant.A,
                alwaysPlayFullSongs = true,
                linkPromptConsumed = false,
                firstPlayChooserConsumed = false,
            ),
        )
        assertFalse(
            SpotifyFtueExperiment.shouldPromptAlwaysFullPlay(
                assignedVariant = SpotifyFtueVariant.A,
                alwaysPlayFullSongs = false,
                linkPromptConsumed = false,
                firstPlayChooserConsumed = false,
            ),
        )
        assertFalse(
            SpotifyFtueExperiment.shouldPromptAlwaysFullPlay(
                assignedVariant = SpotifyFtueVariant.B,
                alwaysPlayFullSongs = true,
                linkPromptConsumed = false,
                firstPlayChooserConsumed = false,
            ),
        )
        assertFalse(
            SpotifyFtueExperiment.shouldPromptAlwaysFullPlay(
                assignedVariant = SpotifyFtueVariant.A,
                alwaysPlayFullSongs = true,
                linkPromptConsumed = true,
                firstPlayChooserConsumed = false,
            ),
        )
    }
}
