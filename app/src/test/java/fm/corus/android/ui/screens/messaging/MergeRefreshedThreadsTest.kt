package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.CymbalThread
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeRefreshedThreadsTest {

    private fun thread(id: String, last: String = "hey") = CymbalThread(
        id = id,
        otherUserId = "u-$id",
        lastMessageText = last,
        lastMessageFromUserId = "u-$id",
    )

    @Test
    fun keepsPaginatedTailBelowRefreshedPage() {
        val existing = listOf(thread("a"), thread("b"), thread("c"), thread("d"))
        val refreshed = listOf(thread("a"), thread("b"))

        val merged = mergeRefreshedThreads(existing, refreshed)

        assertEquals(listOf("a", "b", "c", "d"), merged.map { it.id })
    }

    @Test
    fun newThreadLandsOnTopWithoutDroppingTail() {
        val existing = listOf(thread("a"), thread("b"), thread("c"))
        val refreshed = listOf(thread("new"), thread("a"), thread("b"))

        val merged = mergeRefreshedThreads(existing, refreshed)

        assertEquals(listOf("new", "a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun bumpedTailThreadMovesUpWithoutDuplicating() {
        // "c" was below the fold and got a new message, so it's now in the
        // refreshed newest page.
        val existing = listOf(thread("a"), thread("b"), thread("c"), thread("d"))
        val refreshed = listOf(thread("c", last = "new message"), thread("a"), thread("b"))

        val merged = mergeRefreshedThreads(existing, refreshed)

        assertEquals(listOf("c", "a", "b", "d"), merged.map { it.id })
        assertEquals("new message", merged.first { it.id == "c" }.lastMessageText)
    }

    @Test
    fun refreshedDataReplacesStaleCopies() {
        val existing = listOf(thread("a", last = "old preview"), thread("b"))
        val refreshed = listOf(thread("a", last = "fresh preview"), thread("b"))

        val merged = mergeRefreshedThreads(existing, refreshed)

        assertEquals("fresh preview", merged.first { it.id == "a" }.lastMessageText)
    }

    @Test
    fun emptyExistingAdoptsRefreshedPage() {
        val refreshed = listOf(thread("a"), thread("b"))

        assertEquals(refreshed, mergeRefreshedThreads(emptyList(), refreshed))
    }
}
