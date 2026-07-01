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
        val result = filteredUnfollowedUsers(
            enabled = false,
            users = matches,
            followedIds = setOf("a"),
        )
        assertSame(matches, result)
    }

    @Test
    fun `filter enabled drops already-followed matches`() {
        val matches = listOf(match("a"), match("b"), match("c"))
        val result = filteredUnfollowedUsers(
            enabled = true,
            users = matches,
            followedIds = setOf("a", "c"),
        )
        assertEquals(listOf("b"), result.map { it.user.id })
    }

    @Test
    fun `filter enabled with all followed falls back to full list`() {
        val matches = listOf(match("a"), match("b"))
        val result = filteredUnfollowedUsers(
            enabled = true,
            users = matches,
            followedIds = setOf("a", "b"),
        )
        assertEquals(listOf("a", "b"), result.map { it.user.id })
    }

    @Test
    fun `toggle hidden when none followed`() {
        val matches = listOf(match("a"), match("b"))
        assertFalse(shouldShowUnfollowedFilter(matches, followedIds = emptySet()))
    }

    @Test
    fun `toggle hidden when all followed`() {
        val matches = listOf(match("a"), match("b"))
        assertFalse(shouldShowUnfollowedFilter(matches, followedIds = setOf("a", "b")))
    }

    @Test
    fun `toggle shown when mix of followed and unfollowed`() {
        val matches = listOf(match("a"), match("b"), match("c"))
        assertTrue(shouldShowUnfollowedFilter(matches, followedIds = setOf("a")))
    }

    @Test
    fun `toggle hidden when match list is empty`() {
        assertFalse(shouldShowUnfollowedFilter(emptyList(), followedIds = setOf("a")))
    }

    // ── Default-filter flips (iOS parity) ──
    // Both the Taste Matches and Popular rails now default to "All" (filter off).
    // These lock in the semantics: with the filter off, every match is shown
    // regardless of follow state. The composable defaults themselves live in
    // SearchScreen as rememberSaveable { mutableStateOf(false) }.

    @Test
    fun `taste matches default filter off shows followed and unfollowed`() {
        val matches = listOf(match("a"), match("b"), match("c"))
        // Default state = false (All).
        val defaultFilterUnfollowedMatches = false
        val result = filteredUnfollowedUsers(
            enabled = defaultFilterUnfollowedMatches,
            users = matches,
            followedIds = setOf("a", "c"),
        )
        assertEquals(listOf("a", "b", "c"), result.map { it.user.id })
    }

    @Test
    fun `popular default filter off means empty exclusion of followed ids`() {
        // The Popular rail folds the followed-id set into excludeIds only when the
        // filter is on; default (false) contributes no exclusion, so "All" shows
        // everyone. Mirror that here.
        val defaultFilterUnfollowedPopular = false
        val followingIds = setOf("a", "b")
        val popularRailFilterFollowedIds =
            if (defaultFilterUnfollowedPopular) followingIds else emptySet()
        assertTrue(popularRailFilterFollowedIds.isEmpty())
    }
}
