package fm.corus.android.ui.screens.messaging

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the pure reaction-collapsing helpers behind the group "Reactions" badge. */
class MessageReactionsTest {

    @Test
    fun `reactionEmojiChar maps legacy named keys to emoji`() {
        assertEquals("❤️", reactionEmojiChar("heart"))
        assertEquals("👍", reactionEmojiChar("thumbsup"))
        assertEquals("🔥", reactionEmojiChar("fire"))
    }

    @Test
    fun `reactionEmojiChar passes raw emoji keys (current data) through`() {
        assertEquals("❤️", reactionEmojiChar("❤️"))
        assertEquals("😮", reactionEmojiChar("😮"))
    }

    @Test
    fun `reactionEmojiChar leaves unknown keys unchanged`() {
        assertEquals("🦄", reactionEmojiChar("🦄"))
    }

    @Test
    fun `orderedReactionEmojiChars dedups and orders by picker with extras last`() {
        val reactions = mapOf(
            "🔥" to listOf("a"),
            "❤️" to listOf("b", "c"),
            "🦄" to listOf("d"), // not in the picker -> sorts last
        )
        assertEquals(listOf("❤️", "🔥", "🦄"), orderedReactionEmojiChars(reactions))
    }

    @Test
    fun `orderedReactionEmojiChars skips empty reaction lists`() {
        val reactions = mapOf(
            "❤️" to emptyList<String>(),
            "👍" to listOf("a"),
        )
        assertEquals(listOf("👍"), orderedReactionEmojiChars(reactions))
    }

    @Test
    fun `orderedReactionEmojiChars maps legacy keys before ordering`() {
        val reactions = mapOf(
            "thumbsup" to listOf("a"),
            "heart" to listOf("b"),
        )
        assertEquals(listOf("❤️", "👍"), orderedReactionEmojiChars(reactions))
    }

    @Test
    fun `reactionRowTap removes your own reaction`() {
        assertEquals(ReactionRowTap.Remove, reactionRowTap(isMine = true, hasResolvedUser = true))
        // Your own row always removes, even if your own profile didn't resolve.
        assertEquals(ReactionRowTap.Remove, reactionRowTap(isMine = true, hasResolvedUser = false))
    }

    @Test
    fun `reactionRowTap opens another reactor's profile`() {
        assertEquals(ReactionRowTap.OpenProfile, reactionRowTap(isMine = false, hasResolvedUser = true))
    }

    @Test
    fun `reactionRowTap is inert for an unresolved reactor`() {
        assertEquals(ReactionRowTap.Inert, reactionRowTap(isMine = false, hasResolvedUser = false))
    }
}
