package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure parser tests for the `findContactMatches` Cloud Function payload.
 * Heavy mocks against FirebaseFunctions are intentionally avoided per the
 * project's "Mockito tests fail from CLI" note in auto-memory. We verify the
 * id extraction and the defensive defaults for malformed/forked responses.
 */
class ParseContactMatchIdsTest {

    @Test
    fun `null payload yields empty list`() {
        assertTrue(parseContactMatchIds(null).isEmpty())
    }

    @Test
    fun `empty map yields empty list`() {
        assertTrue(parseContactMatchIds(emptyMap()).isEmpty())
    }

    @Test
    fun `missing matches key yields empty list`() {
        assertTrue(parseContactMatchIds(mapOf("other" to 1)).isEmpty())
    }

    @Test
    fun `non-list matches yields empty list`() {
        assertTrue(parseContactMatchIds(mapOf("matches" to "nope")).isEmpty())
    }

    @Test
    fun `extracts ids in server order`() {
        val payload = mapOf(
            "matches" to listOf(
                mapOf("id" to "u1", "username" to "alice", "isBot" to false),
                mapOf("id" to "u2", "username" to "bob", "isBot" to false),
                mapOf("id" to "u3", "username" to "carol", "isBot" to false),
            ),
        )
        assertEquals(listOf("u1", "u2", "u3"), parseContactMatchIds(payload))
    }

    @Test
    fun `drops entries with missing, non-string, or blank id`() {
        val payload = mapOf(
            "matches" to listOf(
                mapOf("id" to "u1"),
                mapOf("username" to "no-id-here"),        // missing id
                mapOf("id" to 42),                        // non-string id
                mapOf("id" to ""),                        // blank id
                mapOf("id" to "  "),                      // blank-ish id
                "not-a-map",                              // wrong entry type
                mapOf("id" to "u2"),
            ),
        )
        assertEquals(listOf("u1", "u2"), parseContactMatchIds(payload))
    }
}
