package fm.corus.android.ui.screens.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRoomFollowingTest {
    @Test fun aFollowedHostOrGuestDjQualifiesTheRoom() {
        val room = LiveRoom("room", mapOf(
            "hostId" to "host",
            "djs" to listOf(mapOf("uid" to "guest1"), mapOf("uid" to "guest2")),
        ))
        assertTrue(room.matchesFollowing(setOf("host")))
        assertTrue(room.matchesFollowing(setOf("guest2")))
        assertFalse(room.matchesFollowing(setOf("listener")))
        assertFalse(room.matchesFollowing(emptySet()))
        assertTrue(room.matchesFollowing(null))
    }
}
