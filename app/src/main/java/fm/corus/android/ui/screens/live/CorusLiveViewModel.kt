package fm.corus.android.ui.screens.live

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.repository.MusicSearchRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.SpotifyPlaybackService
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

internal data class LiveState(
    val available: Boolean? = null, val rooms: List<LiveRoom> = emptyList(), val room: LiveRoom? = null,
    val selected: List<LiveTrack> = emptyList(), val results: List<LiveTrack> = emptyList(),
    val messages: List<Pair<String, String>> = emptyList(), val joined: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val people: Int = 1,
)

@HiltViewModel
internal class CorusLiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth, private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions, private val search: MusicSearchRepository,
    private val cloudFunctions: fm.corus.android.data.remote.CloudFunctionsDataSource,
    private val spotify: SpotifyPlaybackService, private val normalPlayer: NowPlayingManager,
) : ViewModel() {
    private val _state = MutableStateFlow(LiveState())
    val state = _state.asStateFlow()
    private fun update(change: (LiveState) -> LiveState) { _state.value = change(_state.value) }
    val hosting get() = state.value.room?.hostId == auth.currentUser?.uid
    private var discovery: ListenerRegistration? = null
    private var roomListener: ListenerRegistration? = null
    private var chatListener: ListenerRegistration? = null
    private var heartbeat: Job? = null
    private var searchJob: Job? = null
    private var syncJob: Job? = null
    private var syncKey: String? = null
    private var voice: Room? = null
    private var voiceKey: String? = null
    private var musicKey: String? = null
    private var offset = 0L
    private val audioLock = Mutex()
    init { viewModelScope.launch {
        runCatching { command("availability")["available"] == true }
            .onSuccess { available -> update { it.copy(available = available) }
                if (available) discovery = db.collection("corusLiveRooms").whereEqualTo("status", "playing").limit(25).addSnapshotListener { snapshot, error ->
                    if (error != null) fail(error) else update { it.copy(rooms = snapshot?.documents?.map { d -> LiveRoom.parse(d.id, d.data ?: emptyMap()) }?.filter { r -> r.leaseUntil > System.currentTimeMillis() } ?: emptyList()) }
                }
            }.onFailure { fail(it); update { s -> s.copy(available = false) } }
    } }
    @Suppress("UNCHECKED_CAST")
    private suspend fun command(action: String, fields: Map<String, Any> = emptyMap(), roomId: String? = state.value.room?.id): Map<String, Any?> {
        val before = System.currentTimeMillis()
        val result = functions.getHttpsCallable("corusLive").call(fields + mapOf("action" to action, "voiceProtocol" to 1, "clientPlatform" to "android") + (roomId?.let { mapOf("roomId" to it) } ?: emptyMap())).await().getData() as? Map<String, Any?> ?: emptyMap()
        (result["serverNow"] as? Number)?.let { offset = it.toLong() - (before + System.currentTimeMillis()) / 2 }
        return result
    }
    private fun fail(error: Throwable) { if (error !is CancellationException) update { it.copy(error = error.message ?: "Live could not connect. Please try again.") } }
    private fun act(block: suspend () -> Unit) { if (state.value.busy) return; viewModelScope.launch { update { it.copy(busy = true, error = null) }; try { block() } catch (e: Exception) { fail(e) } finally { update { it.copy(busy = false) } } } }
    fun search(query: String) { searchJob?.cancel(); searchJob = viewModelScope.launch { delay(300); try {
        val result = search.search(query, includeSoundCloud = false).tracks.filter { it.source.raw in listOf("spotify", "applemusic") && it.durationMs >= 1000 }.map { t -> LiveTrack(t.id, t.name, t.artistName, t.source.raw, t.durationMs.toLong(), t.albumArtURL, t.isrc, t.appleMusicId, t.spotifyURI) }
        update { it.copy(results = result) }
    } catch (e: Exception) { fail(e) } } }
    fun add(track: LiveTrack) {
        val room = state.value.room
        if (room == null) update { it.copy(selected = it.selected + track) }
        else act { command("queue", mapOf("queue" to (room.queue + track).map { it.payload() }, "queueVersion" to room.queueVersion)) }
    }
    fun remove(index: Int) { update { it.copy(selected = it.selected.filterIndexed { i, _ -> i != index }) } }
    fun create(title: String) = act {
        require(state.value.selected.isNotEmpty()) { "Pick your first song." }
        spotify.authorizeForLink()
        val id = UUID.randomUUID().toString()
        command("create", mapOf("queue" to state.value.selected.map { it.payload() }, "notifyFollowers" to false) + (title.trim().takeIf { it.isNotEmpty() }?.let { mapOf("title" to it) } ?: emptyMap()), id)
        openRoom(id)
    }
    fun open(id: String) = act { openRoom(id) }
    private suspend fun openRoom(id: String) {
        val document = db.collection("corusLiveRooms").document(id).get().await()
        val room = LiveRoom.parse(id, document.data ?: error("This room is unavailable."))
        spotify.authorizeForLink()
        normalPlayer.stop()
        if (room.hostId != auth.currentUser?.uid) command("join", mapOf("revision" to room.revision), id)
        update { it.copy(room = room, joined = true, error = null) }
        roomListener?.remove(); chatListener?.remove(); heartbeat?.cancel()
        roomListener = document.reference.addSnapshotListener { snapshot, error ->
            if (error != null) { fail(error); pauseAudio() }
            else if (snapshot?.exists() == true) { update { it.copy(room = LiveRoom.parse(id, snapshot.data ?: emptyMap())) }; synchronize() }
        }
        chatListener = document.reference.collection("messages").orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50).addSnapshotListener { snapshot, error ->
            if (error != null) fail(error) else update { it.copy(messages = snapshot?.documents?.map { d -> (d.getString("displayName") ?: "") to (d.getString("text") ?: "") }?.reversed() ?: emptyList()) }
        }
        heartbeat = viewModelScope.launch { while (isActive) {
            try { if (hosting) command("tick"); val response = command("presence"); update { it.copy(people = (response["people"] as? Number)?.toInt() ?: 1) }; synchronize() }
            catch (e: Exception) { fail(e); pauseAudio() }; delay(5000)
        } }
    }
    private fun pauseAudio() { syncJob?.cancel(); disconnectVoice(); musicKey = null; spotify.pauseImmediately() }
    private fun synchronize() {
        val room = state.value.room ?: return
        val key = listOf(room.id, room.revision, room.startedAt, room.pausedAt, room.voiceMode, room.voiceSession, room.status, room.leaseUntil > System.currentTimeMillis() + offset).joinToString(":")
        if (syncKey == key && syncJob?.isActive == true) return
        syncKey = key
        syncJob?.cancel()
        if (!room.musicShouldPlay(System.currentTimeMillis() + offset)) spotify.pauseImmediately()
        syncJob = viewModelScope.launch { audioLock.withLock {
            try {
                if (room.status == "ended" || room.leaseUntil <= System.currentTimeMillis() + offset) { disconnectVoice(); spotify.pause(); return@withLock }
                if (room.voiceMode == "talk" && room.voiceEnabled) {
                    spotify.pause(); musicKey = null
                    val key = "${room.id}:${room.voiceSession}"
                    if (voiceKey != key) {
                        disconnectVoice()
                        @Suppress("UNCHECKED_CAST") val access = functions.getHttpsCallable("corusLiveVoice").call(mapOf("roomId" to room.id)).await().getData() as Map<String, String>
                        val rtc = LiveKit.create(context); voice = rtc
                        rtc.connect(access.getValue("url"), access.getValue("token"))
                        if (hosting) check(rtc.localParticipant.setMicrophoneEnabled(true)) { "The microphone could not start. Please try Talk again." }
                        voiceKey = key
                    }
                } else {
                    disconnectVoice()
                    if (!room.musicShouldPlay(System.currentTimeMillis() + offset)) { spotify.pause(); return@withLock }
                    val track = room.current ?: return@withLock
                    val key = "${room.id}:${room.revision}:${room.startedAt}"
                    if (musicKey != key) {
                        val spotifyId = if (track.source == "spotify") track.id else {
                            val match = cloudFunctions.spotifyTrackLookup(track.title, track.artist, track.isrc, track.appleMusicId ?: track.id)
                            match.spotifyUri?.substringAfterLast(':') ?: match.webUrl?.let { Regex("track/([a-zA-Z0-9]+)").find(it)?.groupValues?.get(1) }
                                ?: error("This song is unavailable on Spotify.")
                        }
                        spotify.play(spotifyId, track.spotifyURI)
                        spotify.seek(room.position(System.currentTimeMillis() + offset)); musicKey = key
                    } else if (kotlin.math.abs(spotify.positionSeconds.value - room.position(System.currentTimeMillis() + offset)) > 2.5) spotify.seek(room.position(System.currentTimeMillis() + offset))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e); disconnectVoice(); if (hosting && room.voiceMode == "talk") runCatching { command("voiceMode", mapOf("mode" to "music")) } }
        } }
    }
    private fun disconnectVoice() { voice?.disconnect(); voice?.release(); voice = null; voiceKey = null }
    fun mode(value: String) = act { command("voiceMode", mapOf("mode" to value)) }
    fun skip() = act { state.value.room?.let { command("skip", mapOf("revision" to it.revision)) } }
    fun chat(text: String) = act { command("chat", mapOf("text" to text.trim(), "requestId" to UUID.randomUUID().toString())) }
    fun leave(done: () -> Unit) = act { val room = state.value.room; if (room != null) command(if (hosting) "end" else "leave"); cleanup(); update { it.copy(room = null, joined = false) }; done() }
    fun clearError() = update { it.copy(error = null) }
    private fun cleanup() { heartbeat?.cancel(); syncJob?.cancel(); roomListener?.remove(); chatListener?.remove(); disconnectVoice(); spotify.pauseImmediately() }
    override fun onCleared() { cleanup(); discovery?.remove(); super.onCleared() }
}
