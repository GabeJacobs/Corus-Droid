package fm.corus.android.data.remote

import com.google.firebase.functions.FirebaseFunctions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class SuggestedUserRowsParserTest {

    private val dataSource = CloudFunctionsDataSource(mock<FirebaseFunctions>(), mock(), mock())

    private fun row(id: String, isBot: Boolean): Map<String, Any?> = mapOf(
        "id" to id,
        "username" to id,
        "displayName" to id,
        "isBot" to isBot,
    )

    @Test
    fun `parseUserRows preserves isBot flag for downstream filtering`() {
        val parsed = dataSource.parseUserRows(
            listOf(row("human-1", false), row("bot-1", true), row("human-2", false)),
        )
        assertEquals(listOf("human-1", "bot-1", "human-2"), parsed.map { it.user.id })
        assertFalse(parsed[0].user.isBot)
        assertTrue(parsed[1].user.isBot)
        assertFalse(parsed[2].user.isBot)
    }

    @Test
    fun `caller-side bot filter removes bots from suggested users`() {
        val parsed = dataSource.parseUserRows(
            listOf(row("human-1", false), row("bot-1", true), row("human-2", false)),
        )
        // Mirrors the `.filter { !it.user.isBot }` applied inside getSuggestedUsers.
        val filtered = parsed.filter { !it.user.isBot }
        assertEquals(listOf("human-1", "human-2"), filtered.map { it.user.id })
    }

    // Parity with iOS fetchSuggestedUserMatches — a user whose only signal is a raw shared
    // track or artist count (no score, previews, adjacent artists, or movie overlap) should
    // have matchData=null so they fall out of the Taste Matches section.
    @Test
    fun `matchData is null when only shared track or artist counts are present`() {
        val rows = listOf(
            mapOf("id" to "u", "username" to "u", "displayName" to "u",
                "sharedPostedTracks" to 3, "sharedLikedTracks" to 2,
                "sharedArtists" to 1, "sharedLikedMovies" to 4),
        )
        val parsed = dataSource.parseUserRows(rows)
        assertEquals(null, parsed.single().matchData)
    }

    @Test
    fun `matchData is built when similarity score is present`() {
        val rows = listOf(
            mapOf("id" to "u", "username" to "u", "displayName" to "u",
                "similarityScore" to 0.42),
        )
        val parsed = dataSource.parseUserRows(rows)
        assertTrue(parsed.single().matchData != null)
        assertTrue(parsed.single().matchData!!.similarityScore > 0)
    }

    @Test
    fun `matchData is built when adjacent artists are present`() {
        val rows = listOf(
            mapOf("id" to "u", "username" to "u", "displayName" to "u",
                "adjacentArtists" to 2),
        )
        val parsed = dataSource.parseUserRows(rows)
        assertTrue(parsed.single().matchData != null)
    }

    @Test
    fun `matchData is built when sharedDirectors are present`() {
        val rows = listOf(
            mapOf("id" to "u", "username" to "u", "displayName" to "u",
                "sharedDirectors" to 1),
        )
        val parsed = dataSource.parseUserRows(rows)
        assertTrue(parsed.single().matchData != null)
    }

    @Test
    fun `matchData is built when sharedPostedMovies are present`() {
        val rows = listOf(
            mapOf("id" to "u", "username" to "u", "displayName" to "u",
                "sharedPostedMovies" to 1),
        )
        val parsed = dataSource.parseUserRows(rows)
        assertTrue(parsed.single().matchData != null)
    }

    // Regression: the row carries authoritative shared artist/director names; without
    // forwarding them the card fell back to count labels ("2 artist matches") instead of
    // listing the actual names like iOS does.
    @Test
    fun `parseUserRows forwards shared artist and director names`() {
        val rows = listOf(
            mapOf("id" to "u", "username" to "u", "displayName" to "u",
                "similarityScore" to 0.42,
                "sharedArtistNames" to listOf("Frank Ocean", "Lana Del Rey"),
                "sharedDirectorNames" to listOf("Denis Villeneuve")),
        )
        val matchData = dataSource.parseUserRows(rows).single().matchData!!
        assertEquals(listOf("Frank Ocean", "Lana Del Rey"), matchData.sharedArtistNames)
        assertEquals(listOf("Denis Villeneuve"), matchData.sharedDirectorNames)
    }

    @Test
    fun `parseUserRows tolerates missing or malformed name arrays`() {
        val rows = listOf(
            mapOf("id" to "u", "username" to "u", "displayName" to "u",
                "similarityScore" to 0.42,
                // Non-string entries must be dropped, not crash the parse.
                "sharedArtistNames" to listOf("Frank Ocean", 42, null)),
        )
        val matchData = dataSource.parseUserRows(rows).single().matchData!!
        assertEquals(listOf("Frank Ocean"), matchData.sharedArtistNames)
        assertEquals(emptyList<String>(), matchData.sharedDirectorNames)
    }
}
