package fm.corus.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTypingTest {
    @Test
    fun dropsSelfBlockedAndStale() {
        val now = 10_000L
        val ids = ChatTyping.activeIds(
            listOf(
                TypingPulse("me", now),
                TypingPulse("blocked", now),
                TypingPulse("stale", now - ChatTyping.STALE_MS - 1),
                TypingPulse("live", now - 400),
            ),
            now,
            "me",
            setOf("blocked"),
        )
        assertEquals(listOf("live"), ids)
    }

    @Test
    fun labelsCityRoomsSeparately() {
        assertEquals("city", ChatTyping.threadKind(true, "nyc"))
        assertEquals("group", ChatTyping.threadKind(true, null))
        assertEquals("dm", ChatTyping.threadKind(false, null))
    }

    @Test
    fun testersBypassRemoteFlag() {
        assertEquals(true, ChatTyping.isEnabled(false, "FUQZIrZR08T2Ux2vYpPzWx7B1rv1"))
        assertEquals(true, ChatTyping.isEnabled(false, "u3UmswvOg5c2r9zYlOidJYFzqbp2"))
        assertEquals(false, ChatTyping.isEnabled(false, "someone-else"))
        assertEquals(true, ChatTyping.isEnabled(true, "someone-else"))
    }
}
