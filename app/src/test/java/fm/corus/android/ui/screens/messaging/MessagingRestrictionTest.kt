package fm.corus.android.ui.screens.messaging

import fm.corus.android.data.model.MessagingRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagingRestrictionTest {
    @Test
    fun `legacy English message is nobody`() {
        assertEquals(
            MessagingRestriction.NOBODY,
            messagingRestrictionFrom(RuntimeException("This user has turned off messaging")),
        )
    }

    @Test
    fun `unrelated errors are not a restriction`() {
        assertNull(messagingRestrictionFrom(RuntimeException("network")))
    }

    @Test
    fun `nested cause still matches the legacy message`() {
        val wrapped = RuntimeException("failed", RuntimeException("This user has turned off messaging"))
        assertEquals(MessagingRestriction.NOBODY, messagingRestrictionFrom(wrapped))
    }
}
