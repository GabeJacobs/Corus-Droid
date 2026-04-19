package fm.corus.android.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostActionMenuTest {

    @Test
    fun `report and block shown for non-owner human-authored posts`() {
        assertTrue(showPostReportBlockActions(isMine = false, authorIsBot = false))
    }

    @Test
    fun `report and block hidden for bot-authored posts`() {
        assertFalse(showPostReportBlockActions(isMine = false, authorIsBot = true))
    }

    @Test
    fun `report and block hidden for own posts`() {
        assertFalse(showPostReportBlockActions(isMine = true, authorIsBot = false))
    }

    @Test
    fun `report and block hidden for own bot posts`() {
        assertFalse(showPostReportBlockActions(isMine = true, authorIsBot = true))
    }
}
