package fm.corus.android.ui.screens.live

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
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
    private var receivedRemoteMicrophone = false

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

    fun isConnected(session: String): Boolean =
        this.session == session && room?.state == Room.State.CONNECTED

    fun isReconnecting(session: String): Boolean =
        this.session == session && room?.state == Room.State.RECONNECTING

    fun hasReceivedRemoteMicrophone(session: String): Boolean =
        this.session == session && receivedRemoteMicrophone

    fun waitingForRemoteMicrophone(session: String): Boolean {
        val connection = room ?: return false
        if (!isConnected(session)) return false
        val microphones = connection.remoteParticipants.values.mapNotNull {
            it.getTrackPublication(Track.Source.MICROPHONE)
        }
        // A room connection can precede microphone publication and subscription.
        // Intentionally muted publications are settled, rather than loading.
        if (microphones.any { !it.muted && it.track != null }) receivedRemoteMicrophone = true
        return !receivedRemoteMicrophone &&
            (microphones.isEmpty() || microphones.any { !it.muted })
    }

    fun holdingTalkPresentation(session: String): Boolean {
        if (!hasReceivedRemoteMicrophone(session) || isReconnecting(session)) return false
        if (!isConnected(session)) return true
        val microphones = room?.remoteParticipants?.values.orEmpty().mapNotNull {
            it.getTrackPublication(Track.Source.MICROPHONE)
        }
        // Keep the established label across mic teardown until the room's Music snapshot.
        // A microphone that is explicitly muted still uses the normal muted presentation.
        return microphones.isEmpty() || microphones.any { !it.muted && it.track == null }
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
        receivedRemoteMicrophone = false
        room?.disconnect()
        room?.release()
        room = null
        session = null
    }
}
