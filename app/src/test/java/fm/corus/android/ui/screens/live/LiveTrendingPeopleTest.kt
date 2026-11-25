package fm.corus.android.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTrendingPeopleTest {
    @Test fun trendingCountsTheHostAndGuestDJs() {
        val room = LiveRoom("room", mapOf("djs" to listOf(mapOf("uid" to "dj1"), mapOf("uid" to "dj2"))))
        val state = LiveUiState(discovery = listOf(room), discoveryListenerCounts = mapOf("room" to 3))
        assertEquals(room, state.trendingRoom(6))
        assertNull(state.copy(discoveryListenerCounts = mapOf("room" to 2)).trendingRoom(6))
        assertNull(state.copy(discoveryListenerCounts = emptyMap()).trendingRoom(6))
    }

    @Test fun trendingRanksByEveryoneInTheRoom() {
        val solo = LiveRoom("solo", emptyMap())
        val group = LiveRoom("group", mapOf("djs" to listOf(mapOf("uid" to "dj1"), mapOf("uid" to "dj2"))))
        val state = LiveUiState(discovery = listOf(solo, group), discoveryListenerCounts = mapOf("solo" to 5, "group" to 4))
        assertEquals(group, state.trendingRoom(6))
    }
}
