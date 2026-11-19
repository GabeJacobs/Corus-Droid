package fm.corus.android.ui.screens.live

import org.junit.Assert.*
import org.junit.Test

class LiveRoomInfoTest {
    @Test fun missingRoomInfoLeavesTheNameDraftAndDescriptionEmpty() {
        val room = LiveRoom("room", mapOf("hostId" to "host", "hostUsername" to "gabe"))
        assertEquals("", room.customTitle)
        assertEquals("", room.roomDescription)
        assertEquals("", LiveSetup("draft").title)
        assertEquals("", LiveSetup("draft").roomDescription)
    }
    @Test fun roomInfoRetainsMultilineDescriptionSeparatelyFromHostIdentity() {
        val room = LiveRoom("room", mapOf("hostId" to "host", "hostUsername" to "gabe",
            "title" to "After hours", "roomDescription" to "Deep cuts\nRequests welcome."))
        assertEquals("After hours", room.customTitle)
        assertEquals("Deep cuts\nRequests welcome.", room.roomDescription)
        assertEquals("gabe", room.host.username)
    }
}
