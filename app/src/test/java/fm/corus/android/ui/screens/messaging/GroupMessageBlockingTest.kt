package fm.corus.android.ui.screens.messaging

import org.junit.Assert.*
import org.junit.Test

class GroupMessageBlockingTest {
    private val blocked = setOf("blocked")
    @Test fun ordinaryChatsAreUnchanged() {
        assertFalse(collapseGroupMessage(true, "friend", false, blocked, false))
        assertFalse(collapseGroupMessage(true, "blocked", false, emptySet(), false))
        assertFalse(collapseGroupMessage(false, "blocked", false, blocked, false))
    }
    @Test fun blockedMessagesCollapseButSystemRowsRemain() {
        assertTrue(collapseGroupMessage(true, "blocked", false, blocked, false))
        assertFalse(collapseGroupMessage(true, "blocked", true, blocked, false))
    }
    @Test fun revealIsPerMessageAndDoesNotExposeQuotesElsewhere() {
        assertFalse(collapseGroupMessage(true, "blocked", false, blocked, true))
        assertTrue(collapseGroupMessage(true, "blocked", false, blocked, false))
        assertTrue(isBlockedGroupAuthor(true, "blocked", blocked))
    }
    @Test fun unblockAndMissingAuthorAreSafe() {
        assertFalse(isBlockedGroupAuthor(true, "blocked", emptySet()))
        assertFalse(isBlockedGroupAuthor(true, null, blocked))
    }
}
