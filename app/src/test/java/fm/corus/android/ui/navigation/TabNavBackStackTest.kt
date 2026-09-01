package fm.corus.android.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for tab NavHost back handling. A double-tap on a profile back
 * chevron used to pop Search's start destination and leave a blank tab body.
 */
class TabNavBackStackTest {

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
}
