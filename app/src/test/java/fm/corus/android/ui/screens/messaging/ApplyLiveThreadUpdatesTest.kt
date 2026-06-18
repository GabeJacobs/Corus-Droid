package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ApplyLiveThreadUpdatesTest {

    private fun user(id: String) = CymbalUser(id = id, username = "user-$id", displayName = "User $id")

    private fun loaded(id: String, text: String = "hi", at: Long = 1_000L) = CymbalThread(
        id = id,
        otherUser = user(id),
        otherUserId = "u-$id",
        lastMessageText = text,
        lastMessageAt = Date(at),
        lastMessageFromUserId = "u-$id",
        unreadCount = 0,
    )

    // A live summary as parsed from the raw thread doc: no otherUser resolved.
    private fun summary(id: String, text: String, at: Long, unread: Int = 1) = CymbalThread(
        id = id,
        otherUser = null,
        otherUserId = "u-$id",
        lastMessageText = text,
        lastMessageAt = Date(at),
        lastMessageFromUserId = "u-$id",
        unreadCount = unread,
    )

    @Test
    fun refreshesPreviewAndUnreadForKnownThreadKeepingProfile() {
        val existing = listOf(loaded("a", "old", at = 1_000))
        val live = listOf(summary("a", "new message", at = 2_000, unread = 1))

        val result = applyLiveThreadUpdates(existing, live)

        val a = result.merged.first { it.id == "a" }
        assertEquals("new message", a.lastMessageText)
        assertEquals(1, a.unreadCount)
        // otherUser must be preserved (live docs don't carry the profile).
        assertEquals("user-a", a.otherUser?.username)
        assertTrue(result.newThreads.isEmpty())
    }

    @Test
    fun newMessageFloatsThreadToTop() {
        val existing = listOf(
            loaded("a", at = 3_000),
            loaded("b", at = 2_000),
            loaded("c", at = 1_000),
        )
        // c gets a brand-new message, newer than everything.
        val live = listOf(summary("c", "ping", at = 9_000))

        val result = applyLiveThreadUpdates(existing, live)

        assertEquals(listOf("c", "a", "b"), result.merged.map { it.id })
    }

    @Test
    fun unknownThreadIsReportedForProfileResolutionNotDropped() {
        val existing = listOf(loaded("a", at = 1_000))
        val live = listOf(summary("zzz", "first message", at = 5_000))

        val result = applyLiveThreadUpdates(existing, live)

        // Not merged in yet (no profile), surfaced for resolution instead.
        assertEquals(listOf("a"), result.merged.map { it.id })
        assertEquals(listOf("zzz"), result.newThreads.map { it.id })
    }

    @Test
    fun threadWithNoMessageYetIsIgnored() {
        val existing = listOf(loaded("a", at = 1_000))
        val live = listOf(
            CymbalThread(
                id = "empty",
                otherUserId = "u-empty",
                lastMessageFromUserId = null, // created but never messaged
                lastMessageAt = Date(9_000),
            )
        )

        val result = applyLiveThreadUpdates(existing, live)

        assertEquals(listOf("a"), result.merged.map { it.id })
        assertTrue(result.newThreads.isEmpty())
    }
}
