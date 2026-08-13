package fm.corus.android.ui.screens.notifications

import fm.corus.android.data.model.CymbalNotification
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.NotificationType
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the Activity-feed merge ([mergedNotificationList]).
 *
 * The 07-14 bug: the Activity tab stays composed, so the list survives
 * overnight in memory while the process is frozen. On resume the limit-15
 * listener re-emits only the newest window. When more than a window's worth
 * arrived overnight, the window shares nothing with the stale list; the old
 * merge appended the stale tail after the fresh head, silently hiding every
 * notification in between (a subscribed new_post among them) — and load-more
 * paginated from the stale tail's bottom, so the hole never backfilled.
 * These tests pin the gap guard and the pre-existing merge semantics.
 */
class NotificationListMergeTest {

    /** Notifications are ordered newest-first; timestamps make that explicit. */
    private fun notif(id: String, ageMs: Long) = CymbalNotification(
        id = id,
        type = NotificationType.LIKE,
        fromUser = CymbalUser(id = "sender-$id", username = "u", displayName = "U"),
        timestamp = Date(1_000_000_000L - ageMs),
    )

    @Test
    fun `initial load takes incoming as-is`() {
        val incoming = listOf(notif("a", ageMs = 10), notif("b", ageMs = 20))
        val result = mergedNotificationList(current = emptyList(), incoming = incoming)
        assertEquals(listOf("a", "b"), result.list.map { it.id })
        assertFalse(result.droppedStaleTail)
    }

    @Test
    fun `overlapping window keeps older paginated tail`() {
        val current = listOf(
            notif("n1", ageMs = 60), notif("n2", ageMs = 120),
            notif("old1", ageMs = 9_000), notif("old2", ageMs = 9_500),
        )
        // One brand-new arrival; the window still overlaps the on-screen list.
        val incoming = listOf(notif("n0", ageMs = 5), notif("n1", ageMs = 60), notif("n2", ageMs = 120))
        val result = mergedNotificationList(current = current, incoming = incoming)
        assertEquals(listOf("n0", "n1", "n2", "old1", "old2"), result.list.map { it.id })
        assertFalse(result.droppedStaleTail)
    }

    /**
     * THE regression (07-14): overnight 45 arrived; on resume the listener
     * emits one coalesced snapshot of the newest 15 — zero overlap with the
     * stale in-memory list. Keeping the stale tail would render the fresh
     * window directly above hours-older rows, silently hiding the 30
     * notifications between them. The stale tail must be dropped so
     * pagination refetches from the fresh window down.
     */
    @Test
    fun `non-overlapping fresh window replaces the stale list instead of hiding a gap`() {
        val stale = (1..15).map { notif("old$it", ageMs = 30_000_000L + it) }
        val fresh = (1..15).map { notif("new$it", ageMs = it.toLong()) }
        val result = mergedNotificationList(current = stale, incoming = fresh)
        assertEquals(fresh.map { it.id }, result.list.map { it.id })
        assertTrue(result.droppedStaleTail)
    }

    /** A single shared id means the window is contiguous with the list — the
     * older items are genuinely adjacent and must be kept. */
    @Test
    fun `single overlap item keeps the tail`() {
        val current = listOf(notif("n1", ageMs = 60), notif("old1", ageMs = 9_000))
        val incoming = (0 until 14).map { notif("new$it", ageMs = it.toLong()) } + notif("n1", ageMs = 60)
        val result = mergedNotificationList(current = current, incoming = incoming)
        assertEquals("old1", result.list.last().id)
        assertEquals(16, result.list.size)
        assertFalse(result.droppedStaleTail)
    }

    /** An empty emission (e.g. all notifications deleted server-side while the
     * window is empty of new data) must not wipe the on-screen list. */
    @Test
    fun `empty incoming is a no-op`() {
        val current = listOf(notif("n1", ageMs = 60), notif("n2", ageMs = 120))
        val result = mergedNotificationList(current = current, incoming = emptyList())
        assertEquals(listOf("n1", "n2"), result.list.map { it.id })
        assertFalse(result.droppedStaleTail)
    }

    @Test
    fun `head reorders to server order`() {
        val current = listOf(notif("n2", ageMs = 120), notif("n1", ageMs = 60), notif("old1", ageMs = 9_000))
        val incoming = listOf(notif("n1", ageMs = 60), notif("n2", ageMs = 120))
        val result = mergedNotificationList(current = current, incoming = incoming)
        assertEquals(listOf("n1", "n2", "old1"), result.list.map { it.id })
        assertFalse(result.droppedStaleTail)
    }

    @Test
    fun `brand new ids are those in incoming but not already on screen`() {
        val incoming = listOf(notif("n0", ageMs = 5), notif("n1", ageMs = 60))
        assertEquals(setOf("n0"), brandNewNotificationIds(currentIds = setOf("n1", "n2"), incoming = incoming))
    }

    @Test
    fun `first window is not treated as brand new so lastSeen can decide highlighting`() {
        val incoming = listOf(notif("n1", ageMs = 10), notif("n2", ageMs = 20))
        assertEquals(emptySet<String>(), brandNewNotificationIds(currentIds = emptySet(), incoming = incoming))
    }

    @Test
    fun `pin to new head only when the user was still looking at the old head`() {
        assertTrue(shouldPinActivityToNewHead(previousHeadId = "old", newHeadId = "new", firstVisibleItemKey = "old"))
        assertFalse(shouldPinActivityToNewHead(previousHeadId = "old", newHeadId = "new", firstVisibleItemKey = "other"))
        assertFalse(shouldPinActivityToNewHead(previousHeadId = null, newHeadId = "new", firstVisibleItemKey = "new"))
        assertFalse(shouldPinActivityToNewHead(previousHeadId = "same", newHeadId = "same", firstVisibleItemKey = "same"))
        assertFalse(shouldPinActivityToNewHead(previousHeadId = "old", newHeadId = null, firstVisibleItemKey = "old"))
    }
}
