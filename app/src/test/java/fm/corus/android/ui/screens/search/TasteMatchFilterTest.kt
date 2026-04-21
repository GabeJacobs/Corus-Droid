package fm.corus.android.ui.screens.search

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteMatchFilterTest {

    private fun match(id: String): SuggestedUserMatch =
        SuggestedUserMatch(
            user = CymbalUser(id = id, username = id, displayName = id),
            matchData = null,
            suggestionReason = null,
        )

    @Test
    fun `filter disabled returns original list unchanged`() {
        val matches = listOf(match("a"), match("b"), match("c"))
        val result = filteredMusicMatchUsers(
            enabled = false,
            musicMatchUsers = matches,
            followedIds = setOf("a"),
        )
        assertSame(matches, result)
    }

    @Test
    fun `filter enabled drops already-followed matches`() {
        val matches = listOf(match("a"), match("b"), match("c"))
        val result = filteredMusicMatchUsers(
            enabled = true,
            musicMatchUsers = matches,
            followedIds = setOf("a", "c"),
        )
        assertEquals(listOf("b"), result.map { it.user.id })
    }

    @Test
    fun `filter enabled with all followed falls back to full list`() {
        val matches = listOf(match("a"), match("b"))
        val result = filteredMusicMatchUsers(
            enabled = true,
            musicMatchUsers = matches,
            followedIds = setOf("a", "b"),
        )
        assertEquals(listOf("a", "b"), result.map { it.user.id })
    }

    @Test
    fun `toggle hidden when none followed`() {
        val matches = listOf(match("a"), match("b"))
        assertFalse(showUnfollowedMatchesToggle(matches, followedIds = emptySet()))
    }

    @Test
    fun `toggle hidden when all followed`() {
        val matches = listOf(match("a"), match("b"))
        assertFalse(showUnfollowedMatchesToggle(matches, followedIds = setOf("a", "b")))
    }

    @Test
    fun `toggle shown when mix of followed and unfollowed`() {
        val matches = listOf(match("a"), match("b"), match("c"))
        assertTrue(showUnfollowedMatchesToggle(matches, followedIds = setOf("a")))
    }

    @Test
    fun `toggle hidden when match list is empty`() {
        assertFalse(showUnfollowedMatchesToggle(emptyList(), followedIds = setOf("a")))
    }
}
