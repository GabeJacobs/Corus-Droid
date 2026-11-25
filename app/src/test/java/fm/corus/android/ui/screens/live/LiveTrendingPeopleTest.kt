package fm.corus.android.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTrendingPeopleTest {
    private val room = LiveRoom("room", mapOf("djs" to listOf(mapOf("uid" to "dj1"), mapOf("uid" to "dj2"))))
    @Test fun trendingRequiresSixPeopleIncludingHostAndDJs() {
        val state = LiveUiState(discovery = listOf(room), discoveryListenerCounts = mapOf("room" to 3))
        assertEquals(room, state.trendingRoom(6))
        assertNull(state.copy(discoveryListenerCounts = mapOf("room" to 2)).trendingRoom(6))
        assertNull(state.copy(discoveryListenerCounts = emptyMap()).trendingRoom(6))
    }
    @Test fun authoritativeTotalDoesNotAddDJsAgain() {
        val state = LiveUiState(discovery = listOf(room), discoveryListenerCounts = mapOf("room" to 3),
            discoveryPeopleCounts = mapOf("room" to 6))
        assertEquals(6, state.discoveryPeople(room))
        assertNull(state.copy(discoveryPeopleCounts = mapOf("room" to 5)).trendingRoom(6))
        assertEquals(6, livePeopleCount(3, 3, 6))
        assertEquals(1, livePeopleCount(0, 0))
    }
    @Test fun trendingRanksByEveryoneInTheRoom() {
        val solo = LiveRoom("solo", emptyMap())
        val state = LiveUiState(discovery = listOf(solo, room), discoveryListenerCounts = mapOf("solo" to 5, "room" to 4))
        assertEquals(room, state.trendingRoom(6))
    }
}
