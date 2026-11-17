package fm.corus.android.ui.screens.live

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.log10

internal data class LiveRecording(val bytes: ByteArray, val seconds: Double)

internal const val LiveVoiceMeterSegmentCount = 8

internal fun liveVoiceMeterBars(audioLevel: Float): Int {
    if (!audioLevel.isFinite() || audioLevel <= 0f) return 0
    // Match the iOS display range, from quiet speech at -50 dB to full at -12 dB.
    val decibels = 20f * log10(audioLevel)
    val level = ((decibels + 50f) / 38f).coerceIn(0f, 1f)
    return ceil(level * LiveVoiceMeterSegmentCount).toInt()
}

internal class LiveVoice @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LiveRepository,
) {
    private var room: Room? = null
    private var session: String? = null

    suspend fun connect(roomId: String, session: String, publish: Boolean) {
        if (this.session != session || room == null) {
            disconnect()
            val credentials = repository.voiceToken(roomId, session)
            val connection = LiveKit.create(context)
            try {
                connection.connect(credentials.text("url"), credentials.text("token"))
                room = connection
                this.session = session
            } finally {
                if (room !== connection) connection.release()
            }
        }
        val participant = room?.localParticipant ?: return
        val enabled = participant.setMicrophoneEnabled(publish)
        check(!publish || enabled) { "The microphone could not start." }
    }

    fun levels(): Map<String, Float> {
        val connection = room ?: return emptyMap()
        val speakers = connection.activeSpeakers + connection.localParticipant
        return speakers.mapNotNull { participant ->
            participant.identity?.value?.let { it to participant.audioLevel.coerceIn(0f, 1f) }
        }.toMap()
    }

    fun unmutedMicrophoneIDs(session: String): Set<String> {
        val connection = room ?: return emptySet()
        if (this.session != session || connection.state != Room.State.CONNECTED) return emptySet()
        return (connection.remoteParticipants.values + connection.localParticipant)
            .filter { it.isMicrophoneEnabled }
            .mapNotNull { it.identity?.value }
            .toSet()
    }

    fun disconnect() {
        room?.disconnect()
        room?.release()
        room = null
        session = null
    }
}
