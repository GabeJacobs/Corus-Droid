package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.MessageRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Whether a conversation may be opened at all. The backend refuses to hand one
 * over when the caller has no row for it, when that row says the caller blocked
 * the correspondent, or when the correspondent is banned; this is that same
 * refusal, answered on the device so no way in can get around it.
 */
class ThreadAccessTest {

    private val nobodyBanned: (String) -> Boolean = { false }

    private fun direct(blocked: Boolean = false, otherUserId: String = "peer") =
        CymbalThread(id = "dm1", otherUserId = otherUserId, blocked = blocked)

    private fun row(thread: CymbalThread?, fromCache: Boolean = false) =
        MessageRepository.ThreadRowSnapshot(thread = thread, fromCache = fromCache)

    @Test
    fun `nothing is decided before the row has answered, so a deep link cannot flash a conversation it may have to take away`() {
        assertEquals(ThreadAccess.RESOLVING, resolveThreadAccess(null, emptySet(), nobodyBanned))
    }

    @Test
    fun `an ordinary conversation opens`() {
        assertEquals(ThreadAccess.OPEN, resolveThreadAccess(row(direct()), emptySet(), nobodyBanned))
    }

    @Test
    fun `a conversation the caller blocked is refused`() {
        assertEquals(
            ThreadAccess.UNAVAILABLE,
            resolveThreadAccess(row(direct(blocked = true)), emptySet(), nobodyBanned),
        )
    }

    @Test
    fun `blocking on this device refuses it without waiting for the row to say so`() {
        assertEquals(
            ThreadAccess.UNAVAILABLE,
            resolveThreadAccess(row(direct(otherUserId = "nemesis")), setOf("nemesis"), nobodyBanned),
        )
    }

    @Test
    fun `a conversation with a banned account is refused`() {
        assertEquals(
            ThreadAccess.UNAVAILABLE,
            resolveThreadAccess(row(direct(otherUserId = "spammer")), emptySet()) { it == "spammer" },
        )
    }

    @Test
    fun `a blocked conversation is refused on the local cache's word alone, because guessing wrong shows the correspondent`() {
        assertEquals(
            ThreadAccess.UNAVAILABLE,
            resolveThreadAccess(row(direct(blocked = true), fromCache = true), emptySet(), nobodyBanned),
        )
    }

    @Test
    fun `lifting the block opens it again`() {
        assertEquals(
            ThreadAccess.OPEN,
            resolveThreadAccess(row(direct(blocked = false)), emptySet(), nobodyBanned),
        )
    }

    @Test
    fun `a group survives a member the caller cannot see`() {
        val group = CymbalThread(
            id = "g1",
            isGroup = true,
            blocked = true,
            otherUserId = "nemesis",
            memberIds = listOf("me", "nemesis", "friend"),
        )

        assertEquals(
            ThreadAccess.OPEN,
            resolveThreadAccess(row(group), setOf("nemesis")) { it == "nemesis" },
        )
    }

    @Test
    fun `a conversation the caller has no row for is refused`() {
        assertEquals(
            ThreadAccess.UNAVAILABLE,
            resolveThreadAccess(row(null), emptySet(), nobodyBanned),
        )
    }

    @Test
    fun `a row the device has simply never held is not an answer, so a cold deep link keeps waiting`() {
        assertEquals(
            ThreadAccess.RESOLVING,
            resolveThreadAccess(row(null, fromCache = true), emptySet(), nobodyBanned),
        )
    }

    @Test
    fun `ordinary unread counts toward the badge`() {
        assertEquals(
            2,
            unreadContribution("a", 2, isGroup = false, otherUserId = "ada", blocked = false, activeThreadId = null) { false },
        )
    }

    @Test
    fun `a hidden or blocked peer does not leave a stuck badge`() {
        assertEquals(
            0,
            unreadContribution("gone", 1, isGroup = false, otherUserId = "spammer", blocked = false, activeThreadId = null) { it == "spammer" },
        )
        assertEquals(
            0,
            unreadContribution("blocked", 3, isGroup = false, otherUserId = "nemesis", blocked = true, activeThreadId = null) { false },
        )
    }

    @Test
    fun `the open thread and groups still behave`() {
        assertEquals(
            0,
            unreadContribution("open", 4, isGroup = false, otherUserId = "ada", blocked = false, activeThreadId = "open") { false },
        )
        assertEquals(
            2,
            unreadContribution("g", 2, isGroup = true, otherUserId = "", blocked = false, activeThreadId = null) { it == "spammer" },
        )
    }

    @Test
    fun `a direct row without a resolvable peer is not drawn in the inbox`() {
        val missing = CymbalThread(id = "gone", otherUserId = "deleted", otherUser = null)
        val blankName = CymbalThread(
            id = "stub",
            otherUserId = "stub",
            otherUser = CymbalUser(id = "stub", username = "", displayName = ""),
        )
        val ok = CymbalThread(
            id = "ok",
            otherUserId = "ada",
            otherUser = CymbalUser(id = "ada", username = "ada", displayName = "Ada"),
        )
        val group = CymbalThread(id = "g", isGroup = true, memberIds = listOf("me", "ada"))

        assertEquals(
            listOf("ok", "g"),
            visibleInboxRows(listOf(missing, blankName, ok, group), emptySet(), nobodyBanned).map { it.id },
        )
    }
}
