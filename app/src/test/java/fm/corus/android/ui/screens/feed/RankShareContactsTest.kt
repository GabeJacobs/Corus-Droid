package fm.corus.android.ui.screens.feed

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Share-sheet recents are most-recent 1:1 DM partners in inbox order.
 * Groups drop out, duplicates collapse, and the grid is capped.
 */
class RankShareContactsTest {

    private fun user(id: String) = CymbalUser(id = id, username = id, displayName = id)

    private fun thread(id: String, other: CymbalUser? = null, isGroup: Boolean = false) =
        CymbalThread(id = id, otherUser = other, otherUserId = other?.id ?: "", isGroup = isGroup)

    @Test
    fun `contacts stay in inbox order`() {
        val result = recentDmShareContacts(
            listOf(thread("t1", user("c")), thread("t2", user("a")), thread("t3", user("b"))),
        )
        assertEquals(listOf("c", "a", "b"), result.map { it.id })
    }

    @Test
    fun `group threads are skipped`() {
        val result = recentDmShareContacts(
            listOf(
                thread("g1", isGroup = true),
                thread("t1", user("a")),
                thread("g2", user("skip"), isGroup = true),
            ),
        )
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `duplicates keep their first inbox position`() {
        val result = recentDmShareContacts(
            listOf(thread("t1", user("a")), thread("t2", user("b")), thread("t3", user("a"))),
        )
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `result is capped`() {
        val many = (1..30).map { thread("t$it", user("u$it")) }
        val result = recentDmShareContacts(many)
        assertEquals(SHARE_CONTACTS_CAP, result.size)
        assertEquals("u1", result.first().id)
    }

    @Test
    fun `blank ids and missing otherUser are dropped`() {
        val result = recentDmShareContacts(
            listOf(
                thread("t1", user("a")),
                thread("t2", user("")),
                thread("t3"),
            ),
        )
        assertEquals(listOf("a"), result.map { it.id })
    }
}
