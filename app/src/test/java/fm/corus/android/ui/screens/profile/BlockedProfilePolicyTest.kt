package fm.corus.android.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockedProfilePolicyTest {
    @Test
    fun `viewer-created block exposes only the limited profile`() {
        assertEquals(
            BlockedProfileVisibility.LIMITED,
            blockedProfileVisibility("other", setOf("other"), emptySet()),
        )
    }

    @Test
    fun `incoming block remains unavailable`() {
        assertEquals(
            BlockedProfileVisibility.UNAVAILABLE,
            blockedProfileVisibility("other", emptySet(), setOf("other")),
        )
    }

    @Test
    fun `viewer-created block wins when both block sets contain the user`() {
        assertEquals(
            BlockedProfileVisibility.LIMITED,
            blockedProfileVisibility("other", setOf("other"), setOf("other")),
        )
    }

    @Test
    fun `unblocked profile remains full`() {
        assertEquals(
            BlockedProfileVisibility.FULL,
            blockedProfileVisibility("other", emptySet(), emptySet()),
        )
    }
}
