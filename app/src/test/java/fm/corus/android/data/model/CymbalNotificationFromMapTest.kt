package fm.corus.android.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CymbalNotificationFromMapTest {

    @Test
    fun `parses createdAt when delivered as Firestore Timestamp (listener path)`() {
        val ms = 1776637448435L
        val data = mapOf<String, Any?>(
            "type" to "like",
            "fromUserId" to "u1",
            "createdAt" to Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt()),
        )
        val n = CymbalNotification.fromMap("n1", data)
        assertEquals(ms, n.timestamp.time)
    }

    @Test
    fun `parses createdAt when delivered as Number millis (cloud function path)`() {
        val ms = 1776637448435L
        val data = mapOf<String, Any?>(
            "type" to "like",
            "fromUserId" to "u1",
            "createdAt" to ms,
        )
        val n = CymbalNotification.fromMap("n1", data)
        assertEquals(ms, n.timestamp.time)
    }

    @Test
    fun `falls back to now when createdAt is missing`() {
        val before = Date().time
        val n = CymbalNotification.fromMap(
            "n1",
            mapOf("type" to "like", "fromUserId" to "u1"),
        )
        val after = Date().time
        assertTrue(n.timestamp.time in before..after)
    }
}
