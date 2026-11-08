package fm.corus.android.ui.screens.live
import org.junit.Assert.*
import org.junit.Test
class CorusLiveClockTest {
    private val room = LiveRoom("room", "host", "host", null, null, "playing",
        LiveTrack("song", "Song", "Artist", "spotify", 180000), emptyList(), 1, 0, 1000, 181000, 200000, null, "music", true, null)
    @Test fun talkingFreezesTheSharedSongPosition() {
        val talking = room.copy(pausedAt = 6000, voiceMode = "talk")
        assertEquals(5.0, talking.position(50000), 0.001)
        assertFalse(talking.musicShouldPlay(50000))
        val resumed = room.copy(startedAt = 45000, deadline = 225000)
        assertEquals(5.0, resumed.position(50000), 0.001)
        assertTrue(resumed.musicShouldPlay(50000))
    }
    @Test fun expiredEndedAndEmptyRoomsCannotPlayMusic() {
        assertFalse(room.musicShouldPlay(250000))
        assertFalse(room.copy(status = "ended").musicShouldPlay(5000))
        assertFalse(room.copy(current = null).musicShouldPlay(5000))
        assertEquals(0.0, room.position(-1000), 0.001)
    }
}
