package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalMessage
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageHistoryPagingTest {
    @Test
    fun `merge deduplicates overlap prefers live copy and remains newest first`() {
        val old = message("old", 1)
        val staleOverlap = message("overlap", 2, "stale")
        val liveOverlap = message("overlap", 2, "live")
        val newest = message("new", 3)

        val merged = mergeMessagePages(
            preferred = listOf(newest, liveOverlap),
            fallback = listOf(staleOverlap, old),
        )

        assertEquals(listOf("new", "overlap", "old"), merged.map { it.id })
        assertEquals("live", merged.first { it.id == "overlap" }.text)
    }

    @Test
    fun `empty page leaves current live window unchanged`() {
        val live = listOf(message("new", 3), message("old", 1))
        assertEquals(live, mergeMessagePages(live, emptyList()))
    }

    @Test
    fun `live snapshot infers hasMore only before older pages exist`() {
        assertEquals(
            true,
            hasMoreAfterLiveSnapshot(
                previous = true,
                liveWindowCount = 40,
                hasRetainedOlder = false,
                pageSize = 40,
            ),
        )
        assertEquals(
            false,
            hasMoreAfterLiveSnapshot(
                previous = false,
                liveWindowCount = 40,
                hasRetainedOlder = true,
                pageSize = 40,
            ),
        )
        assertEquals(
            false,
            hasMoreAfterLiveSnapshot(
                previous = true,
                liveWindowCount = 1,
                hasRetainedOlder = false,
                pageSize = 40,
            ),
        )
    }

    private fun message(id: String, time: Long, text: String = id) = CymbalMessage(
        id = id,
        threadId = "thread",
        fromUserId = "user",
        text = text,
        createdAt = Date(time),
    )
}
