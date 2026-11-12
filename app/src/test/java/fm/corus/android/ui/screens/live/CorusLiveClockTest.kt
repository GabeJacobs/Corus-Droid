package fm.corus.android.ui.screens.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorusLiveClockTest {
    private val fields = mapOf(
        "hostId" to "host",
        "status" to "playing",
        "current" to mapOf("id" to "song", "title" to "Song", "durationMs" to 180_000),
        "startedAt" to 1_000L,
        "deadline" to 181_000L,
        "leaseUntil" to 200_000L,
        "voiceMode" to "music",
    )

    @Test fun talkingFreezesTheSharedSongPosition() {
        val talking = LiveRoom("room", fields + mapOf("pausedAt" to 6_000L, "voiceMode" to "talk"))
        assertEquals(5_000L, talking.position(50_000L))
        assertFalse(talking.musicShouldPlay(50_000L))
        val resumed = LiveRoom("room", fields + mapOf("startedAt" to 45_000L, "deadline" to 225_000L))
        assertEquals(5_000L, resumed.position(50_000L))
        assertTrue(resumed.musicShouldPlay(50_000L))
    }

    @Test fun expiredEndedAndEmptyRoomsCannotPlayMusic() {
        assertFalse(LiveRoom("room", fields).musicShouldPlay(250_000L))
        assertFalse(LiveRoom("room", fields + ("status" to "ended")).musicShouldPlay(5_000L))
        assertFalse(LiveRoom("room", fields - "current").musicShouldPlay(5_000L))
        assertEquals(0L, LiveRoom("room", fields).position(-1_000L))
    }
}
