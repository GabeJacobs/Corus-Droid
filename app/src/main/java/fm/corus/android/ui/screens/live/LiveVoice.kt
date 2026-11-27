package fm.corus.android.ui.screens.live

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKitOverrides
import io.livekit.android.audio.AudioProcessorOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.nio.ByteBuffer
import livekit.org.webrtc.AudioTrackSink
import android.os.SystemClock
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
    private var scope: CoroutineScope? = null
    private var readinessEnabled = false
    private var audience = emptySet<String>()
    @Volatile private var readinessGate: LiveVoiceReadinessGate? = null
    private var requested = false
    @Volatile private var microphoneDesired = false
    @Volatile private var speechAllowed = false
    private val microphoneMutex = Mutex()
    private var readinessFailed = false
    private val legacySubscriptions = mutableSetOf<String>()
    private data class Probe(val sid: String, val track: RemoteAudioTrack, val sink: AudioTrackSink, @Volatile var seconds: Double = 0.0)
    private val probes = mutableMapOf<String, Probe>()
    private val challenges = mutableMapOf<String, Triple<LiveVoiceReadinessMessage, Double, Long>>()


    suspend fun connect(roomId: String, session: String, publish: Boolean) {
        microphoneDesired = publish
        if (!publish) speechAllowed = false
        microphoneMutex.lock()
        try { connectLocked(roomId, session, publish) } finally { microphoneMutex.unlock() }
    }

    private suspend fun connectLocked(roomId: String, session: String, publish: Boolean) {
        if (this.session != session || room == null) {
            disconnect()
            microphoneDesired = publish
            val credentials = repository.voiceToken(roomId, session)
            val connection = LiveKit.create(context, overrides = LiveKitOverrides(audioOptions = AudioOptions(
                audioProcessorOptions = AudioProcessorOptions(capturePostProcessor = LiveVoiceSpeechGate { speechAllowed }),
            )))
            try {
                room = connection
                this.session = session
                readinessEnabled = (credentials["readinessProtocol"] as? Number)?.toInt() == 1
                check(!readinessEnabled || credentials["audienceIDs"] is List<*>)
                audience = (credentials["audienceIDs"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
                if (readinessEnabled) startReadiness(connection, roomId, session)
                connection.connect(credentials.text("url"), credentials.text("token"))
                currentCoroutineContext().ensureActive()
                check(room === connection)
            } catch (error: Exception) {
                if (room === connection) disconnect() else connection.release()
                throw error
            }
        }
        val participant = room?.localParticipant ?: return
        checkReadiness()
        if (requested == publish && participant.isMicrophoneEnabled == publish) {
            if (readinessEnabled) releaseIfReady() else speechAllowed = publish && microphoneDesired
            return
        }
        requested = publish
        if (!readinessEnabled) {
            speechAllowed = publish && microphoneDesired
            val enabled = participant.setMicrophoneEnabled(publish)
            check(!publish || enabled) { "The microphone could not start." }
        } else if (publish) {
            try {
                // The post-processing gate is already closed before the SDK starts its owned mic.
                check(participant.setMicrophoneEnabled(true))
                val publication = checkNotNull(participant.getTrackPublication(Track.Source.MICROPHONE))
                readinessGate = LiveVoiceReadinessGate(session, publication.sid, audience).also {
                    it.legacySubscribed = publication.sid in legacySubscriptions
                }
                releaseIfReady()
            } catch (error: Exception) {
                requested = false; speechAllowed = false
                withContext(NonCancellable) { participant.setMicrophoneEnabled(false) }
                throw error
            }
        } else {
            speechAllowed = false
            readinessGate = null
            participant.setMicrophoneEnabled(false)
            for (publication in participant.trackPublications.values.toList()) {
                if (publication.source == Track.Source.MICROPHONE) publication.track?.let { participant.unpublishTrack(it) }
            }
            legacySubscriptions.clear()
        }
    }

    fun checkReadiness() { check(!readinessFailed) { "Live voice is unavailable." } }

    private fun releaseIfReady() {
        val people = room?.remoteParticipants?.values.orEmpty()
        readinessGate?.allowLegacy(people.map { it.identity?.value.orEmpty() }.toSet(),
            people.filter { it.attributes["corus.voiceReadiness"] == "1" }.map { it.identity?.value.orEmpty() }.toSet())
        speechAllowed = microphoneDesired && readinessGate?.ready == true
    }

    private fun startReadiness(connection: Room, roomId: String, session: String) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { this.scope = it }
        fun message(data: ByteArray): LiveVoiceReadinessMessage? = runCatching {
            if (data.size > 1024) return@runCatching null
            val json = JSONObject(String(data, Charsets.UTF_8))
            LiveVoiceReadinessMessage(json.getString("session"), json.getString("track"), json.getString("nonce"))
        }.getOrNull()
        suspend fun send(value: LiveVoiceReadinessMessage, topic: String, uid: String? = null) {
            val bytes = JSONObject(mapOf("session" to value.session, "track" to value.track, "nonce" to value.nonce)).toString().toByteArray()
            connection.localParticipant.publishData(bytes, topic = topic, identities = uid?.let { listOf(Participant.Identity(it)) })
        }
        scope.launch {
            connection.events.collect { event ->
                if (room !== connection) return@collect
                when (event) {
                    is RoomEvent.Reconnecting -> {
                        speechAllowed = false
                        readinessGate?.let { readinessGate = LiveVoiceReadinessGate(session, it.message.track, audience) }
                        challenges.clear(); legacySubscriptions.clear()
                    }
                    is RoomEvent.TrackSubscribed -> {
                        val uid = event.participant.identity?.value ?: return@collect
                        val track = event.track as? RemoteAudioTrack ?: return@collect
                        if (event.publication.source != Track.Source.MICROPHONE) return@collect
                        probes.remove(uid)?.let { it.track.removeSink(it.sink) }
                        lateinit var probe: Probe
                        val sink = object : AudioTrackSink {
                            override fun onData(data: ByteBuffer, bits: Int, rate: Int, channels: Int, frames: Int, timestamp: Long) {
                                if (rate > 0) probe.seconds += frames.toDouble() / rate
                            }
                        }
                        probe = Probe(event.publication.sid, track, sink)
                        probes[uid] = probe; track.addSink(sink)
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        val uid = event.participant.identity?.value ?: return@collect
                        probes.remove(uid)?.let { it.track.removeSink(it.sink) }; challenges.remove(uid)
                    }
                    is RoomEvent.LocalTrackSubscribed -> {
                        legacySubscriptions.add(event.publication.sid)
                        readinessGate?.takeIf { it.message.track == event.publication.sid }?.legacySubscribed = true
                        releaseIfReady()
                    }
                    is RoomEvent.DataReceived -> {
                        val uid = event.participant?.identity?.value ?: return@collect
                        val value = message(event.data) ?: return@collect
                        if (value.session != session) return@collect
                        if (event.topic == "corus.voice.ready.v1") { readinessGate?.acknowledge(uid, value); releaseIfReady() }
                        else if (event.topic == "corus.voice.prepare.v1") {
                            val probe = probes[uid] ?: return@collect
                            if (probe.sid == value.track) {
                                val previous = challenges[uid]
                                challenges[uid] = Triple(value, if (previous != null && previous.first == value) previous.second else probe.seconds, SystemClock.elapsedRealtime())
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        // Independent deadline: a slow membership refresh must not hold capture indefinitely.
        scope.launch {
            var nonce: String? = null
            var started = 0L
            while (isActive && room === connection) {
                val gate = readinessGate
                val now = SystemClock.elapsedRealtime()
                if (gate != null && !gate.ready) {
                    if (nonce != gate.message.nonce) { nonce = gate.message.nonce; started = now }
                    if (now - started >= 10_000) {
                        readinessFailed = true; speechAllowed = false
                        connection.localParticipant.setMicrophoneEnabled(false)
                        return@launch
                    }
                }
                delay(200)
            }
        }
        scope.launch {
            var nonce: String? = null
            var started = 0L
            var refreshed = 0L
            var wasReconnecting = false
            while (isActive && room === connection) {
                val now = SystemClock.elapsedRealtime()
                val reconnecting = connection.state == Room.State.RECONNECTING
                if (reconnecting && !wasReconnecting) {
                    speechAllowed = false
                    readinessGate?.let { readinessGate = LiveVoiceReadinessGate(session, it.message.track, audience) }
                    challenges.clear(); legacySubscriptions.clear()
                }
                wasReconnecting = reconnecting
                val publication = connection.localParticipant.trackPublications.values.firstOrNull { it.source == Track.Source.MICROPHONE }
                if (requested && publication != null && readinessGate?.message?.track != publication.sid) {
                    readinessGate = LiveVoiceReadinessGate(session, publication.sid, audience).also { it.legacySubscribed = publication.sid in legacySubscriptions }
                    releaseIfReady()
                }
                val gate = readinessGate
                if (gate != null && !gate.ready) {
                    if (nonce != gate.message.nonce) { nonce = gate.message.nonce; started = now; refreshed = now }
                    if (now - started >= 10_000) {
                        readinessFailed = true; speechAllowed = false
                        connection.localParticipant.setMicrophoneEnabled(false)
                        return@launch
                    }
                    try {
                        if (now - refreshed >= 1_000) {
                            refreshed = now
                            val data = repository.voiceToken(roomId, session)
                            if (room !== connection || readinessGate !== gate) continue
                            check((data["readinessProtocol"] as? Number)?.toInt() == 1 && data["audienceIDs"] is List<*>)
                            audience = (data["audienceIDs"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
                            gate.updateAudience(audience)
                        }
                        releaseIfReady()
                        if (!gate.ready) send(gate.message, "corus.voice.prepare.v1")
                    } catch (error: CancellationException) { throw error } catch (_: Exception) { /* Stay gated. */ }
                }
                for ((uid, pending) in challenges.toMap()) {
                    if (SystemClock.elapsedRealtime() - pending.third > 1_000) { challenges.remove(uid); continue }
                    val probe = probes[uid] ?: continue
                    val remote = connection.remoteParticipants.values.firstOrNull { it.identity?.value == uid }
                    if (probe.sid == pending.first.track && probe.seconds - pending.second >= 0.15 && remote?.isMicrophoneEnabled == true) {
                        try { send(pending.first, "corus.voice.ready.v1", uid) }
                        catch (error: CancellationException) { throw error } catch (_: Exception) { /* Retry the acknowledgment. */ }
                    }
                }
                delay(200)
            }
        }
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
            .filter { it.isMicrophoneEnabled && (it !== connection.localParticipant || !readinessEnabled || (microphoneDesired && readinessGate?.ready == true)) }
            .mapNotNull { it.identity?.value }
            .toSet()
    }

    fun disconnect() {
        microphoneDesired = false; speechAllowed = false
        scope?.cancel(); scope = null
        readinessGate = null; requested = false; readinessFailed = false; readinessEnabled = false
        for (probe in probes.values) probe.track.removeSink(probe.sink)
        probes.clear(); challenges.clear(); legacySubscriptions.clear(); audience = emptySet()
        receivedRemoteMicrophone = false
        room?.disconnect()
        room?.release()
        room = null
        session = null
    }
}
