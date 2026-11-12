package fm.corus.android.ui.screens.live

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.SpotifyPlaybackService
import fm.corus.android.service.AnalyticsService
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal enum class LiveProgress { TUNING, PREPARING, CUEING, STARTING }

internal data class LiveUiState(
    val uid: String? = null,
    val access: LiveAccess? = null,
    val accessResolved: Boolean = false,
    val discovery: List<LiveRoom> = emptyList(),
    val discoveryCounts: Map<String, Int> = emptyMap(),
    val invites: List<LiveFields> = emptyList(),
    val shownInvite: LiveFields? = null,
    val setup: LiveSetup? = null,
    val room: LiveRoom? = null,
    val ended: LiveRoom? = null,
    val minimized: Boolean = false,
    val muted: Boolean = false,
    val audioReady: Boolean = false,
    val audioError: String? = null,
    val error: String? = null,
    val progress: LiveProgress? = null,
    val listenerCount: Int = 0,
    val messages: List<LiveMessage> = emptyList(),
    val membership: LiveFields = emptyMap(),
    val savedIds: Set<String> = emptySet(),
    val mixedSaves: List<LiveSaveItem>? = null,
    val mixedSavesLoading: Boolean = false,
    val mixedSavesHasMore: Boolean = false,
    val openSavedTrack: LiveSaveItem.Track? = null,
    val openSavedSetlist: LiveRoom? = null,
    val pendingDJs: List<LiveFields> = emptyList(),
    val djRequests: List<LiveFields> = emptyList(),
    val myDJRequest: LiveFields = emptyMap(),
    val queueUpdating: Boolean = false,
    val micEnabled: Boolean = false,
    val voiceLevels: Map<String, Float> = emptyMap(),
    val needsClub: Boolean = false,
    val spotifyInstallNeeded: Boolean = false,
    val linkUpcoming: Boolean = false,
    val positionMs: Long = 0L,
    val confirmPlaybackExit: Boolean = false,
) {
    val hosting get() = room?.host?.uid == uid && room != null
    val dj get() = room?.djs?.firstOrNull { it.text("uid") == uid }
}

@HiltViewModel
internal class LiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LiveRepository,
    private val auth: AuthRepository,
    private val spotify: SpotifyPlaybackService,
    private val player: NowPlayingManager,
    private val voice: LiveVoice,
    private val analytics: AnalyticsService,
    private val subscriptions: fm.corus.android.data.repository.SubscriptionRepository,
) : ViewModel(), DefaultLifecycleObserver {
    private val _state = MutableStateFlow(LiveUiState())
    val state = _state.asStateFlow()
    val profile = auth.userProfile
    private val roomJobs = mutableListOf<Job>()
    private val playbackLock = Mutex()
    private var playbackJob: Job? = null
    private var voiceJob: Job? = null
    private var queueUpdateJob: Job? = null
    private var expectedUri: String? = null
    private var saveLookup: Job? = null
    private var shelfLookup: Job? = null
    private var shelfCursor: LiveFields? = null
    private var confirmedRevision: Long? = null
    private var initialTrackRevision: Long? = null
    private var expiredRevision: Long? = null
    private var offset = 0L
    private var welcomePlaying = false
    private var auditionPlaying = false
    private val playedWelcomes = mutableSetOf<String>()
    private var foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    private var lastTick = 0L
    private var pendingJoin: String? = null
    private var operation: Job? = null
    private var playbackExit: CompletableDeferred<Boolean>? = null
    private var playbackRelease: Job? = null
    private val hiddenRooms = mutableSetOf<String>()
    private var discoverySource: List<LiveRoom> = emptyList()
    private var heartJob: Job? = null
    private var heartsPending = 0
    private var heartRevision = 0L
    private val heartSender = UUID.randomUUID().toString()
    private val uriCache = mutableMapOf<Pair<String, String>, String>()
    private val now get() = System.currentTimeMillis() + offset
    fun serverTime(): Long = now

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        player.livePlaybackExit = ::requestPlaybackExit
        viewModelScope.launch {
            auth.currentUser.map { it?.uid }.distinctUntilChanged().collectLatest { uid ->
                operation?.cancel()
                shelfLookup?.cancel()
                shelfCursor = null
                playbackExit?.complete(false)
                playedWelcomes.clear()
                uriCache.clear()
                offset = 0L
                resetRoom()
                discoverySource = emptyList()
                hiddenRooms.clear()
                pendingJoin = null
                _state.value = LiveUiState(uid = uid)
                if (uid == null) return@collectLatest
                val access = try {
                    repository.access()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                update { copy(access = access, accessResolved = true) }
                if (access == null) return@collectLatest
                coroutineScope {
                    launch {
                        try {
                            val token = FirebaseMessaging.getInstance().token.await()
                            repository.call("registerPush", fields = mapOf(
                                "token" to token, "djInvites" to access.collaboration, "locale" to LiveRepository.locale(),
                            ))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) { }
                    }
                    launch {
                        repository.discovery().catch { error -> showError(error) }.collectLatest { rooms ->
                            discoverySource = rooms
                            refreshDiscovery()
                        }
                    }
                    if (access.collaboration) launch {
                        repository.invites(uid).catch { error -> showError(error) }.collectLatest { invites ->
                            update { copy(invites = invites.filter { it.number("expiresAt") > now }) }
                        }
                    }
                    launch {
                        while (true) {
                            delay(15_000)
                            refreshDiscovery()
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshDiscovery() {
        val rooms = discoverySource.filter { room ->
            room.id !in hiddenRooms && room.active(now) && (room.pausedAt != null || room.deadline > now) && room.host.uid != state.value.uid
        }
        if (!state.value.access?.listenerCounts.orFalse() || rooms.isEmpty()) {
            update { copy(discovery = rooms, discoveryCounts = emptyMap()) }
            return
        }
        val counts = try {
            val result = repository.call("discoveryListenerCounts", fields = mapOf("roomIds" to rooms.map { it.id }))
            result["counts"].liveFields().orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            update { copy(discovery = rooms) }
            return
        }
        update {
            copy(
                discovery = rooms.filter { it.id in counts },
                discoveryCounts = counts.mapValues { (_, count) -> (count as? Number)?.toInt() ?: 0 },
            )
        }
    }

    fun openSetup() {
        if (state.value.access == null) return
        state.value.room?.let {
            if (state.value.hosting) expand()
            return
        }
        runOperation(LiveProgress.PREPARING) {
            val uid = state.value.uid ?: return@runOperation
            val existing = repository.hostedRoom(uid)?.takeIf { it.active(now) }
            if (existing != null) {
                requireSpotify()
                command("tick", existing.id, mapOf("hostActivity" to "foreground"))
                existing.pendingSkip?.let { command("cancelSkip", existing.id, mapOf("skipId" to it)) }
                if (existing.voiceMode == "talk") command("voiceMode", existing.id, mapOf("mode" to "music"))
                mount(repository.room(existing.id) ?: error("This room has ended."))
                return@runOperation
            }
            var access = repository.call("hostAccess").text("access")
            if (access == "upgrade" && subscriptions.hasFullAccessFlow.value) {
                subscriptions.checkStatus()
                access = repository.call("hostAccess").text("access")
                check(access != "upgrade") { "Couldn’t verify your Club access. Try again." }
            }
            if (access == "upgrade") {
                update { copy(needsClub = true) }
                return@runOperation
            }
            val setup = LiveSetup(UUID.randomUUID().toString())
            update { copy(setup = setup) }
            viewModelScope.launch {
                val first = try {
                    repository.lastSong(uid)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (first != null) update {
                    if (this.setup == setup) copy(setup = setup.copy(queue = listOf(first))) else this
                }
            }
        }
    }

    fun editSetup(edit: LiveSetup.() -> LiveSetup) = update { copy(setup = setup?.edit()) }
    fun dismissDiscoveryRoom(id: String) { hiddenRooms += id; update { copy(discovery = discovery.filterNot { it.id == id }) } }
    fun closeSetup() { if (state.value.progress == null) update { copy(setup = null) } }
    fun clearError() = update { copy(error = null, spotifyInstallNeeded = false, linkUpcoming = false) }
    fun clearEnded() = update { copy(ended = null) }
    fun clearClub() = update { copy(needsClub = false) }
    fun retryJoin() { pendingJoin?.let { join(it) } }

    fun start() {
        val setup = state.value.setup ?: return
        val first = setup.queue.firstOrNull() ?: return
        runOperation(LiveProgress.PREPARING) {
            requireSpotify()
            val uid = state.value.uid ?: return@runOperation
            var created = false
            try {
                val fields = buildMap<String, Any?> {
                    put("musicService", "spotify")
                    put("queue", setup.queue.map(LiveTrack::fields))
                    put("notifyFollowers", setup.notifyFollowers)
                    put("voiceProtocol", 2)
                    put("hostMonetizationProtocol", 1)
                    put("locale", LiveRepository.locale())
                    if (state.value.access?.collaboration == true) {
                        put("collaborationProtocol", 1)
                        put("invitees", setup.invitees.map { it.uid })
                        if (state.value.access?.talkRequests == true) put("djTalkRequestProtocol", 1)
                    }
                    setup.title.trim().replace(Regex("\\s+"), " ").takeIf(String::isNotEmpty)?.let { put("title", it) }
                    setup.notificationMessage.trim().takeIf(String::isNotEmpty)?.let { put("notificationMessage", it) }
                    setup.welcome?.let { put("welcomeMessage", repository.uploadWelcome(uid, setup.id, it)) }
                }
                delay(1_000)
                update { copy(progress = LiveProgress.CUEING) }
                prepareTrack(first, { 0L })
                update { copy(progress = LiveProgress.STARTING) }
                val position = (spotify.positionSeconds.value * 1_000).toLong().coerceIn(0, first.duration.toLong() - 1)
                val result = command("create", setup.id, fields + ("initialPlaybackPositionMs" to position))
                created = true
                val room = result["room"].liveFields()?.let { LiveRoom(setup.id, it) } ?: repository.room(setup.id)
                check(room != null && room.host.uid == uid && room.active(now)) { "Couldn’t start the live room. Try again." }
                check(setup.invitees.isEmpty() || room.collaborative) { "DJ invites are unavailable on this build." }
                initialTrackRevision = room.revision
                update { copy(setup = null) }
                mount(room)
                event("host_started")
            } catch (error: Exception) {
                if (created && error !is CancellationException) {
                    try { repository.call("end", setup.id) } catch (_: Exception) { }
                }
                if (state.value.room == null) releasePlayback()
                if (error is FirebaseFunctionsException &&
                    error.details.liveFields()?.text("reason") == "live-host-club-required"
                ) update { copy(needsClub = true) } else throw error
            }
        }
    }

    fun join(id: String, link: Boolean = false, invite: LiveFields? = null) {
        if (state.value.access == null) {
            if (state.value.accessResolved) update { copy(error = "Live is unavailable.") }
            return
        }
        if (state.value.room?.id == id) {
            expand()
            invite?.let { action("acceptDJInvite", mapOf("inviteExpiresAt" to it.number("expiresAt"))) }
            return
        }
        if (operation?.isActive == true) return
        pendingJoin = id
        event("join_attempted", source = if (link) "share_link" else if (invite != null) "dj_invite" else "discovery")
        runOperation(LiveProgress.TUNING) {
            if (link) {
                when (repository.call("resolveShareLink", id).text("status")) {
                    "upcoming" -> {
                        update { copy(linkUpcoming = true) }
                        return@runOperation
                    }
                    "ended" -> {
                        val ended = repository.room(id)
                        update { copy(ended = ended, error = if (ended == null) "This room has ended." else null) }
                        return@runOperation
                    }
                }
            }
            requireSpotify()
            spotify.authorizeForLink()
            check(spotify.isConnected) { "Connect Spotify to hear the music." }
            if (state.value.room != null) exitCurrent(end = state.value.hosting, showSetlist = false)
            val original = repository.room(id) ?: error("This room is unavailable.")
            if (original.host.uid == state.value.uid) {
                command("tick", id, mapOf("hostActivity" to "foreground"))
                mount(repository.room(id) ?: original)
                return@runOperation
            }
            var latest = original
            repeat(3) { attempt ->
                try {
                    command("join", id, mapOf(
                        "revision" to latest.revision, "voiceProtocol" to 1,
                        "collaborationProtocol" to 1, "musicService" to "spotify",
                    ))
                    mount(latest)
                    pendingJoin = null
                    invite?.let { command("acceptDJInvite", id, mapOf("inviteExpiresAt" to it.number("expiresAt"))) }
                    event("join_finished", "joined", if (link) "share_link" else "discovery")
                    return@runOperation
                } catch (error: FirebaseFunctionsException) {
                    if (attempt == 2 || error.code != FirebaseFunctionsException.Code.ABORTED) throw error
                    latest = repository.room(id) ?: throw error
                }
            }
        }
    }

    fun minimize() = update { copy(minimized = true) }
    fun expand() = update { copy(minimized = false) }

    fun claimWelcome(roomId: String): Boolean =
        state.value.room?.takeIf { it.id == roomId }?.welcomeUrl != null && playedWelcomes.add(roomId)

    fun setWelcomePlaying(playing: Boolean) {
        if (welcomePlaying == playing) return
        welcomePlaying = playing
        state.value.room?.let { synchronize(it) }
    }

    fun toggleMute() {
        if (state.value.room == null) return
        update { copy(muted = !muted) }
        state.value.room?.let {
            synchronize(it, force = !state.value.muted)
            synchronizeVoice(it)
        }
    }

    fun toggleMicrophone() {
        val room = state.value.room ?: return
        if (!state.value.hosting && state.value.dj == null) return
        update { copy(micEnabled = !micEnabled) }
        if (room.voiceMode == "talk") synchronizeVoice(room)
    }

    fun setAuditionPlaying(playing: Boolean) {
        if (auditionPlaying == playing) return
        auditionPlaying = playing
        state.value.room?.let { synchronize(it) }
    }

    fun reconnectAudio() {
        val room = state.value.room ?: return
        runOperation {
            requireSpotify()
            spotify.authorizeForLink()
            synchronize(room, force = true)
            synchronizeVoice(room)
        }
    }

    private suspend fun requestPlaybackExit(): Boolean {
        if (state.value.room == null) return false
        playbackExit?.complete(false)
        val answer = CompletableDeferred<Boolean>()
        playbackExit = answer
        update { copy(confirmPlaybackExit = true) }
        try {
            if (!answer.await()) return false
            exitCurrent(end = state.value.hosting, showSetlist = false)
            playbackRelease?.join()
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            showError(error)
            return false
        } finally {
            if (playbackExit === answer) {
                playbackExit = null
                update { copy(confirmPlaybackExit = false) }
            }
        }
    }

    fun confirmPlaybackExit(leave: Boolean) { playbackExit?.complete(leave) }

    fun leave() = runOperation { exitCurrent(end = false, showSetlist = false) }
    fun end() = runOperation { exitCurrent(end = true, showSetlist = true) }

    private suspend fun exitCurrent(end: Boolean, showSetlist: Boolean) {
        val room = state.value.room ?: return
        command(if (end) "end" else "leave", room.id)
        val final = if (showSetlist) {
            try { repository.room(room.id) ?: room } catch (e: CancellationException) { throw e } catch (_: Exception) { room }
        } else null
        if (state.value.room?.id == room.id) closeRoom(final)
        event(if (end) "host_ended" else "listener_left")
    }

    fun action(name: String, fields: LiveFields = emptyMap(), done: ((LiveFields) -> Unit)? = null) {
        val room = state.value.room ?: return
        viewModelScope.launch {
            try {
                val result = command(name, room.id, fields)
                if (state.value.room?.id == room.id) done?.invoke(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (state.value.room?.id == room.id) showError(error)
            }
        }
    }

    fun openInvite(id: String) = runOperation {
        val uid = state.value.uid ?: return@runOperation
        val invite = repository.invite(uid, id)?.takeIf { it.number("expiresAt") > now }
            ?: error("This invite has expired.")
        presentInvite(invite)
    }

    fun presentInvite(invite: LiveFields) {
        event("invite_opened", source = "notification")
        update { copy(shownInvite = invite) }
    }

    fun closeInvite() = update { copy(shownInvite = null) }

    fun inviteDecision(invite: LiveFields, accept: Boolean) {
        closeInvite()
        val id = invite.text("roomId")
        if (accept) join(id, invite = invite)
        else runOperation { command("declineDJInvite", id, mapOf("inviteExpiresAt" to invite.number("expiresAt"))) }
    }

    fun send(text: String, gif: String?, done: () -> Unit) {
        if (text.isBlank() && gif == null) return
        action("chat", buildMap {
            put("requestId", UUID.randomUUID().toString())
            put("text", text.trim())
            gif?.let { put("gifURL", it) }
        }) { done() }
    }

    fun heart() {
        val room = state.value.room?.takeIf { it.status == "playing" && it.current != null } ?: return
        if (heartRevision != room.revision) {
            heartJob?.cancel()
            heartsPending = 0
            heartRevision = room.revision
        }
        heartsPending++
        if (heartJob?.isActive == true) return
        heartJob = viewModelScope.launch {
            delay(240)
            while (heartsPending > 0 && state.value.room?.id == room.id && state.value.room?.revision == room.revision) {
                val count = heartsPending.coerceAtMost(12)
                heartsPending -= count
                try {
                    command("like", room.id, mapOf("revision" to room.revision, "heartCount" to count, "heartSender" to heartSender))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    showError(error)
                    break
                }
            }
        }
    }

    fun save(track: LiveTrack) {
        val room = state.value.room ?: return
        if (saveId(track) in state.value.savedIds) {
            action("unsaveLiveTrack", mapOf("saveId" to saveId(track))) {
                update { copy(savedIds = savedIds - saveId(track), mixedSaves = mixedSaves?.filterNot { it.id == saveId(track) }) }
            }
        } else {
            action("saveLiveTrack", mapOf(
                "playRevision" to (track.playRevision.takeIf { it > 0 } ?: room.revision),
                "trackId" to track.id, "trackSource" to track.song.source.raw,
            )) {
                update { copy(savedIds = savedIds + saveId(track)) }
                if (state.value.mixedSaves != null) loadMixedSaves(reset = true)
                event("song_saved", source = if (state.value.hosting) "host" else "listener")
            }
        }
    }

    fun refreshSaves(tracks: List<LiveTrack>) {
        val uid = state.value.uid ?: return
        if (state.value.access?.saves != true) return
        saveLookup?.cancel()
        saveLookup = viewModelScope.launch {
            val ids = tracks.map(::saveId).distinct()
            val saved = repository.savedIds(uid, ids)
            if (state.value.uid == uid) update { copy(savedIds = (savedIds - ids.toSet()) + saved) }
        }
    }

    fun loadMixedSaves(reset: Boolean = false) {
        val uid = state.value.uid ?: return
        if (state.value.access?.saves != true) return
        if (!reset && (shelfLookup?.isActive == true || (state.value.mixedSaves != null && shelfCursor == null))) return
        shelfLookup?.cancel()
        shelfLookup = viewModelScope.launch {
            update { copy(mixedSavesLoading = true) }
            try {
                val page = repository.mixedSaves(if (reset) null else shelfCursor)
                if (state.value.uid != uid) return@launch
                shelfCursor = page.cursor
                update {
                    val prior = if (reset) emptyList() else mixedSaves.orEmpty()
                    copy(mixedSaves = (prior + page.items).distinctBy { it.id }, mixedSavesHasMore = page.cursor != null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The existing post-only shelf remains usable when mixed saves are unavailable.
            } finally {
                if (state.value.uid == uid) update { copy(mixedSavesLoading = false) }
            }
        }
    }

    fun presentSavedTrack(track: LiveSaveItem.Track) = update { copy(openSavedTrack = track) }
    fun closeSavedTrack() = update { copy(openSavedTrack = null) }
    fun closeSavedSetlist() = update { copy(openSavedSetlist = null) }

    fun showSavedSetlist(track: LiveSaveItem.Track) = runOperation {
        val room = repository.room(track.roomId) ?: error("Setlist unavailable.")
        update { copy(openSavedTrack = null, openSavedSetlist = room) }
    }

    fun removeSavedTrack(track: LiveSaveItem.Track) = runOperation {
        repository.call("unsaveLiveTrack", fields = mapOf("saveId" to track.id))
        update { copy(openSavedTrack = null, savedIds = savedIds - track.id, mixedSaves = mixedSaves?.filterNot { it.id == track.id }) }
    }

    fun updateQueue(queue: List<LiveTrack>, expectedVersion: Long, expectedHosting: Boolean) {
        val snapshot = state.value
        val room = snapshot.room ?: return
        if (queueUpdateJob?.isActive == true) return
        val dj = snapshot.dj
        val version = dj?.number("version") ?: room.queueVersion
        if (expectedVersion != version || expectedHosting != snapshot.hosting || (!snapshot.hosting && dj == null)) {
            update { copy(error = "The queue changed. Try again.") }
            return
        }
        val fields = mapOf(
            "queue" to queue.map(LiveTrack::fields),
            (if (dj == null) "queueVersion" else "version") to version,
        )
        queueUpdateJob = viewModelScope.launch {
            update { copy(queueUpdating = true) }
            try {
                command(if (dj == null) "queue" else "djQueue", room.id, fields)
                withTimeoutOrNull(3_000) {
                    state.first { latest ->
                        latest.room?.id != room.id ||
                            (if (dj == null) latest.room?.queueVersion else latest.dj?.number("version")) != version
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (state.value.room?.id == room.id) showError(error)
            } finally {
                if (state.value.room?.id == room.id) update { copy(queueUpdating = false) }
            }
        }
    }

    fun skip() {
        val room = state.value.room ?: return
        val next = room.queue.firstOrNull()
        if (!state.value.hosting || next == null || room.djs.isNotEmpty() || state.value.access?.confirmedSkip != true) {
            action("skip", mapOf("revision" to room.revision)) { event("host_skipped", source = "host") }
            return
        }
        runOperation {
            val id = UUID.randomUUID().toString()
            command("beginSkip", room.id, mapOf("skipId" to id, "revision" to room.revision, "queueVersion" to room.queueVersion))
            try {
                prepareTrack(next, { 0L })
                val position = (spotify.positionSeconds.value * 1_000).toLong().coerceIn(0, next.duration.toLong() - 1)
                initialTrackRevision = room.revision + 1
                command("completeSkip", room.id, mapOf("skipId" to id, "positionMs" to position))
                event("host_skipped", source = "host")
            } catch (error: Exception) {
                initialTrackRevision = null
                if (error !is CancellationException) {
                    try { command("cancelSkip", room.id, mapOf("skipId" to id)) } catch (_: Exception) { }
                    state.value.room?.let { synchronize(it, force = true) }
                }
                throw error
            }
        }
    }

    suspend fun exportSetlist(id: String) = repository.exportSetlist(id)
    suspend fun previewUrl(track: LiveTrack): String? {
        val song = track.song
        return song.previewUrl ?: player.lookupPreviewUrl(
            song.id, song.name, song.artistName, song.isrc, song.appleMusicId, song.durationMs, song.albumName,
        )
    }

    suspend fun suggestions() = state.value.uid?.let { repository.suggestions(it) } ?: LiveSuggestions()
    suspend fun search(query: String, offset: Int = 0) = repository.search(query, offset)
    suspend fun people(query: String) = state.value.uid?.let { repository.searchPeople(query, it) }.orEmpty()
    suspend fun spotifyPlaylist(link: String) = repository.spotifyPlaylist(link)
    suspend fun gifs(query: String) = repository.gifs(query)
    suspend fun share(id: String, setup: Boolean): String =
        if (setup) command("reserveShareLink", id).text("shareUrl") else "https://corus.fm/live/$id"

    private fun mount(room: LiveRoom) {
        roomJobs.forEach(Job::cancel)
        roomJobs.clear()
        queueUpdateJob?.cancel()
        lastTick = 0
        update {
            copy(
                room = room, ended = null, minimized = false, muted = false, messages = emptyList(),
                listenerCount = 0, membership = emptyMap(), audioError = null, queueUpdating = false,
            )
        }
        observe(room.id)
        refreshSaves(listOfNotNull(room.current))
        synchronize(room)
        synchronizeVoice(room)
    }

    private fun observe(id: String) {
        val uid = state.value.uid ?: return
        val initialHost = state.value.hosting
        roomJobs += viewModelScope.launch {
            repository.observeRoom(id).catch { error -> showError(error) }.collect { next ->
                if (state.value.room?.id != id) return@collect
                if (next == null || next.status == "ended") {
                    closeRoom(next)
                    return@collect
                }
                val previous = state.value.room
                update { copy(room = next) }
                if (previous?.host?.uid != next.host.uid) observeHostRecords(next, uid)
                if (previous?.revision != next.revision) refreshSaves(listOfNotNull(next.current))
                if (previous?.revision != next.revision || previous.status != next.status ||
                    previous.pausedAt != next.pausedAt || previous.startedAt != next.startedAt ||
                    previous.pendingSkip != next.pendingSkip
                ) synchronize(next)
                if (previous?.voiceSession != next.voiceSession || previous?.voiceMode != next.voiceMode ||
                    previous?.host?.uid != next.host.uid || previous?.djs != next.djs
                ) synchronizeVoice(next)
            }
        }
        roomJobs += viewModelScope.launch {
            repository.messages(id).catch { error -> showError(error) }.collect { messages ->
                if (state.value.room?.id == id) update { copy(messages = messages) }
            }
        }
        roomJobs += viewModelScope.launch {
            repository.membership(id, uid).catch { error -> showError(error) }.collect { fields ->
                if (state.value.room?.id != id) return@collect
                if (fields.flag("removed")) {
                    closeRoom(null)
                    update { copy(error = "You are no longer in this room.") }
                } else update { copy(membership = fields) }
            }
        }
        roomJobs += viewModelScope.launch {
            repository.ownRequest(id, "djRequests", uid).catch { }.collect { request ->
                if (state.value.room?.id == id) update { copy(myDJRequest = request) }
            }
        }
        if (initialHost) state.value.room?.let { observeHostRecords(it, uid) }
        roomJobs += viewModelScope.launch {
            while (state.value.room?.id == id) {
                val room = state.value.room ?: break
                val serverNow = now
                if (!room.active(serverNow)) {
                    closeRoom(room)
                    break
                }
                update { copy(positionMs = room.position(serverNow)) }
                if (room.status == "playing" && room.pausedAt == null && room.deadline <= serverNow &&
                    expiredRevision != room.revision
                ) {
                    expiredRevision = room.revision
                    if (expectedUri != null && room.pendingSkip == null) spotify.pauseImmediately()
                }
                if (foreground && state.value.hosting &&
                    (serverNow - lastTick >= 8_000 || (room.deadline <= serverNow &&
                        room.pausedAt == null && serverNow - lastTick > 900))
                ) {
                    lastTick = serverNow
                    maintain(room, "tick", mapOf("hostActivity" to "foreground"))
                }
                delay(500)
            }
        }
        roomJobs += viewModelScope.launch {
            while (state.value.room?.id == id) {
                val room = state.value.room ?: break
                maintain(room, "presence", mapOf("hostHandoffReady" to foreground))
                if (!state.value.muted && foreground) synchronize(room)
                delay(15_000)
            }
        }
    }

    private var hostRecords: Job? = null

    private fun observeHostRecords(room: LiveRoom, uid: String) {
        hostRecords?.cancel()
        update { copy(pendingDJs = emptyList(), djRequests = emptyList()) }
        if (room.host.uid != uid || !room.collaborative) return
        hostRecords = viewModelScope.launch {
            launch {
                repository.roomRecords(room.id, "collaboration").catch { }.collect { records ->
                    update { copy(pendingDJs = records.firstOrNull { it.text("uid") == "host" }?.rows("pendingDJs").orEmpty()) }
                }
            }
            launch {
                repository.roomRecords(room.id, "djRequests").catch { }.collect { records ->
                    update { copy(djRequests = records.filter { it.text("status") == "pending" }) }
                }
            }
        }
    }

    private suspend fun maintain(room: LiveRoom, action: String, fields: LiveFields) {
        try {
            val result = command(action, room.id, fields)
            if (action == "presence" && state.value.room?.id == room.id) {
                update { copy(listenerCount = result.number("listenerCount").toInt()) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseFunctionsException) {
            if (state.value.room?.id == room.id && error.code == FirebaseFunctionsException.Code.PERMISSION_DENIED) {
                closeRoom(null)
                showError(error)
            }
        } catch (_: Exception) { }
    }

    private fun synchronize(room: LiveRoom, force: Boolean = false) {
        if (state.value.room?.id != room.id || (room.pendingSkip != null && state.value.hosting)) return
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            try {
                playbackLock.withLock {
                    val track = room.current
                    if (state.value.muted || welcomePlaying || auditionPlaying || track == null || !room.musicShouldPlay(now)) {
                        if (expectedUri != null) spotify.pause()
                        return@withLock
                    }
                    if (!foreground && !spotify.isConnected) return@withLock
                    val uri = resolve(track)
                    if (uri == null) {
                        if (expectedUri != null) spotify.pause()
                        update { copy(audioReady = false, audioError = "This song is unavailable on Spotify.") }
                        return@withLock
                    }
                    val previousRevision = confirmedRevision
                    if (initialTrackRevision == room.revision && expectedUri == uri) {
                        initialTrackRevision = null
                    } else if (!spotify.isConnected || !matchesSpotifyUri(uri) || confirmedRevision != room.revision) {
                        prepareTrack(track, { room.position(now) }, uri)
                    } else {
                        if (!spotify.isPlaying.value) check(spotify.resume()) { "Couldn’t resume Spotify." }
                        val difference = kotlin.math.abs(spotify.positionSeconds.value * 1_000 - room.position(now))
                        if (force || difference > 1_800) {
                            check(spotify.seek(room.position(now) / 1_000.0)) { "Couldn’t sync Spotify." }
                            if (difference > 1_800) analytics.logEvent("live_sync_corrected", mapOf(
                                "source" to "spotify", "milliseconds" to difference.toInt(),
                            ))
                        }
                    }
                    confirmedRevision = room.revision
                    if (previousRevision == null && !state.value.hosting) event("playback_started")
                    update { copy(audioReady = true, audioError = null) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                update { copy(audioReady = false, audioError = "Couldn’t sync Spotify. Try again.") }
            }
        }
    }

    private fun matchesSpotifyUri(uri: String): Boolean =
        spotify.currentTrackUri.value?.let { SpotifyPlaybackService.spotifyURIsMatch(it, uri) } == true

    private suspend fun resolve(track: LiveTrack): String? {
        val key = track.song.source.raw to track.id
        return uriCache[key] ?: repository.spotifyUri(track)?.also { uriCache[key] = it }
    }

    private suspend fun prepareTrack(track: LiveTrack, position: () -> Long, knownUri: String? = null) {
        playbackRelease?.join()
        val uri = knownUri ?: resolve(track) ?: error("This song is unavailable on Spotify.")
        if (expectedUri == null) {
            expectedUri = uri
            player.beginLiveSpotifyPlayback()
        } else expectedUri = uri
        if (!spotify.isConnected || !matchesSpotifyUri(uri) || !spotify.isPlaying.value) {
            spotify.play(uri.substringAfterLast(':'), uri, replaceQueue = false)
            spotify.refreshState()
            check(spotify.currentTrackUri.value?.let { SpotifyPlaybackService.spotifyURIsMatch(it, uri) } == true &&
                spotify.isPlaying.value) { "Couldn’t start Spotify." }
        }
        val target = position().coerceIn(0, track.duration.toLong() - 1)
        val observed = spotify.positionSeconds.value * 1_000
        if (target > 250 || observed > 750) {
            check(spotify.seek(target / 1_000.0)) { "Couldn’t sync Spotify." }
        }
    }

    private fun synchronizeVoice(room: LiveRoom) {
        voiceJob?.cancel()
        val session = room.voiceSession
        if (room.voiceMode != "talk" || session == null) {
            voice.disconnect()
            update { copy(micEnabled = false, voiceLevels = emptyMap()) }
            return
        }
        if (state.value.muted) {
            voice.disconnect()
            update { copy(voiceLevels = emptyMap()) }
            return
        }
        val publish = state.value.micEnabled && (state.value.hosting || state.value.dj != null)
        voiceJob = viewModelScope.launch {
            try {
                voice.connect(room.id, session, publish)
                while (state.value.room?.voiceSession == session) {
                    update { copy(voiceLevels = voice.levels()) }
                    delay(100)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                voice.disconnect()
                update { copy(micEnabled = false, voiceLevels = emptyMap(), audioError = "Couldn’t connect to Live voice.") }
            }
        }
    }

    private suspend fun command(action: String, id: String, fields: LiveFields = emptyMap()): LiveFields {
        val before = System.currentTimeMillis()
        val result = repository.call(action, id, fields)
        val elapsed = System.currentTimeMillis() - before
        val server = result.number("serverNow")
        if (server > 0 && elapsed in 0..5_000) offset = server - (before + elapsed / 2)
        return result
    }

    private fun runOperation(progress: LiveProgress? = null, block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            update { copy(progress = progress, error = null) }
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(error)
            } finally {
                update { copy(progress = null) }
            }
        }
    }

    private fun requireSpotify() {
        if (SpotifyPlaybackService.isSpotifyAppInstalled(context)) return
        update { copy(spotifyInstallNeeded = true) }
        error("Spotify is required to use Live.")
    }

    private fun showError(error: Throwable) = update {
        copy(error = error.localizedMessage ?: "Something went wrong. Try again.")
    }

    private fun closeRoom(ended: LiveRoom?) {
        resetRoom()
        update {
            copy(room = null, ended = ended, minimized = false, messages = emptyList(),
                audioReady = false, audioError = null, micEnabled = false, voiceLevels = emptyMap(), membership = emptyMap(),
                pendingDJs = emptyList(), djRequests = emptyList(), myDJRequest = emptyMap(), queueUpdating = false)
        }
    }

    private fun resetRoom() {
        roomJobs.forEach(Job::cancel)
        roomJobs.clear()
        hostRecords?.cancel()
        playbackJob?.cancel()
        voiceJob?.cancel()
        queueUpdateJob?.cancel()
        heartJob?.cancel()
        saveLookup?.cancel()
        heartsPending = 0
        welcomePlaying = false
        auditionPlaying = false
        voice.disconnect()
        releasePlayback()
    }

    private fun releasePlayback() {
        if (expectedUri != null) {
            playbackRelease = player.endLiveSpotifyPlayback()
        }
        expectedUri = null
        confirmedRevision = null
        initialTrackRevision = null
        expiredRevision = null
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        val room = state.value.room ?: return
        viewModelScope.launch {
            if (state.value.hosting) maintain(room, "tick", mapOf("hostActivity" to "foreground"))
            maintain(room, "presence", mapOf("hostHandoffReady" to true))
        }
        synchronize(room)
        synchronizeVoice(room)
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        val room = state.value.room ?: return
        viewModelScope.launch {
            if (state.value.hosting) {
                val fields = buildMap {
                    put("hostActivity", "background")
                    confirmedRevision?.let { put("spotifyPlaybackRevision", it) }
                }
                maintain(room, "tick", fields)
            } else maintain(room, "presence", mapOf("hostHandoffReady" to false))
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        player.livePlaybackExit = null
        playbackExit?.complete(false)
        resetRoom()
    }

    fun event(name: String, outcome: String? = null, source: String = "spotify") {
        analytics.logEvent("live_$name", buildMap {
            put("source", source)
            outcome?.let { put("outcome", it) }
        })
    }

    private inline fun update(crossinline change: LiveUiState.() -> LiveUiState) =
        _state.update { it.change() }

    companion object {
        fun saveId(track: LiveTrack): String = MessageDigest.getInstance("SHA-256")
            .digest("${track.song.source.raw}:${track.id}".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

private fun Boolean?.orFalse(): Boolean = this == true
