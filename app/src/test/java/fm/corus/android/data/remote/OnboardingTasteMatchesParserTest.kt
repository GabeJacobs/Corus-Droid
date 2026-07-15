package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure parser tests for the `getOnboardingTasteMatches` response. No
 * FirebaseFunctions mocks (per the project's "Mockito tests fail from CLI"
 * note) — mirrors ParseTasteMatchesGateTest.
 */
class OnboardingTasteMatchesParserTest {

    private fun row(
        id: String = "u1",
        isBot: Boolean = false,
        sharedArtists: Int = 2,
        artistNames: List<String> = listOf("Radiohead", "Björk"),
        directorNames: List<String> = emptyList(),
        previews: List<Map<String, Any?>> = emptyList(),
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "username" to "user-$id",
        "displayName" to "User $id",
        "isBot" to isBot,
        "sharedArtists" to sharedArtists,
        "isStrongMatch" to (sharedArtists >= 3),
        "sharedArtistNames" to artistNames,
        "sharedDirectorNames" to directorNames,
        "sharedTrackPreviews" to previews,
    )

    @Test
    fun `null payload parses to an empty result`() {
        val result = parseOnboardingTasteMatchesResponse(null)
        assertEquals(0, result.users.size)
        assertEquals(0, result.strongCount)
        assertEquals(0, result.totalCount)
    }

    @Test
    fun `counts and users parse from a full payload`() {
        val result = parseOnboardingTasteMatchesResponse(
            mapOf(
                "users" to listOf(row("u1"), row("u2", sharedArtists = 4)),
                "strongCount" to 1,
                "totalCount" to 7,
            ),
        )
        assertEquals(listOf("u1", "u2"), result.users.map { it.user.id })
        assertEquals(1, result.strongCount)
        assertEquals(7, result.totalCount)
    }

    @Test
    fun `matchData is built unconditionally so the shared-names subtitle always renders`() {
        // Unlike parseUserRows (which gates matchData on previews/score), a
        // 0-preview onboarding match must still carry its shared names — the
        // card's "You both love X" line reads them.
        val result = parseOnboardingTasteMatchesResponse(
            mapOf("users" to listOf(row(previews = emptyList()))),
        )
        val match = result.users.single()
        assertNotNull(match.matchData)
        assertEquals(listOf("Radiohead", "Björk"), match.matchData?.sharedArtistNames)
        assertEquals(2, match.matchData?.sharedArtists)
        // hasSimilarityData drives the card's subtitle path.
        assertTrue(match.matchData!!.hasSimilarityData)
    }

    @Test
    fun `preview isMovie derives from poster-without-album-art like web`() {
        val result = parseOnboardingTasteMatchesResponse(
            mapOf(
                "users" to listOf(
                    row(
                        previews = listOf(
                            mapOf("trackName" to "Song", "albumArtURL" to "https://a"),
                            mapOf("trackName" to "Film", "posterURL" to "https://p"),
                            mapOf("trackName" to "Both", "albumArtURL" to "https://a", "posterURL" to "https://p"),
                        ),
                    ),
                ),
            ),
        )
        val previews = result.users.single().matchData!!.sharedTrackPreviews
        assertEquals(listOf(false, true, false), previews.map { it.isMovie })
    }

    @Test
    fun `bots are filtered out of the users list`() {
        val result = parseOnboardingTasteMatchesResponse(
            mapOf(
                "users" to listOf(row("human"), row("bot", isBot = true)),
                "totalCount" to 2,
            ),
        )
        assertEquals(listOf("human"), result.users.map { it.user.id })
        assertFalse(result.users.any { it.user.isBot })
    }

    @Test
    fun `rows without an id are skipped`() {
        val result = parseOnboardingTasteMatchesResponse(
            mapOf("users" to listOf(mapOf("username" to "ghost"), row("real"))),
        )
        assertEquals(listOf("real"), result.users.map { it.user.id })
    }

    @Test
    fun `missing optional fields default safely`() {
        val result = parseOnboardingTasteMatchesResponse(
            mapOf("users" to listOf(mapOf("id" to "bare", "username" to "bare"))),
        )
        val match = result.users.single()
        assertEquals(0, match.matchData?.sharedArtists)
        assertEquals(emptyList<String>(), match.matchData?.sharedArtistNames)
        assertEquals(0, result.strongCount)
    }
}
