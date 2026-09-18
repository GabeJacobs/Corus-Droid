package fm.corus.android.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for tab NavHost back handling. A double-tap on a profile back
 * chevron used to pop Search's start destination and leave a blank tab body.
 */
class TabNavBackStackTest {
    @Test fun selectedSearchRootEmitsScrollWhileFirstSelectionDoesNot() {
        assertEquals(TabReselectAction.SELECT_ONLY, tabReselectAction(false, true, true))
        assertEquals(TabReselectAction.SCROLL_TO_TOP, tabReselectAction(true, true, true))
    }

    @Test fun selectedSearchSubscreenPopsBeforeAnyScrollAction() {
        assertEquals(TabReselectAction.POP_TO_START, tabReselectAction(true, true, false))
        assertEquals(TabReselectAction.RESTORE_START, tabReselectAction(true, false, false))
    }

    @Test
    fun `in-app back does not pop when already at tab root`() {
        assertFalse(canPopTabBackStack(hasPreviousEntry = false))
    }

    @Test
    fun `in-app back pops when a screen is above the tab root`() {
        assertTrue(canPopTabBackStack(hasPreviousEntry = true))
    }

    @Test
    fun `empty tab stack must remount the start destination`() {
        assertTrue(shouldRestoreTabStart(hasCurrentDestination = false))
    }

    @Test
    fun `populated tab stack is left alone`() {
        assertFalse(shouldRestoreTabStart(hasCurrentDestination = true))
    }

    @Test
    fun `city on a profile opened from map does not push another map`() {
        assertFalse(shouldOpenMapFromProfileCity(mapAlreadyOnStack = true))
        assertTrue(shouldOpenMapFromProfileCity(mapAlreadyOnStack = false))
    }

    @Test
    fun `typed map explore routes are recognized under a profile`() {
        val map = MapExploreRoute::class.qualifiedName
        assertTrue(destinationIsMapExplore("$map/{cityId}"))
        assertFalse(destinationIsMapExplore(OtherProfileRoute::class.qualifiedName + "/{userId}"))
        assertFalse(destinationIsMapExplore(null))
    }
}
