package fm.corus.android.ui.screens.live

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.repeatOnLifecycle
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.model.MusicService
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.domain.AppleMusicLiveQueue
import fm.corus.android.domain.AppleMusicPlayback
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.toQueuedTrack
import fm.corus.android.domain.MusicServicePreference
import fm.corus.android.domain.SpotifyPlaybackError
import fm.corus.android.domain.SpotifyPlaybackService
import fm.corus.android.service.AnalyticsService
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

internal enum class LiveProgress { TUNING, PREPARING, CUEING, STARTING_SPOTIFY, STARTING_APPLE_MUSIC, STARTING }

internal data class LiveUiState(
    val uid: String? = null,
    val access: LiveAccess? = null,
    val accessResolved: Boolean = false,
    val cachedMenuAccess: Boolean = false,
    val discovery: List<LiveRoom> = emptyList(),
    val discoveryCounts: Map<String, Int> = emptyMap(),
    val invites: List<LiveFields> = emptyList(),
    val shownInvite: LiveFields? = null,
    val setup: LiveSetup? = null,
    val checkingSetupAccess: Boolean = false,
    val songSuggestions: LiveSuggestions? = null,
    val songSuggestionsFailed: Boolean = false,
    val room: LiveRoom? = null,
    val ended: LiveRoom? = null,
    val minimized: Boolean = false,
    val pendingChatEnabled: Boolean? = null,
    val recoverableHostRoom: LiveRoom? = null,
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
    val mixedSavesFailed: Boolean = false,
    val openSavedSetlist: LiveRoom? = null,
    val pendingDJs: List<LiveFields> = emptyList(),
    val djRequests: List<LiveFields> = emptyList(),
    val myDJRequest: LiveFields = emptyMap(),
    val queueUpdating: Boolean = false,
    val effectSelection: LiveEffectSelection = LiveEffectSelection(),
    val micEnabled: Boolean = false,
    val voiceLevels: Map<String, Float> = emptyMap(),
    val needsClub: Boolean = false,
    val spotifyInstallNeeded: Boolean = false,
    val hostProviderPrompt: Boolean = false,
    val hostProviderRequiredForSetup: Boolean = false,
    val connectingProvider: MusicService? = null,
    val hostProviderHint: String? = null,
    val linkUpcoming: Boolean = false,
    val confirmPlaybackExit: Boolean = false,
) {
    val hosting get() = room?.host?.uid == uid && room != null
    val miniPlayerRoom get() = if (room == null && setup == null && ended == null) recoverableHostRoom
        else room?.takeIf { minimized }

    fun canSkipTrack(room: LiveRoom, queue: List<LiveTrack> = room.queue): Boolean =
        room.pendingSkip == null && !queueUpdating && room.current != null &&
            room.pausedAt == null && room.status == "playing" &&
            if (hosting) {
                (room.collaborative || queue.isNotEmpty()) &&
                    !(room.djs.isEmpty() && queue.isNotEmpty() && access?.confirmedSkip == true && muted)
            } else dj != null && room.currentDJ?.uid == uid

    val dj get() = room?.djs?.firstOrNull { it.text("uid") == uid }
}

@HiltViewModel
internal class LiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LiveRepository,
    private val auth: AuthRepository,
    private val spotify: SpotifyPlaybackService,
    private val appleMusic: AppleMusicPlayback,
    private val player: NowPlayingManager,
    private val musicService: MusicServicePreference,
    private val voice: LiveVoice,
    private val analytics: AnalyticsService,
    private val subscriptions: fm.corus.android.data.repository.SubscriptionRepository,
    private val saveChangedEvent: fm.corus.android.domain.SaveChangedEvent,
) : ViewModel(), DefaultLifecycleObserver {
    private val menuAccessPrefs = context.getSharedPreferences("corus_live_menu_access", Context.MODE_PRIVATE)
    private val accessLock = Mutex()
    private val _state = MutableStateFlow(
        LiveUiState(
            uid = auth.currentUser.value?.uid,
            cachedMenuAccess = auth.currentUser.value?.uid?.let { menuAccessPrefs.getBoolean(it, false) } == true,
        ),
    )
    val state = _state.asStateFlow()
    val profile = auth.userProfile
    private val roomJobs = mutableListOf<Job>()
    private val playbackLock = Mutex()
    private var playbackJob: Job? = null
    private var voiceJob: Job? = null
    private var queueUpdateJob: Job? = null
    private var effectSaveJob: Job? = null
    private var effectConfirmationJob: Job? = null
    private var expectedUri: String? = null
    private var liveProvider: MusicService? = null
    private var applePlayer: Player? = null
    private data class AppleQueueTarget(
        val player: AppleMusicLiveQueue,
        val roomId: String,
        val revision: Long,
        val queueVersion: Long,
        val track: LiveTrack,
    )

    private var appleQueueTarget: AppleQueueTarget? = null
    private var appleQueueLookup: Job? = null
    private var appleAdvanceConfirmed: AppleQueueTarget? = null
    private var appleTrackKey: Pair<TrackSource, String>? = null
    private var sourcePlayer: ExoPlayer? = null
    private var sourceTrackId: String? = null
    private var ownsLivePlayback = false
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
    private var songSuggestionsJob: Job? = null
    private var songSuggestionsFreshUntil = 0L
    private var firstSongLookup: Deferred<LiveTrack?>? = null
    private var firstSongFreshUntil = 0L
    private val now get() = System.currentTimeMillis() + offset
    fun serverTime(): Long = now

    init {
        viewModelScope.launch {
            saveChangedEvent.events.collect { change ->
                if (state.value.mixedSaves == null) return@collect
                if (change.isSaved) loadMixedSaves(reset = true)
                else update {
                    copy(mixedSaves = mixedSaves?.filterNot { it is LiveSaveItem.Post && it.id == change.postId })
                }
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        player.livePlaybackExit = ::requestPlaybackExit
        viewModelScope.launch {
            auth.currentUser.map { it?.uid }.distinctUntilChanged().collectLatest { uid ->
                operation?.cancel()
                shelfLookup?.cancel()
                songSuggestionsJob?.cancel()
                songSuggestionsJob = null
                songSuggestionsFreshUntil = 0L
                firstSongLookup?.cancel()
                firstSongLookup = null
                firstSongFreshUntil = 0L
                shelfCursor = null
                playbackExit?.complete(false)
                playedWelcomes.clear()
                uriCache.clear()
                offset = 0L
                resetRoom()
                discoverySource = emptyList()
                hiddenRooms.clear()
                pendingJoin = null
                _state.value = LiveUiState(
                    uid = uid,
                    cachedMenuAccess = uid?.let { menuAccessPrefs.getBoolean(it, false) } == true,
                )
                if (uid != null) resolveAccess(uid)
            }
        }
        viewModelScope.launch {
            state.map { it.uid to it.access }.distinctUntilChanged().collectLatest { (uid, access) ->
                if (uid == null || access == null) return@collectLatest
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
        viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.map {
                    it.uid?.takeIf { _ -> it.access != null && it.room == null && it.setup == null && it.ended == null }
                }.distinctUntilChanged().collectLatest { uid ->
                    if (uid == null) return@collectLatest
                    try {
                        val hosted = repository.hostedRoom(uid)?.takeIf { it.active(now) }
                        currentCoroutineContext().ensureActive()
                        update { copy(recoverableHostRoom = hosted) }
                        if (hosted != null) {
                            repository.observeRoom(hosted.id).collectLatest { room ->
                                update { copy(recoverableHostRoom = room?.takeIf { it.host.uid == uid && it.active(now) }) }
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        update { copy(recoverableHostRoom = null) }
                    }
                }
            }
        }
    }

    private suspend fun resolveAccess(uid: String): LiveAccess? = accessLock.withLock {
        if (state.value.uid != uid || auth.currentUser.value?.uid != uid) return@withLock null
        state.value.access?.let { return@withLock it }
        val access = try {
            repository.access()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (state.value.uid == uid) update { copy(accessResolved = true) }
            return@withLock null
        }
        currentCoroutineContext().ensureActive()
        if (state.value.uid != uid || auth.currentUser.value?.uid != uid) return@withLock null
        val eligible = access != null
        menuAccessPrefs.edit().putBoolean(uid, eligible).apply()
        update { copy(access = access, accessResolved = true, cachedMenuAccess = eligible) }
        access
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
        val current = state.value
        val uid = current.uid ?: return
        if (current.setup != null) return
        if (current.room != null) {
            if (current.hosting) expand()
            return
        }
        if (operation?.isActive == true) return
        val lookup = preloadFirstSong() ?: return
        val setup = LiveSetup(UUID.randomUUID().toString(), loadingFirstSong = true)
        update { copy(setup = setup, checkingSetupAccess = true) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val first = lookup.await()
            update {
                val draft = this.setup
                if (this.uid == uid && draft?.id == setup.id && draft.loadingFirstSong) {
                    copy(setup = draft.copy(queue = listOfNotNull(first), loadingFirstSong = false))
                } else this
            }
        }
        runOperation {
            try {
                check(resolveAccess(uid) != null) { "Live is unavailable." }
                val existing = current.recoverableHostRoom?.takeIf { it.host.uid == uid && it.active(now) }
                if (existing != null) {
                    if (resumeHostedRoom(existing)) {
                        update { copy(setup = null, checkingSetupAccess = false) }
                        return@runOperation
                    }
                    update { copy(recoverableHostRoom = null) }
                }
                var access = repository.call("hostAccess").text("access")
                if (access == "upgrade" && subscriptions.hasFullAccessFlow.value) {
                    subscriptions.checkStatus()
                    access = repository.call("hostAccess").text("access")
                    check(access != "upgrade") { "Couldn’t verify your Club access. Try again." }
                }
                if (access == "upgrade") {
                    update { copy(setup = null, checkingSetupAccess = false, needsClub = true) }
                    return@runOperation
                }
                val mustConnectProvider = selectedLiveProvider() == null
                update {
                    copy(
                        checkingSetupAccess = false,
                        hostProviderPrompt = mustConnectProvider,
                        hostProviderRequiredForSetup = mustConnectProvider,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update { copy(setup = null, checkingSetupAccess = false) }
                throw error
            }
        }
    }

    private suspend fun resumeHostedRoom(existing: LiveRoom): Boolean {
        try {
            command("tick", existing.id, mapOf("hostActivity" to "foreground"))
        } catch (error: FirebaseFunctionsException) {
            if (error.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION ||
                error.code == FirebaseFunctionsException.Code.NOT_FOUND
            ) return false
            throw error
        }
        existing.pendingSkip?.let { command("cancelSkip", existing.id, mapOf("skipId" to it)) }
        if (existing.voiceMode == "talk") command("voiceMode", existing.id, mapOf("mode" to "music"))
        val room = repository.room(existing.id)?.takeIf { it.active(now) } ?: return false
        liveProvider = selectedLiveProvider()
        mount(room)
        return true
    }

    fun preloadFirstSong(): Deferred<LiveTrack?>? {
        val uid = state.value.uid ?: return null
        if (auth.currentUser.value?.uid != uid) return null
        firstSongLookup?.let { lookup ->
            if (lookup.isActive || (!lookup.isCancelled && SystemClock.elapsedRealtime() < firstSongFreshUntil)) {
                return lookup
            }
        }
        return viewModelScope.async {
            try {
                val first = withTimeoutOrNull(10_000L) { repository.lastSong(uid) }
                firstSongFreshUntil = SystemClock.elapsedRealtime() + if (first != null) 30_000L else 2_000L
                first
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                firstSongFreshUntil = SystemClock.elapsedRealtime() + 2_000L
                null
            }
        }.also {
            firstSongLookup = it
        }
    }

    fun editSetup(edit: LiveSetup.() -> LiveSetup) = update { copy(setup = setup?.edit()) }
    fun dismissDiscoveryRoom(id: String) { hiddenRooms += id; update { copy(discovery = discovery.filterNot { it.id == id }) } }
    fun closeSetup() {
        val current = state.value
        if (current.progress != null || current.connectingProvider != null) return
        if (current.checkingSetupAccess) operation?.cancel()
        update {
            copy(setup = null, checkingSetupAccess = false, hostProviderPrompt = false,
                hostProviderRequiredForSetup = false, hostProviderHint = null)
        }
    }
    fun clearError() = update { copy(error = null, spotifyInstallNeeded = false, linkUpcoming = false) }
    fun clearEnded() = update { copy(ended = null) }
    fun clearClub() = update { copy(needsClub = false) }
    fun retryJoin() { pendingJoin?.let { join(it) } }

    fun dismissHostProviderPrompt() {
        if (state.value.hostProviderRequiredForSetup) closeSetup()
        else update { copy(hostProviderPrompt = false, hostProviderHint = null) }
    }

    private fun selectedLiveProvider(): MusicService? = when (musicService.current.value) {
        MusicService.APPLE_MUSIC -> MusicService.APPLE_MUSIC.takeIf {
            AppleMusicPlayback.isAvailable() && appleMusic.isConnected()
        }
        MusicService.SPOTIFY -> MusicService.SPOTIFY.takeIf {
            SpotifyPlaybackService.isSpotifyAppInstalled(context)
        }
        else -> null
    }

    private suspend fun authorizeLiveProvider(provider: MusicService): String? = when (provider) {
        MusicService.APPLE_MUSIC -> if (appleMusic.isConnected() || appleMusic.connect()) null
            else "Connect an Apple Music subscription and try again."
        MusicService.SPOTIFY -> {
            requireSpotify()
            spotify.authorizeForLink()
            spotifyCapabilityError()
        }
        else -> "Apple Music or Spotify Premium is required for Live."
    }

    private suspend fun spotifyCapabilityError(): String? = when (spotify.canPlayOnDemandForLive()) {
        true -> null
        false -> "Use a Spotify Premium account and try connecting again."
        null -> "Couldn’t verify Spotify playback. Open Spotify and try connecting again."
    }

    fun connectHostProvider(provider: MusicService) {
        val current = state.value
        if (!current.hostProviderPrompt || current.connectingProvider != null || operation?.isActive == true) return
        runOperation {
            update { copy(connectingProvider = provider, hostProviderHint = null) }
            try {
                val hint = authorizeLiveProvider(provider)
                if (hint == null) {
                    musicService.syncToFirestore(provider)
                    currentCoroutineContext().ensureActive()
                    update { copy(hostProviderPrompt = false, hostProviderRequiredForSetup = false) }
                } else update { copy(hostProviderHint = hint) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error !is SpotifyPlaybackError.AuthorizationCancelled) {
                    update { copy(hostProviderHint = "Couldn’t connect to ${provider.displayLabel}. Try again.") }
                }
            } finally {
                update { copy(connectingProvider = null) }
            }
        }
    }

    fun start() {
        val current = state.value
        if (current.checkingSetupAccess || current.room != null || operation?.isActive == true) return
        val setup = current.setup ?: return
        val first = setup.queue.firstOrNull() ?: return
        val provider = selectedLiveProvider()
        if (provider == null) {
            update { copy(hostProviderPrompt = true, hostProviderHint = null) }
            return
        }
        dismissHostProviderPrompt()
        runOperation(LiveProgress.PREPARING) {
            val uid = state.value.uid ?: return@runOperation
            val existing = repository.hostedRoom(uid)?.takeIf { it.active(now) }
            currentCoroutineContext().ensureActive()
            if (existing != null && resumeHostedRoom(existing)) return@runOperation
            var created = false
            liveProvider = provider
            var checkingProvider = !first.playsFromSource
            try {
                val fields = buildMap<String, Any?> {
                    put("musicService", provider.value)
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
                prepareTrack(first, { 0L }, forceSpotifyPlay = true, onStartingProvider = {
                    update { copy(progress = if (provider == MusicService.APPLE_MUSIC)
                        LiveProgress.STARTING_APPLE_MUSIC else LiveProgress.STARTING_SPOTIFY) }
                })
                if (checkingProvider && provider == MusicService.SPOTIFY) {
                    val hint = spotifyCapabilityError()
                    if (hint != null) {
                        releasePlayback()
                        update { copy(hostProviderPrompt = true, hostProviderHint = hint) }
                        return@runOperation
                    }
                }
                checkingProvider = false
                update { copy(progress = LiveProgress.STARTING) }
                val position = livePlaybackPosition(first)
                val result = command("create", setup.id, fields + ("initialPlaybackPositionMs" to position))
                created = true
                val room = result["room"].liveFields()?.let { LiveRoom(setup.id, it) } ?: repository.room(setup.id)
                check(room != null && room.host.uid == uid && room.active(now)) { "Couldn’t start the live room. Try again." }
                check(setup.invitees.isEmpty() || room.collaborative) { "DJ invites are unavailable on this build." }
                initialTrackRevision = room.revision
                mount(room)
                event("host_started")
            } catch (error: Exception) {
                if (created && error !is CancellationException) {
                    try { repository.call("end", setup.id) } catch (_: Exception) { }
                }
                if (state.value.room == null) releasePlayback()
                if (error is CancellationException) throw error
                if (checkingProvider && (error is SpotifyPlaybackError || provider == MusicService.APPLE_MUSIC)) {
                    update {
                        copy(hostProviderPrompt = true, hostProviderHint = if (error is SpotifyPlaybackError.AuthorizationCancelled) null
                            else if (provider == MusicService.APPLE_MUSIC) error.message ?: "Couldn’t start Apple Music. Try again."
                            else "Couldn’t connect to Spotify. Open Spotify, check that you’re signed in to Premium, then try again.")
                    }
                } else if (error is FirebaseFunctionsException &&
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
            val original = repository.room(id) ?: error("This room is unavailable.")
            if (state.value.room != null) exitCurrent(end = state.value.hosting, showSetlist = false)
            liveProvider = selectedLiveProvider()
            if (liveProvider == MusicService.SPOTIFY && original.current?.playsFromSource != true) {
                try {
                    spotify.authorizeForLink()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: SpotifyPlaybackError) {
                    liveProvider = null
                }
            }
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
                        "collaborationProtocol" to 1, "musicService" to liveProvider?.value,
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

    fun openMiniPlayer() {
        if (state.value.room != null) expand()
        else if (state.value.miniPlayerRoom != null) openSetup()
    }

    suspend fun miniPlayerHostProfile(uid: String) = repository.chatAuthors(listOf(uid))[uid]?.profile

    fun heartFromMiniPlayer(roomId: String, revision: Long) {
        val current = state.value
        val room = current.room ?: return
        if (!current.minimized || !current.audioReady || room.id != roomId || room.revision != revision) return
        if (room.canLikeFromMiniPlayer(current.uid, now)) heart()
    }

    fun skipFromMiniPlayer(roomId: String, revision: Long) {
        val current = state.value
        val room = current.room ?: return
        if (current.minimized && room.id == roomId && room.revision == revision &&
            room.miniPlayerTrack(now) != null && current.canSkipTrack(room)
        ) skip()
    }

    fun claimWelcome(roomId: String): Boolean =
        state.value.room?.takeIf { it.id == roomId }?.welcomeUrl != null && playedWelcomes.add(roomId)

    fun setWelcomePlaying(playing: Boolean) {
        if (welcomePlaying == playing) return
        welcomePlaying = playing
        if (playing) sourcePlayer?.pause()
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
        if (playing) sourcePlayer?.pause()
        state.value.room?.let { synchronize(it) }
    }

    fun connectListenerProvider(provider: MusicService) {
        val room = state.value.room ?: return
        if (state.value.connectingProvider != null || operation?.isActive == true) return
        runOperation {
            update { copy(connectingProvider = provider, audioError = null) }
            try {
                val hint = authorizeLiveProvider(provider)
                if (hint != null) {
                    update { copy(audioError = hint) }
                    return@runOperation
                }
                val latest = state.value.room?.takeIf { it.id == room.id } ?: return@runOperation
                liveProvider = provider
                musicService.syncToFirestore(provider)
                currentCoroutineContext().ensureActive()
                state.value.room?.takeIf { it.id == latest.id }?.let {
                    synchronize(it, force = true)?.join()
                    synchronizeVoice(it)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (state.value.room?.id == room.id) {
                    update { copy(audioError = "Couldn’t connect to ${provider.displayLabel}. Try again.") }
                }
            } finally {
                update { copy(connectingProvider = null) }
            }
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

    fun toggleRoomEffect(effect: String) {
        val room = state.value.room ?: return
        if (!state.value.hosting) return
        update { copy(effectSelection = effectSelection.toggle(effect, room.effect)) }
        if (effectSaveJob?.isActive == true) return

        effectSaveJob = viewModelScope.launch {
            while (state.value.room?.id == room.id && state.value.hosting) {
                val requested = state.value.effectSelection.requested ?: break
                try {
                    command("effect", room.id, mapOf("effect" to requested))
                    currentCoroutineContext().ensureActive()
                    update { copy(effectSelection = effectSelection.saved(requested, this.room?.effect.orEmpty())) }
                    effectConfirmationJob?.cancel()
                    val confirmation = state.value.effectSelection.awaitingSnapshot
                    if (confirmation != null) {
                        effectConfirmationJob = viewModelScope.launch {
                            delay(5_000)
                            update { copy(effectSelection = effectSelection.observe(confirmation)) }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    update { copy(effectSelection = effectSelection.finished(requested)) }
                    if (state.value.effectSelection.requested == null) showError(error)
                }
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

    suspend fun renameRoom(roomId: String, title: String) {
        require(state.value.room?.id == roomId && state.value.hosting)
        command("renameRoom", roomId, mapOf("title" to title))
        withTimeoutOrNull(3_000) {
            state.first { it.room?.id != roomId || it.room.customTitle == title }
        }
    }

    fun setChatEnabled(enabled: Boolean) {
        val current = state.value
        val room = current.room ?: return
        if (!current.hosting || current.pendingChatEnabled != null || room.chatEnabled == enabled) return
        update { copy(pendingChatEnabled = enabled) }
        viewModelScope.launch {
            try {
                command("setChatEnabled", room.id, mapOf("enabled" to enabled))
                withTimeoutOrNull(3_000) {
                    state.first { it.room?.id != room.id || !it.hosting || it.room.chatEnabled == enabled }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (state.value.room?.id == room.id) showError(error)
            } finally {
                update { if (this.room?.id == room.id) copy(pendingChatEnabled = null) else this }
            }
        }
    }

    fun send(text: String, gif: String?, done: () -> Unit) {
        if (state.value.room?.chatEnabled != true || (text.isBlank() && gif == null)) return
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
                update { copy(savedIds = savedIds - saveId(track), mixedSaves = mixedSaves?.filterNot { it is LiveSaveItem.Track && it.id == saveId(track) }) }
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

    fun loadMixedSaves(reset: Boolean = false): Job? {
        val uid = state.value.uid ?: return null
        if (state.value.access?.saves != true) return null
        if (!reset && shelfLookup?.isActive == true) return shelfLookup
        if (!reset && state.value.mixedSaves != null && shelfCursor == null) return null
        shelfLookup?.cancel()
        shelfLookup = viewModelScope.launch {
            update { copy(mixedSavesLoading = true, mixedSavesFailed = false) }
            try {
                val page = repository.mixedSaves(if (reset) null else shelfCursor)
                if (state.value.uid != uid) return@launch
                shelfCursor = page.cursor
                update {
                    val prior = if (reset) emptyList() else mixedSaves.orEmpty()
                    copy(mixedSaves = (prior + page.items).distinctBy { it.feedId }, mixedSavesHasMore = page.cursor != null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (state.value.uid == uid) update { copy(mixedSavesFailed = true) }
            } finally {
                if (state.value.uid == uid) update { copy(mixedSavesLoading = false) }
            }
        }
        return shelfLookup
    }

    fun closeSavedSetlist() = update { copy(openSavedSetlist = null) }

    fun showSavedSetlist(track: LiveSaveItem.Track) = runOperation {
        val room = repository.room(track.roomId) ?: error("Couldn’t load this set.")
        update { copy(openSavedSetlist = room) }
    }

    fun removeSavedTrack(track: LiveSaveItem.Track) = runOperation {
        repository.call("unsaveLiveTrack", fields = mapOf("saveId" to track.id))
        update {
            copy(savedIds = savedIds - track.id, mixedSaves = mixedSaves?.filterNot { it.feedId == track.feedId })
        }
    }

    fun updateQueue(
        queue: List<LiveTrack>, expectedVersion: Long, expectedHosting: Boolean, onFinished: () -> Unit = {},
    ): Boolean {
        val snapshot = state.value
        val room = snapshot.room ?: return false
        if (queueUpdateJob?.isActive == true) return false
        val dj = snapshot.dj
        val version = dj?.number("version") ?: room.queueVersion
        if (expectedVersion != version || expectedHosting != snapshot.hosting || (!snapshot.hosting && dj == null)) {
            update { copy(error = "The queue changed. Try again.") }
            return false
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
                onFinished()
            }
        }
        return true
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
                playbackLock.withLock { prepareTrack(next, { 0L }) }
                val position = livePlaybackPosition(next)
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
        if (track.playsFromSource) return repository.sourceAudioUrl(track)
        val song = track.song
        return song.previewUrl ?: player.lookupPreviewUrl(
            song.id, song.name, song.artistName, song.isrc, song.appleMusicId, song.durationMs, song.albumName,
        )
    }

    suspend fun prepareQueueTrack(track: LiveTrack): LiveTrack {
        if (!track.playsFromSource) return track
        val url = repository.sourceAudioUrl(track) ?: error("This track is unavailable for Live.")
        if (track.song.source != TrackSource.BANDCAMP) return track
        val probe = newLiveStreamPlayer(context)
        try {
            probe.prepareLiveStream(url)
            val duration = probe.duration
            check(duration in 1_000L..7_200_000L) { "This track is unavailable for Live." }
            return track.copy(song = track.song.copy(durationMs = duration.toInt()))
        } finally {
            probe.release()
        }
    }

    fun preloadSongSuggestions(force: Boolean = false) {
        val uid = state.value.uid ?: return
        if (songSuggestionsJob?.isActive == true) return
        val sourceTracks = state.value.access?.sourceTracks == true
        if (!force && SystemClock.elapsedRealtime() < songSuggestionsFreshUntil) return
        update { copy(songSuggestionsFailed = false) }
        songSuggestionsJob = viewModelScope.launch {
            try {
                val suggestions = repository.suggestions(uid, sourceTracks)
                if (state.value.uid != uid) return@launch
                update { copy(songSuggestions = suggestions) }
                songSuggestionsFreshUntil = SystemClock.elapsedRealtime() + 120_000L
                val imageSize = (LiveSongPickerArtworkSize.value * context.resources.displayMetrics.density).toInt()
                listOf(suggestions.recent, suggestions.saved, suggestions.trending)
                    .flatMap { it.take(LIVE_SONG_SUGGESTION_BATCH_SIZE) }
                    .mapNotNull { it.song.albumArtURL ?: it.song.albumArtLargeURL }
                    .distinct()
                    .forEach { url ->
                        SingletonImageLoader.get(context).enqueue(
                            ImageRequest.Builder(context).data(url).size(imageSize).build(),
                        )
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (state.value.uid == uid) {
                    update { copy(songSuggestionsFailed = true) }
                    songSuggestionsFreshUntil = SystemClock.elapsedRealtime() + 10_000L
                }
            }
        }
    }
    suspend fun search(query: String, offset: Int = 0) =
        repository.search(query, offset, state.value.access?.sourceTracks == true)
    suspend fun people(query: String) = state.value.uid?.let { repository.searchPeople(query, it) }.orEmpty()
    suspend fun recentPeople() = state.value.uid?.let { repository.recentPeople(it, state.value.room?.id) }.orEmpty()
    suspend fun spotifyPlaylist(id: String) = repository.spotifyPlaylist(id)
    suspend fun gifs(query: String) = repository.gifs(query)
    suspend fun share(id: String, setup: Boolean): String =
        if (setup) command("reserveShareLink", id).text("shareUrl") else "https://corus.fm/live/$id"

    private fun mount(room: LiveRoom) {
        roomJobs.forEach(Job::cancel)
        roomJobs.clear()
        queueUpdateJob?.cancel()
        cancelEffectSaves()
        lastTick = 0
        update {
            copy(
                room = room, ended = null, minimized = false, pendingChatEnabled = null, recoverableHostRoom = null, muted = false, messages = emptyList(),
                listenerCount = 0, membership = emptyMap(), audioError = null, queueUpdating = false,
                effectSelection = LiveEffectSelection(),
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
                val stillHosting = next.host.uid == uid
                if (!stillHosting && previous?.host?.uid == uid) cancelEffectSaves()
                update {
                    copy(
                        room = next,
                        effectSelection = if (stillHosting) effectSelection.observe(next.effect) else LiveEffectSelection(),
                    )
                }
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
            val knownAuthors = mutableMapOf<String, LivePerson?>()
            repository.messages(id).catch { error -> showError(error) }.collect { messages ->
                if (state.value.room?.id != id) return@collect
                fun withAvatars() = messages.map { message ->
                    val sender = message.author
                    val avatar = knownAuthors[sender.uid]?.avatar ?: sender.avatar
                    if (avatar == sender.avatar) message else message.copy(author = sender.copy(avatar = avatar))
                }
                update { copy(messages = withAvatars()) }
                val missing = messages.asSequence()
                    .filter { it.kind != "listenerJoined" }
                    .map { it.author.uid }
                    .filter { it.isNotBlank() && it !in knownAuthors }
                    .distinct()
                    .toList()
                if (missing.isEmpty()) return@collect
                try {
                    val profiles = repository.chatAuthors(missing)
                    missing.forEach { knownAuthors[it] = profiles[it] }
                    if (state.value.room?.id == id) update { copy(messages = withAvatars()) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep the chat visible and retry the lookup on the next snapshot.
                }
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
                if (followAppleMusicQueue(room)) {
                    delay(250)
                    continue
                }
                if (room.status == "playing" && room.pausedAt == null && room.deadline <= serverNow &&
                    expiredRevision != room.revision
                ) {
                    expiredRevision = room.revision
                    if (room.pendingSkip == null) {
                        sourcePlayer?.pause()
                        applePlayer?.pause()
                        if (expectedUri != null) spotify.pauseImmediately()
                    }
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
                val current = state.value
                val latest = current.room
                if (latest?.id == id && !current.muted && foreground) synchronize(latest)
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

    private fun appleQueueTarget(room: LiveRoom): AppleQueueTarget? {
        val current = state.value
        if (!foreground || !current.hosting || current.access?.appleMusicHostAutoplay != true ||
            current.muted || welcomePlaying || auditionPlaying || operation?.isActive == true ||
            room.id != current.room?.id || room.status != "playing" || room.pausedAt != null ||
            room.voiceMode == "talk" || room.pendingSkip != null || confirmedRevision != room.revision ||
            room.current?.playsFromSource != false || liveProvider != MusicService.APPLE_MUSIC
        ) return null
        val nativeQueue = applePlayer as? AppleMusicLiveQueue ?: return null
        val next = room.nextTrack?.takeUnless { it.playsFromSource } ?: return null
        return AppleQueueTarget(nativeQueue, room.id, room.revision, room.queueVersion, next)
    }

    // Let MusicKit cross the track boundary before publishing the new room clock.
    // If MusicKit can't stage or start the next song, the room's deadline still advances it.
    private suspend fun followAppleMusicQueue(room: LiveRoom): Boolean {
        val target = appleQueueTarget(room)
        if (target != appleQueueTarget) {
            appleQueueLookup?.cancel()
            val previous = appleQueueTarget
            appleQueueTarget = target
            if (previous != null && (target == null || previous.player != target.player ||
                previous.revision == target.revision && previous.track.appleKey != target.track.appleKey)
            ) {
                previous.player.stageNext(null, previous.revision)
            }
            appleQueueLookup = target?.let {
                viewModelScope.launch {
                    try {
                        val songId = appleMusic.resolveConnectedSong(target.track.song.toQueuedTrack()) ?: return@launch
                        val latest = state.value.room ?: return@launch
                        if (appleQueueTarget(latest) == target) {
                            target.player.stageNext(songId, target.revision, MediaItem.Builder().setMediaId(target.track.id).build())
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // A lookup failure uses the server's scheduled advance.
                    }
                }
            }
        }
        if (target == null || now >= room.deadline + APPLE_QUEUE_GRACE_MS) return false
        if (appleAdvanceConfirmed == target) return true
        val position = target.player.stagedPosition(target.track.id, target.revision)
        if (position == null) return now >= room.deadline && target.player.hasStagedNext(target.track.id, target.revision)
        if (position >= target.track.duration) return false
        if (state.value.access?.appleMusicHostEarlyAdvance != true && now - position < room.deadline - 4_000L) return true

        appleTrackKey = target.track.appleKey
        initialTrackRevision = room.revision + 1
        return try {
            withTimeoutOrNull(APPLE_QUEUE_GRACE_MS) {
                command("confirmAppleMusicAdvance", room.id, mapOf(
                    "revision" to room.revision,
                    "queueVersion" to room.queueVersion,
                    "trackId" to target.track.id,
                    "trackSource" to target.track.song.source.raw,
                    "positionMs" to position,
                ))
            }?.let {
                appleAdvanceConfirmed = target
                true
            } ?: false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private fun synchronize(room: LiveRoom, force: Boolean = false): Job? {
        if (state.value.room?.id != room.id || (room.pendingSkip != null && state.value.hosting)) return null
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            try {
                playbackLock.withLock {
                    val track = room.current
                    val staged = appleQueueTarget(room)
                    if (staged != null && now < room.deadline + APPLE_QUEUE_GRACE_MS &&
                        staged.player.stagedPosition(staged.track.id, staged.revision) != null
                    ) return@withLock
                    if (state.value.muted || welcomePlaying || auditionPlaying || track == null || !room.musicShouldPlay(now)) {
                        sourcePlayer?.pause()
                        applePlayer?.pause()
                        if (expectedUri != null) spotify.pause()
                        return@withLock
                    }
                    if (track.playsFromSource) {
                        val previousRevision = confirmedRevision
                        val alreadyStarted = initialTrackRevision == room.revision &&
                            sourceTrackId == track.id && sourcePlayer?.isPlaying == true
                        if (alreadyStarted) initialTrackRevision = null
                        else playSourceTrack(track, { room.position(now) }, force || confirmedRevision != room.revision)
                        confirmedRevision = room.revision
                        if (previousRevision == null && !state.value.hosting) event("playback_started", source = track.song.source.raw)
                        update { copy(audioReady = true, audioError = null) }
                        return@withLock
                    }
                    releaseSourcePlayer()
                    when (liveProvider) {
                        MusicService.APPLE_MUSIC -> {
                            val previousRevision = confirmedRevision
                            val startedHere = initialTrackRevision == room.revision && appleTrackKey == track.appleKey &&
                                applePlayer?.isPlaying == true
                            if (startedHere) initialTrackRevision = null
                            else if (applePlayer?.playbackState != Player.STATE_ENDED || confirmedRevision != room.revision || force) {
                                playAppleTrack(track, { room.position(now) },
                                    forceSeek = force || confirmedRevision != room.revision)
                            }
                            confirmedRevision = room.revision
                            if (previousRevision == null && !state.value.hosting) {
                                event("playback_started", source = MusicService.APPLE_MUSIC.value)
                            }
                            update { copy(audioReady = true, audioError = null) }
                            return@withLock
                        }
                        MusicService.SPOTIFY -> releaseApplePlayer()
                        else -> {
                            update { copy(audioReady = false, audioError = "Connect a streaming provider to hear the music.") }
                            return@withLock
                        }
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
                    } else if (!spotify.isConnected || confirmedRevision != room.revision) {
                        prepareTrack(track, { room.position(now) }, uri)
                    } else {
                        val sameTrack = spotify.syncLiveTrack(uri, force, targetPosition = {
                            val latest = state.value.room
                            if (latest?.id == room.id && latest.revision == room.revision &&
                                !(latest.pendingSkip != null && state.value.hosting) &&
                                !state.value.muted && !welcomePlaying && !auditionPlaying && latest.musicShouldPlay(now)
                            ) latest.position(now) else null
                        }) { difference ->
                            analytics.logEvent("live_sync_corrected", mapOf(
                                "source" to "spotify", "milliseconds" to difference.toInt(),
                            ))
                        } ?: return@withLock
                        if (!sameTrack) prepareTrack(track, { room.position(now) }, uri, forceSpotifyPlay = true)
                    }
                    confirmedRevision = room.revision
                    if (previousRevision == null && !state.value.hosting) event("playback_started")
                    update { copy(audioReady = true, audioError = null) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when {
                    room.current?.playsFromSource == true -> {
                        releaseSourcePlayer()
                        "Couldn’t play this Live track. Try again."
                    }
                    liveProvider == MusicService.APPLE_MUSIC -> {
                        releaseApplePlayer()
                        error.message ?: "Couldn’t sync Apple Music. Try again."
                    }
                    else -> "Couldn’t sync Spotify. Try again."
                }
                update { copy(audioReady = false, audioError = message) }
            }
        }
        return playbackJob
    }

    private fun matchesSpotifyUri(uri: String): Boolean =
        spotify.currentTrackUri.value?.let { SpotifyPlaybackService.spotifyURIsMatch(it, uri) } == true

    private suspend fun resolve(track: LiveTrack): String? {
        val key = track.song.source.raw to track.id
        return uriCache[key] ?: repository.spotifyUri(track)?.also { uriCache[key] = it }
    }

    private suspend fun prepareTrack(
        track: LiveTrack,
        position: () -> Long,
        knownUri: String? = null,
        forceSpotifyPlay: Boolean = false,
        onStartingProvider: () -> Unit = {},
    ) {
        if (track.playsFromSource) {
            playSourceTrack(track, position, forceSeek = true)
            return
        }
        releaseSourcePlayer()
        if (liveProvider == MusicService.APPLE_MUSIC) {
            onStartingProvider()
            playAppleTrack(track, position, forceSeek = true, restart = true)
            return
        }
        releaseApplePlayer()
        requireSpotify()
        val uri = knownUri ?: resolve(track) ?: error("This song is unavailable on Spotify.")
        claimLivePlayback()
        expectedUri = uri
        if (forceSpotifyPlay || !spotify.isConnected || !matchesSpotifyUri(uri) || !spotify.isPlaying.value) {
            onStartingProvider()
            spotify.play(uri.substringAfterLast(':'), uri, replaceQueue = false, confirmPlayback = true)
        }
        val target = position().coerceIn(0, track.duration.toLong() - 1)
        val observed = spotify.positionSeconds.value * 1_000
        if (target > 250 || observed > 750) {
            check(spotify.seek(target / 1_000.0)) { "Couldn’t sync Spotify." }
        }
    }

    private suspend fun claimLivePlayback() {
        playbackRelease?.join()
        if (!ownsLivePlayback) {
            ownsLivePlayback = true
            player.beginLiveSpotifyPlayback()
        }
    }

    private suspend fun pauseLiveSpotify() {
        if (expectedUri == null) return
        val paused = spotify.pause()
        currentCoroutineContext().ensureActive()
        check(paused || !spotify.isConnected) { "Couldn’t pause Spotify." }
        expectedUri = null
    }

    private val LiveTrack.appleKey get() = song.source to id

    private suspend fun playAppleTrack(
        track: LiveTrack,
        position: () -> Long,
        forceSeek: Boolean,
        restart: Boolean = false,
    ) {
        claimLivePlayback()
        pauseLiveSpotify()
        val current = applePlayer
        val audio = if (!restart && appleTrackKey == track.appleKey && current != null &&
            current.playerError == null && current.playbackState != Player.STATE_ENDED
        ) current else {
            releaseApplePlayer()
            check(appleMusic.isConnected()) { "Connect Apple Music to hear the music." }
            val candidate = appleMusic.createPlayer(
                track.song.toQueuedTrack(), MediaItem.Builder().setMediaId(track.id).build(),
                interactive = false, explicit = false, onNext = {}, onPrevious = {},
            ) ?: error("This song is unavailable on Apple Music.")
            applePlayer = candidate
            appleTrackKey = track.appleKey
            candidate.play()
            candidate.prepare()
            candidate
        }
        val ready = withTimeoutOrNull(15_000L) {
            while (audio.playbackState != Player.STATE_READY) {
                audio.playerError?.let { throw it }
                check(audio.playbackState != Player.STATE_ENDED) { "Apple Music finished the song before it could sync." }
                delay(50)
            }
            true
        }
        check(ready == true) { "Apple Music took too long to start. Try again." }
        val target = position().coerceIn(0, track.duration.toLong() - 1)
        val threshold = if (forceSeek) 750L else 1_500L
        if (kotlin.math.abs(audio.currentPosition - target) > threshold) {
            check(audio.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                "Apple Music couldn’t seek to the room’s playback position."
            }
            audio.seekTo(target)
            val synced = withTimeoutOrNull(3_000L) {
                while (kotlin.math.abs(audio.currentPosition - position()) > 1_500L) {
                    audio.playerError?.let { throw it }
                    delay(50)
                }
                true
            }
            check(synced == true) { "Couldn’t sync Apple Music. Try again." }
        }
        audio.play()
    }

    private fun releaseApplePlayer() {
        appleQueueLookup?.cancel()
        appleQueueLookup = null
        appleQueueTarget = null
        appleAdvanceConfirmed = null
        applePlayer?.release()
        applePlayer = null
        appleTrackKey = null
    }

    private suspend fun playSourceTrack(track: LiveTrack, position: () -> Long, forceSeek: Boolean) {
        claimLivePlayback()
        releaseApplePlayer()
        pauseLiveSpotify()
        val current = sourcePlayer
        val audio = if (sourceTrackId == track.id && current != null &&
            current.playerError == null && current.playbackState != Player.STATE_IDLE &&
            current.playbackState != Player.STATE_ENDED
        ) {
            current
        } else {
            releaseSourcePlayer()
            val url = repository.sourceAudioUrl(track) ?: error("This track is unavailable for Live.")
            newLiveStreamPlayer(context).also {
                sourcePlayer = it
                sourceTrackId = track.id
                try {
                    it.prepareLiveStream(url, position().coerceIn(0, track.duration.toLong() - 1))
                    it.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            val room = state.value.room
                            if (sourcePlayer === it && room?.current?.id == track.id) {
                                update { copy(audioReady = false, audioError = "Couldn’t play this Live track. Try again.") }
                            }
                        }
                    })
                } catch (error: Exception) {
                    releaseSourcePlayer()
                    throw error
                }
            }
        }
        val target = position().coerceIn(0, track.duration.toLong() - 1)
        if (forceSeek || kotlin.math.abs(audio.currentPosition - target) > 750) audio.seekTo(target)
        audio.play()
    }

    private fun livePlaybackPosition(track: LiveTrack): Long {
        val position = when {
            track.playsFromSource -> sourcePlayer?.currentPosition ?: 0
            liveProvider == MusicService.APPLE_MUSIC -> applePlayer?.currentPosition ?: 0
            else -> (spotify.positionSeconds.value * 1_000).toLong()
        }
        return position.coerceIn(0, track.duration.toLong() - 1)
    }

    private fun releaseSourcePlayer() {
        sourcePlayer?.release()
        sourcePlayer = null
        sourceTrackId = null
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
            copy(room = null, ended = ended, minimized = false, pendingChatEnabled = null, messages = emptyList(),
                audioReady = false, audioError = null, micEnabled = false, voiceLevels = emptyMap(), membership = emptyMap(),
                pendingDJs = emptyList(), djRequests = emptyList(), myDJRequest = emptyMap(), queueUpdating = false,
                effectSelection = LiveEffectSelection())
        }
    }

    private fun resetRoom() {
        roomJobs.forEach(Job::cancel)
        roomJobs.clear()
        hostRecords?.cancel()
        playbackJob?.cancel()
        voiceJob?.cancel()
        queueUpdateJob?.cancel()
        cancelEffectSaves()
        heartJob?.cancel()
        saveLookup?.cancel()
        heartsPending = 0
        welcomePlaying = false
        auditionPlaying = false
        voice.disconnect()
        releasePlayback()
    }

    private fun cancelEffectSaves() {
        effectSaveJob?.cancel()
        effectConfirmationJob?.cancel()
    }

    private fun releasePlayback() {
        releaseSourcePlayer()
        releaseApplePlayer()
        if (ownsLivePlayback) {
            playbackRelease = player.endLiveSpotifyPlayback(pauseSpotify = expectedUri != null)
            ownsLivePlayback = false
        }
        liveProvider = null
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
                    confirmedRevision?.let { revision ->
                        if (room.current?.playsFromSource == true) {
                            if (sourcePlayer?.isPlaying == true) put("backgroundPlaybackRevision", revision)
                        } else if (liveProvider == MusicService.APPLE_MUSIC) {
                            if (applePlayer?.isPlaying == true) put("backgroundPlaybackRevision", revision)
                        } else if (expectedUri != null) put("spotifyPlaybackRevision", revision)
                    }
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
        private const val APPLE_QUEUE_GRACE_MS = 2_500L
        fun saveId(track: LiveTrack): String = MessageDigest.getInstance("SHA-256")
            .digest("${track.song.source.raw}:${track.id}".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

private fun Boolean?.orFalse(): Boolean = this == true

/** True for a matching track, false when it needs to be cued again, null when sync should wait. */
internal suspend fun SpotifyPlaybackService.syncLiveTrack(
    expectedUri: String,
    force: Boolean,
    targetPosition: () -> Long?,
    onDriftCorrected: (Long) -> Unit,
): Boolean? {
    // App Remote state callbacks can lag behind playback. Only a fresh reading
    // can justify interrupting the current track to correct drift.
    val snapshot = try {
        refreshState()
    } catch (_: SpotifyPlaybackError.ApiError) {
        return null
    } ?: return null
    val target = targetPosition() ?: return null
    val uri = snapshot.track?.uri?.takeIf(String::isNotBlank) ?: return null
    if (!SpotifyPlaybackService.spotifyURIsMatch(uri, expectedUri)) return false
    val difference = kotlin.math.abs(snapshot.playbackPosition - target)
    if (snapshot.isPaused) check(resume()) { "Couldn’t resume Spotify." }
    if (force || difference > 1_800L) {
        currentCoroutineContext().ensureActive()
        val seekTarget = targetPosition() ?: return null
        check(seek(seekTarget / 1_000.0)) { "Couldn’t sync Spotify." }
        if (difference > 1_800L) onDriftCorrected(difference)
    }
    return true
}
