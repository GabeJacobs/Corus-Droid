package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the share-sheet contact ranking: share recipients lead,
 * recent DM partners fill, the follow-graph fallback comes last, duplicates collapse
 * to their highest-priority position, and the grid is capped.
 */
class RankShareContactsTest {

    private fun user(id: String) = CymbalUser(id = id, username = id, displayName = id)

    @Test
    fun `share recipients rank ahead of dm partners and follow fallback`() {
        val result = rankShareContacts(
            shareRecipients = listOf(user("a"), user("b")),
            threadContacts = listOf(user("c")),
            followFallback = listOf(user("d")),
        )
        assertEquals(listOf("a", "b", "c", "d"), result.map { it.id })
    }

    @Test
    fun `duplicates keep their highest-priority position`() {
        // "b" appears as a share recipient AND a DM partner AND a follow — it should
        // surface once, at its share-recipient rank, not be duplicated lower down.
        val result = rankShareContacts(
            shareRecipients = listOf(user("a"), user("b")),
            threadContacts = listOf(user("b"), user("c")),
            followFallback = listOf(user("b"), user("d")),
        )
        assertEquals(listOf("a", "b", "c", "d"), result.map { it.id })
    }

    @Test
    fun `result is capped`() {
        val many = (1..30).map { user("u$it") }
        val result = rankShareContacts(
            shareRecipients = many,
            threadContacts = emptyList(),
            followFallback = emptyList(),
        )
        assertEquals(SHARE_CONTACTS_CAP, result.size)
    }

    @Test
    fun `blank ids are dropped`() {
        val result = rankShareContacts(
            shareRecipients = listOf(user("a"), user("")),
            threadContacts = emptyList(),
            followFallback = emptyList(),
        )
        assertEquals(listOf("a"), result.map { it.id })
    }
}
