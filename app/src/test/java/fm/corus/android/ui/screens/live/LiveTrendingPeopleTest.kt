package fm.corus.android.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTrendingPeopleTest {
    @Test fun trendingRequiresFiveListenersRegardlessOfDJs() {
        val room = LiveRoom("room", mapOf("djs" to listOf(mapOf("uid" to "dj1"), mapOf("uid" to "dj2"))))
        val state = LiveUiState(discovery = listOf(room), discoveryListenerCounts = mapOf("room" to 3))
        assertNull(state.trendingRoom(5))
        assertEquals(room, state.copy(discoveryListenerCounts = mapOf("room" to 5)).trendingRoom(5))
        assertNull(state.copy(discoveryListenerCounts = emptyMap()).trendingRoom(5))
    }

    @Test fun trendingRanksByListeners() {
        val solo = LiveRoom("solo", emptyMap())
        val group = LiveRoom("group", mapOf("djs" to listOf(mapOf("uid" to "dj1"), mapOf("uid" to "dj2"))))
        val state = LiveUiState(discovery = listOf(solo, group), discoveryListenerCounts = mapOf("solo" to 6, "group" to 5))
        assertEquals(solo, state.trendingRoom(5))
    }
}
