package fm.corus.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterBannedAuthorsTest {

    private data class Item(val id: String, val authorId: String?)

    @Test
    fun `empty banned set returns input unchanged`() {
        val items = listOf(Item("a", "u1"), Item("b", "u2"))
        val result = filterBannedAuthorsPure(items, emptySet()) { it.authorId }
        assertEquals(items, result)
    }

    @Test
    fun `items whose author is banned are dropped`() {
        val items = listOf(Item("a", "u1"), Item("b", "u2"), Item("c", "u3"))
        val result = filterBannedAuthorsPure(items, setOf("u2")) { it.authorId }
        assertEquals(listOf(Item("a", "u1"), Item("c", "u3")), result)
    }

    @Test
    fun `items with null author id are preserved`() {
        val items = listOf(Item("a", null), Item("b", "u2"))
        val result = filterBannedAuthorsPure(items, setOf("u2")) { it.authorId }
        assertEquals(listOf(Item("a", null)), result)
    }

    @Test
    fun `multiple banned ids drop all matching items`() {
        val items = listOf(Item("a", "u1"), Item("b", "u2"), Item("c", "u3"))
        val result = filterBannedAuthorsPure(items, setOf("u1", "u3")) { it.authorId }
        assertEquals(listOf(Item("b", "u2")), result)
    }
}
