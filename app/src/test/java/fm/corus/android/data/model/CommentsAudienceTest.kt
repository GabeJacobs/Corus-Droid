package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentsAudienceTest {

    @Test
    fun `parses each known wire value`() {
        assertEquals(CommentsAudience.EVERYONE, CommentsAudience.parse("everyone"))
        assertEquals(CommentsAudience.FOLLOWERS, CommentsAudience.parse("followers"))
        assertEquals(CommentsAudience.FOLLOWING, CommentsAudience.parse("following"))
        assertEquals(CommentsAudience.OFF, CommentsAudience.parse("off"))
    }

    @Test
    fun `unknown string falls back to null`() {
        assertNull(CommentsAudience.parse("mutuals"))
        assertNull(CommentsAudience.parse(""))
    }

    @Test
    fun `null and wrong type fall back to null`() {
        assertNull(CommentsAudience.parse(null))
        assertNull(CommentsAudience.parse(123))
        assertNull(CommentsAudience.parse(true))
    }

    @Test
    fun `wire values round-trip`() {
        for (audience in CommentsAudience.values()) {
            assertEquals(audience, CommentsAudience.parse(audience.wire))
        }
    }

    @Test
    fun `following wire value is the literal lowercase following`() {
        // Backend Firestore rule + serializer match on the exact string —
        // a typo here would silently downgrade the post to "everyone".
        assertEquals("following", CommentsAudience.FOLLOWING.wire)
    }
}
