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

    @Test fun miniPlayerReactionsFollowTheDjWhoIsPlaying() {
        val solo = LiveRoom("room", fields)
        assertFalse(solo.canLikeFromMiniPlayer("host", 5_000L))
        assertTrue(solo.canLikeFromMiniPlayer("listener", 5_000L))

        val guestTurn = LiveRoom("room", fields + ("currentDJ" to mapOf("uid" to "guest")))
        assertFalse(guestTurn.canLikeFromMiniPlayer("guest", 5_000L))
        assertTrue(guestTurn.canLikeFromMiniPlayer("host", 5_000L))
        assertFalse(guestTurn.canLikeFromMiniPlayer("listener", 181_000L))
    }

    @Test fun nextTrackAndDjFollowServerRotation() {
        fun song(id: String) = mapOf("id" to id, "title" to id, "durationMs" to 180_000)
        val lineup = fields + mapOf(
            "queue" to listOf(song("hostSong")),
            "djs" to listOf(
                mapOf("uid" to "emptyGuest", "order" to 1, "queue" to emptyList<LiveFields>()),
                mapOf("uid" to "guest", "order" to 2, "queue" to listOf(song("guestSong"))),
            ),
        )

        assertEquals("guestSong", LiveRoom("room", lineup).nextTrack?.id)
        assertEquals("guest", LiveRoom("room", lineup).nextDJUid)
        val afterGuest = lineup + ("currentDJOrder" to 2)
        assertEquals("hostSong", LiveRoom("room", afterGuest).nextTrack?.id)
        assertEquals("host", LiveRoom("room", afterGuest).nextDJUid)
        assertEquals("guestSong", LiveRoom("room", afterGuest - "queue").nextTrack?.id)
        assertEquals("guest", LiveRoom("room", afterGuest - "queue").nextDJUid)
        assertEquals(null, LiveRoom("room", lineup - "djs").nextDJUid)
    }

    @Test fun playingDjTracksSoloAndCollaborativePlayback() {
        val lineup = fields + mapOf(
            "djs" to listOf(mapOf("uid" to "guest")),
            "currentDJ" to mapOf("uid" to "guest"),
        )
        assertEquals("guest", LiveRoom("room", lineup).playingDJUid)
        assertEquals("host", LiveRoom("room", lineup - "currentDJ").playingDJUid)
        assertEquals(null, LiveRoom("room", lineup + ("voiceMode" to "talk")).playingDJUid)
        assertEquals(null, LiveRoom("room", lineup + ("pausedAt" to 6_000L)).playingDJUid)
        assertEquals(null, LiveRoom("room", lineup + ("pendingSkip" to mapOf("id" to "skip"))).playingDJUid)
        assertEquals("host", LiveRoom("room", fields).playingDJUid)
        assertEquals("host", LiveRoom("room", fields + ("djs" to emptyList<LiveFields>())).playingDJUid)
        assertEquals(null, LiveRoom("room", fields + ("pausedAt" to 6_000L)).playingDJUid)
    }

    @Test fun expiredEndedAndEmptyRoomsCannotPlayMusic() {
        assertFalse(LiveRoom("room", fields).musicShouldPlay(250_000L))
        assertFalse(LiveRoom("room", fields + ("status" to "ended")).musicShouldPlay(5_000L))
        assertFalse(LiveRoom("room", fields - "current").musicShouldPlay(5_000L))
        assertEquals(0L, LiveRoom("room", fields).position(-1_000L))
    }
}
