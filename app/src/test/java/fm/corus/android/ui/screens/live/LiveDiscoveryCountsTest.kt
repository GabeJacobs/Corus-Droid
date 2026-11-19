package fm.corus.android.ui.screens.live

import org.junit.Assert.*
import org.junit.Test

class LiveDiscoveryCountsTest {
    private val rooms = listOf("host-only", "busy", "pending").map { LiveRoom(it, emptyMap()) }

    @Test fun waitsForEachRoomsCountIncludingZeroListeners() {
        assertTrue(roomsWithDiscoveryCounts(rooms, emptyMap()).isEmpty())
        assertEquals(rooms.take(2), roomsWithDiscoveryCounts(rooms, mapOf("host-only" to 0, "busy" to 12)))
    }

    @Test fun retainsRecentCountsOnRefreshFailureAndExpiresStaleOnes() {
        val cached = mapOf("busy" to 12)
        assertEquals(cached, freshDiscoveryCounts(cached, 100_000, 130_000))
        assertTrue(freshDiscoveryCounts(cached, 100_000, 160_000).isEmpty())
        assertTrue(roomsWithDiscoveryCounts(rooms, freshDiscoveryCounts(cached, 100_000, 160_000)).isEmpty())
    }
}
