package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ApplyLiveThreadUpdatesTest {

    // Matches the inbox listener's first-page limit. The snapshot is treated as
    // authoritative (and prunes vanished threads) when it returns fewer than this.
    private val pageSize = 30

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
    private fun summary(id: String, text: String = "hi", at: Long = 1_000L, unread: Int = 1) = CymbalThread(
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

        val result = applyLiveThreadUpdates(existing, live, pageSize)

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
        // Full snapshot (what Firestore actually delivers): every thread, with c
        // floated to the top by a brand-new message.
        val live = listOf(
            summary("c", "ping", at = 9_000),
            summary("a", at = 3_000),
            summary("b", at = 2_000),
        )

        val result = applyLiveThreadUpdates(existing, live, pageSize)

        assertEquals(listOf("c", "a", "b"), result.merged.map { it.id })
    }

    @Test
    fun staleCacheSnapshotDoesNotRegressFreshThreadOrReorder() {
        // The callable loaded fresh order: "c" is on top after a message sent on
        // another device bumped it to t=9000. The inbox listener's first emission
        // is Firestore's offline-cache replay, which predates that message and
        // still shows c at its old t=1000. It must NOT clobber c's preview or drop
        // it from the top (the cold-open "non-updated list" bug).
        val existing = listOf(
            loaded("c", "fresh ping", at = 9_000),
            loaded("a", at = 3_000),
            loaded("b", at = 2_000),
        )
        val live = listOf(
            summary("c", "stale old text", at = 1_000),
            summary("a", at = 3_000),
            summary("b", at = 2_000),
        )

        val result = applyLiveThreadUpdates(existing, live, pageSize)

        assertEquals(listOf("c", "a", "b"), result.merged.map { it.id })
        val c = result.merged.first { it.id == "c" }
        assertEquals("fresh ping", c.lastMessageText)
        assertEquals(9_000L, c.lastMessageAt.time)
    }

    @Test
    fun unknownThreadIsReportedForProfileResolutionNotDropped() {
        val existing = listOf(loaded("a", at = 1_000))
        // Full snapshot: the new thread plus the already-known one.
        val live = listOf(
            summary("zzz", "first message", at = 5_000),
            summary("a", at = 1_000),
        )

        val result = applyLiveThreadUpdates(existing, live, pageSize)

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
            ),
            summary("a", at = 1_000),
        )

        val result = applyLiveThreadUpdates(existing, live, pageSize)

        assertEquals(listOf("a"), result.merged.map { it.id })
        assertTrue(result.newThreads.isEmpty())
    }

    @Test
    fun threadAbsentFromCompleteSnapshotIsPruned() {
        // You left "b" on another device, or its creator removed you: its mirror
        // doc is gone, so the (complete) snapshot no longer lists it.
        val existing = listOf(loaded("a", at = 2_000), loaded("b", at = 1_000))
        val live = listOf(summary("a", at = 2_000))

        val result = applyLiveThreadUpdates(existing, live, pageSize)

        assertEquals(listOf("a"), result.merged.map { it.id })
        assertFalse(result.merged.any { it.id == "b" })
    }

    @Test
    fun pruneRespectsTheWindowWhenSnapshotIsFull() {
        // A full window (live.size == pageSize) is authoritative only for its own
        // time range: a thread inside the window that vanished is pruned, but an
        // older paginated thread below the window is kept.
        val existing = listOf(
            loaded("a", at = 3_000),
            loaded("vanished", at = 2_500),
            loaded("old", at = 500),
        )
        val live = listOf(summary("a", at = 3_000), summary("b", at = 2_000))

        val result = applyLiveThreadUpdates(existing, live, pageSize = 2)

        assertEquals(listOf("a", "old"), result.merged.map { it.id })
        assertEquals(listOf("b"), result.newThreads.map { it.id })
    }

    @Test
    fun groupRenameAndPhotoAndMembershipReflectOnKnownRow() {
        val existing = listOf(
            CymbalThread(
                id = "g",
                otherUserId = "",
                lastMessageText = "hey",
                lastMessageAt = Date(1_000),
                lastMessageFromUserId = "u-x",
                isGroup = true,
                groupName = "Old name",
                groupPhotoURL = "https://img/old.jpg",
                memberIds = listOf("u-x", "u-y"),
                members = listOf(user("x"), user("y")),
                createdBy = "u-x",
            )
        )
        // Renamed + new photo + a member added, delivered by the live mirror doc
        // (which carries no resolved member profiles).
        val live = listOf(
            CymbalThread(
                id = "g",
                otherUserId = "",
                lastMessageText = "renamed",
                lastMessageAt = Date(2_000),
                lastMessageFromUserId = "u-x",
                isGroup = true,
                groupName = "New name",
                groupPhotoURL = "https://img/new.jpg",
                memberIds = listOf("u-x", "u-y", "u-z"),
                createdBy = "u-x",
            )
        )

        val g = applyLiveThreadUpdates(existing, live, pageSize).merged.first { it.id == "g" }

        assertEquals("New name", g.groupName)
        assertEquals("https://img/new.jpg", g.groupPhotoURL)
        assertEquals(listOf("u-x", "u-y", "u-z"), g.memberIds)
        // Resolved profiles are preserved (the mirror doc can't carry them).
        assertEquals(2, g.members.size)
    }
}
