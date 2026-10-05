package fm.corus.android.domain

import org.junit.Assert.*
import org.junit.Test

class ForYouStayCloseProgressTest {
    @Test fun `five posts unlock and malformed data stays locked`() {
        assertFalse(ForYouStayCloseProgress(4).unlocked)
        assertTrue(ForYouStayCloseProgress(5).unlocked)
        assertEquals(4, ForYouStayCloseProgress(1).remaining)
        assertNull(ForYouStayCloseProgress.parse(null))
        assertFalse(ForYouStayCloseProgress.parse(emptyMap<String, Any>())!!.unlocked)
        assertEquals(0, ForYouStayCloseProgress.parse(mapOf("postCount" to -5))!!.postCount)
        assertTrue(ForYouStayCloseProgress.parse(mapOf("postCount" to 5L, "threshold" to 5L))!!.unlocked)
    }
    @Test fun `trial expiry locks only nonmembers who met the posting requirement`() {
        val future = System.currentTimeMillis() + 100000
        val past = System.currentTimeMillis() - 1
        assertTrue(ForYouStayCloseProgress(5, trialEndsAt = future).canAccess)
        assertFalse(ForYouStayCloseProgress(5, trialEndsAt = past).canAccess)
        assertTrue(ForYouStayCloseProgress(5, hasFullAccess = true, trialEndsAt = past).canAccess)
        assertFalse(ForYouStayCloseProgress(4, hasFullAccess = true).canAccess)
        assertFalse(ForYouStayCloseProgress.parse(mapOf("postCount" to 5, "paywallLocked" to true))!!.canAccess)
        assertTrue(ForYouStayCloseProgress.parse(mapOf("postCount" to 5, "hasFullAccess" to true, "paywallLocked" to true))!!.canAccess)
        assertEquals(future, ForYouStayCloseProgress.parse(mapOf("postCount" to 5, "trialEndsAt" to future))!!.trialEndsAt)
    }
}
