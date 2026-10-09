package fm.corus.android.service

import org.junit.Assert.*
import org.junit.Test

class ProfileCollectionAnalyticsTest {
    private val context = ProfileCollectionAnalytics.Context("00112233-4455-6677-8899-aabbccddeeff", "self", "gifts", "all")
    @Test fun versionedPrivacySafeContract() {
        val values = ProfileCollectionAnalytics.params("opened", context, mapOf("phase" to "summary", "gift_count" to 2, "post_id" to "secret", "note" to "secret", "error" to "raw error"))
        assertEquals("profile_collection_event", ProfileCollectionAnalytics.EVENT)
        assertEquals(1, values["collection_version"]); assertEquals(2L, values["gift_count"])
        assertFalse(values.containsKey("post_id")); assertFalse(values.containsKey("note")); assertFalse(values.containsKey("error"))
    }
    @Test fun unknownAndInvalidFieldsNeverBecomeUserData() {
        val values = ProfileCollectionAnalytics.params("made up", context.copy(sessionId = "user-id", profileType = "@gabe"), mapOf("item_count" to -5, "gift_count" to Double.NaN, "duration_ms" to Double.POSITIVE_INFINITY, "phase" to "secret"))
        assertEquals(mapOf("collection_version" to 1, "action" to "unknown", "profile_type" to "unknown", "section" to "gifts", "media_type" to "all", "item_count" to 0L), values)
    }
}
