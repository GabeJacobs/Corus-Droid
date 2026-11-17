package fm.corus.android.ui.screens.live

import android.content.Context
import android.content.res.Resources
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
import fm.corus.android.service.CorusFirebaseMessagingService
import fm.corus.android.service.DeepLinkDestination
import fm.corus.android.i18n.LanguageManager
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import fm.corus.android.R
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal enum class LiveProgress { TUNING, PREPARING, CUEING, STARTING_SPOTIFY, STARTING_APPLE_MUSIC, STARTING }
internal enum class LiveDJRequestFeedback { SENDING, SENT, FAILED }

internal const val LiveJoinMinimumMs = 1_800
internal const val LiveJoinFinishMs = 180

internal class PendingLiveChatReaction(val active: Boolean)

internal data class LiveUiState(
    val uid: String? = null,
    val access: LiveAccess? = null,
    val accessResolved: Boolean = false,
    val cachedMenuAccess: Boolean = false,
    val discovery: List<LiveRoom> = emptyList(),
    val invites: List<LiveFields> = emptyList(),
    val shownInvite: LiveFields? = null,
    val seenInviteKeys: Set<Pair<String, Long>> = emptySet(),
    val pendingInvitePush: LiveInvitePush? = null,
    val roomJoinPrompt: DeepLinkDestination.LiveRoom? = null,
    val joiningInvite: Boolean = false,
    val inviteError: Int? = null,
    val setup: LiveSetup? = null,
    val checkingSetupAccess: Boolean = false,
    val songSuggestions: LiveSuggestions? = null,
    val songSuggestionsFailed: Boolean = false,
    val room: LiveRoom? = null,
    val ended: LiveRoom? = null,
    val minimized: Boolean = false,
    val pendingChatEnabled: Boolean? = null,
    val pendingChatReactions: Map<Pair<String, String>, PendingLiveChatReaction> = emptyMap(),
    val recoverableHostRoom: LiveRoom? = null,
    val muted: Boolean = false,
    val audioReady: Boolean = false,
    val audioError: String? = null,
    val error: String? = null,
    val progress: LiveProgress? = null,
    val joiningRoom: LiveRoom? = null,
    val joinReady: Boolean = false,
    val listenerCount: Int = 0,
    val incomingHearts: Long = 0,
    val messages: List<LiveMessage> = emptyList(),
    val pendingLikeNotice: PendingLiveLikeNotice? = null,
    val optimisticLikedRevision: Long? = null,
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
    val djRequestFeedback: LiveDJRequestFeedback? = null,
    val mySongRequest: LiveFields = emptyMap(),
    val requestBusy: Boolean = false,
    val queueUpdating: Boolean = false,
    val effectSelection: LiveEffectSelection = LiveEffectSelection(),
    val micEnabled: Boolean = false,
    val voiceLevels: Map<String, Float> = emptyMap(),
    val unmutedMicrophoneIDs: Set<String> = emptySet(),
    val needsClub: Boolean = false,
    val spotifyInstallNeeded: Boolean = false,
    val hostProviderPrompt: Boolean = false,
    val hostProviderRequiredForSetup: Boolean = false,
    val connectingProvider: MusicService? = null,
    val hostProviderHint: String? = null,
    val linkUpcoming: Boolean = false,
    val playbackExitEndsShow: Boolean? = null,
) {
    val coversFeed get() = room != null && !minimized || progress == LiveProgress.TUNING
    val usesDarkRoomAppearance: Boolean
        get() = when (room?.let { effectSelection.visible(it.effect) }) {
            "slow", "disco", "party", "rain", "snow" -> true
            else -> false
        }

    val chatRows: List<LiveMessage>
        get() {
            if (!hosting) {
                val rows = messages.filterNot { it.kind == "djRequest" }
                val pending = pendingLikeNotice ?: return rows
                if (room?.chatEnabled != true || room.revision != pending.message.fields.number("revision") ||
                    rows.any(pending::matches)
                ) return rows
                val index = rows.indexOfFirst { it.createdAt > pending.message.createdAt }
                return if (index < 0) rows + pending.message
                else rows.toMutableList().apply { add(index, pending.message) }
            }
            if (djRequests.isEmpty()) return messages
            val publicIds = messages.mapTo(hashSetOf()) { it.id }
            val privateRequests = djRequests.mapNotNull { request ->
                if (request.text("chatMessageId") in publicIds) return@mapNotNull null
                val requestId = "private-dj-request:${request.text("uid")}:${request.number("createdAt")}"
                LiveMessage(requestId, request + mapOf(
                    "kind" to "djRequest", "requestStatus" to request.text("status"),
                ))
            }
            return (messages + privateRequests).sortedBy { it.createdAt }
        }

    val hosting get() = room?.host?.uid == uid && room != null

    val hasLikedCurrentSong: Boolean
        get() = room?.revision?.let { revision ->
            revision > 0 && (membership.number("likedRevision") == revision || optimisticLikedRevision == revision)
        } == true

    fun withConfirmedChatMessage(message: LiveMessage): LiveUiState {
        if (messages.any { it.id == message.id }) return this
        val index = messages.indexOfFirst {
            it.createdAt > message.createdAt || (it.createdAt == message.createdAt && it.id > message.id)
        }
        val updated = messages.toMutableList().apply { add(if (index < 0) size else index, message) }
        return copy(messages = updated.takeLast(100))
    }

    fun reactionUsers(message: LiveMessage, emoji: String): List<String> {
        val users = message.reactionUsers(emoji)
        val viewer = uid ?: return users
        val pending = pendingChatReactions[message.id to emoji] ?: return users
        if ((viewer in users) == pending.active) return users
        return if (pending.active) users + viewer else users.filterNot { it == viewer }
    }

    fun reactionEmojis(message: LiveMessage): Set<String> =
        message.chatReactionEmojis + pendingChatReactions.keys.filter { it.first == message.id }.map { it.second }
    val miniPlayerRoom get() = if (room == null && setup == null && ended == null) recoverableHostRoom
        else room?.takeIf { minimized }

    fun canSkipTrack(room: LiveRoom, queue: List<LiveTrack> = room.queue): Boolean =
        room.pendingSkip == null && !room.talkAfterSongArmed && !queueUpdating && room.current != null &&
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
    private var requestJob: Job? = null
    private var djRequestFeedbackExpiry: Job? = null
    private var playbackExit: CompletableDeferred<Boolean>? = null
    private var playbackRelease: Job? = null
    private val hiddenRooms = mutableSetOf<String>()
    private var discoverySource: List<LiveRoom> = emptyList()
    private var heartJob: Job? = null
    private var heartsPending = 0
    private var heartRevision = 0L
    private var confirmedHeartRevision = 0L
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
            }
        }
        viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.map { it.uid }.distinctUntilChanged().collectLatest { uid ->
                    if (uid == null) return@collectLatest
                    while (true) {
                        try {
                            resolveAccess(uid)
                            break
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            delay(10_000)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            state.map { it.uid to it.access }.distinctUntilChanged().collectLatest { (uid, access) ->
                if (uid == null || access == null) return@collectLatest
                coroutineScope {
                    launch {
                        repository.discovery().catch { error -> showError(error) }.collectLatest { rooms ->
                            discoverySource = rooms
                            refreshDiscovery()
                        }
                    }
                    if (access.collaboration) launch {
                        repository.invites(uid).catch { error -> showError(error) }.collectLatest { invites ->
                            val currentInvites = invites.filter { it.number("expiresAt") > now }
                            update {
                                val shown = shownInvite
                                copy(
                                    invites = currentInvites,
                                    shownInvite = shown?.takeIf {
                                        joiningInvite || this.invites.none { it.inviteKey() == shown.inviteKey() } ||
                                            currentInvites.any { it.inviteKey() == shown.inviteKey() }
                                    },
                                )
                            }
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
                state.map { it.uid to it.access?.collaboration }.distinctUntilChanged().collectLatest { (uid, collaboration) ->
                    if (uid == null || collaboration == null) return@collectLatest
                    CorusFirebaseMessagingService.tokenChanged.onStart { emit(Unit) }.collectLatest refresh@ {
                        try {
                            val token = FirebaseMessaging.getInstance().token.await()
                            if (auth.currentUserId != uid) return@refresh
                            val locale = LanguageManager.current(context).tag.ifEmpty {
                                Resources.getSystem().configuration.locales[0].toLanguageTag()
                            }
                            repository.call("registerPush", fields = mapOf(
                                "token" to token, "djInvites" to collaboration, "locale" to locale,
                            ))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) { }
                    }
                }
            }
        }
        viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.map {
                    it.uid?.takeIf { _ -> it.access != null && it.room == null && it.setup == null &&
                        it.ended == null && it.playbackExitEndsShow == null }
                }.distinctUntilChanged().collectLatest { uid ->
                    if (uid != null) refreshHostedRoomPreview(uid)
                }
            }
        }
    }

    internal suspend fun refreshHostedRoomPreview(uid: String) {
        while (true) {
            try {
                val hosted = repository.hostedRoom(uid)
                currentCoroutineContext().ensureActive()
                update {
                    if (this.uid == uid && room == null && setup == null && ended == null && playbackExitEndsShow == null) {
                        copy(recoverableHostRoom = hosted)
                    } else this
                }
                if (hosted == null) return
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Retry a failed launch lookup while the app stays open.
            }
            delay(10_000)
        }
    }

    private suspend fun resolveAccess(uid: String): LiveAccess? = accessLock.withLock {
        if (state.value.uid != uid || auth.currentUser.value?.uid != uid) return@withLock null
        state.value.access?.let { return@withLock it }
        val access = try {
            repository.access()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (state.value.uid == uid) update { copy(accessResolved = true) }
            throw error
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
            update { copy(discovery = rooms) }
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
        // The server omits rooms that are no longer live or whose host is blocked.
        update { copy(discovery = rooms.filter { it.id in counts }) }
    }

    fun openSetup() {
        val current = state.value
        val uid = current.uid ?: return
        if (current.setup != null || current.playbackExitEndsShow != null) return
        if (current.room != null) {
            if (current.hosting) expand()
            return
        }
        if (operation?.isActive == true) return
        val lookup = preloadFirstSong() ?: return
        event("host_setup_opened", source = "go_live")
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
                check(resolveAccess(uid) != null) { context.getString(R.string.live_live_is_unavailable) }
                val existing = current.recoverableHostRoom?.takeIf { it.host.uid == uid }
                    ?: repository.hostedRoom(uid)
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
                    check(access != "upgrade") { context.getString(R.string.live_couldnt_verify_your_club_access_try_again) }
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
            else context.getString(R.string.live_connect_an_apple_music_subscription_and_try_again)
        MusicService.SPOTIFY -> {
            requireSpotify()
            spotify.authorizeForLink()
            spotifyCapabilityError()
        }
        else -> context.getString(R.string.live_apple_music_or_spotify_premium_is_required_for_live)
    }

    private suspend fun spotifyCapabilityError(): String? = when (spotify.canPlayOnDemandForLive()) {
        true -> null
        false -> context.getString(R.string.live_use_a_spotify_premium_account_and_try_connecting_again)
        null -> context.getString(R.string.live_couldnt_verify_spotify_playback_open_spotify_and_try_connecting_again)
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
                    update { copy(hostProviderHint = context.getString(R.string.live_connect_provider_failed, provider.displayLabel)) }
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
            val existing = repository.hostedRoom(uid)
            currentCoroutineContext().ensureActive()
            if (existing != null && resumeHostedRoom(existing)) return@runOperation
            event("host_start_attempted", source = "host_setup", roomId = setup.id)
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
                        event("host_start_result", "failed", "host_setup", setup.id)
                        return@runOperation
                    }
                }
                checkingProvider = false
                update { copy(progress = LiveProgress.STARTING) }
                val position = livePlaybackPosition(first)
                val result = command("create", setup.id, fields + ("initialPlaybackPositionMs" to position))
                created = true
                val room = result["room"].liveFields()?.let { LiveRoom(setup.id, it) } ?: repository.room(setup.id)
                check(room != null && room.host.uid == uid && room.active(now)) { context.getString(R.string.live_couldnt_start_the_live_room_try_again) }
                check(setup.invitees.isEmpty() || room.collaborative) { context.getString(R.string.live_dj_invites_are_unavailable_on_this_build) }
                initialTrackRevision = room.revision
                mount(room)
                event("host_started", source = provider.value, roomId = room.id)
                event("host_start_result", "success", "host_setup", room.id)
            } catch (error: Exception) {
                if (created && error !is CancellationException) {
                    try { repository.call("end", setup.id) } catch (_: Exception) { }
                }
                if (state.value.room == null) releasePlayback()
                if (error is CancellationException) throw error
                event("host_start_result", "failed", "host_setup", setup.id)
                if (checkingProvider && (error is SpotifyPlaybackError || provider == MusicService.APPLE_MUSIC)) {
                    update {
                        copy(hostProviderPrompt = true, hostProviderHint = if (error is SpotifyPlaybackError.AuthorizationCancelled) null
                            else if (provider == MusicService.APPLE_MUSIC) context.getString(R.string.live_couldnt_start_apple_music_try_again)
                            else context.getString(R.string.live_couldnt_connect_to_spotify_open_spotify_check_that_youre_signed_in_to_premium_then_try_again))
                    }
                } else if (error is FirebaseFunctionsException &&
                    error.details.liveFields()?.text("reason") == "live-host-club-required"
                ) update { copy(needsClub = true) } else throw error
            }
        }
    }

    fun promptToJoinRoom(destination: DeepLinkDestination.LiveRoom) {
        if (state.value.access == null || state.value.room?.id == destination.roomId) {
            join(destination.roomId, link = destination.sharedLink)
        } else {
            update { copy(roomJoinPrompt = destination) }
        }
    }

    fun dismissRoomJoinPrompt(prompt: DeepLinkDestination.LiveRoom) {
        update { if (roomJoinPrompt == prompt) copy(roomJoinPrompt = null) else this }
    }

    fun confirmRoomJoin(prompt: DeepLinkDestination.LiveRoom) {
        if (state.value.roomJoinPrompt != prompt) return
        dismissRoomJoinPrompt(prompt)
        event("invite_opened", source = if (prompt.sharedLink) "share" else "push")
        join(prompt.roomId, link = prompt.sharedLink, fromPush = !prompt.sharedLink)
    }

    suspend fun roomJoinPreview(id: String): LiveRoom? = repository.room(id)

    fun join(id: String, link: Boolean = false, invite: LiveFields? = null, fromPush: Boolean = false) {
        if (state.value.access == null) {
            if (state.value.accessResolved) update { copy(error = context.getString(R.string.live_live_is_unavailable)) }
            return
        }
        if (state.value.room?.id == id) {
            if (invite == null) expand()
            else runOperation(onError = ::showInviteJoinError) {
                command("acceptDJInvite", id, mapOf("inviteExpiresAt" to invite.number("expiresAt")))
                clearInvite()
                expand()
            }
            return
        }
        if (operation?.isActive == true) return
        pendingJoin = id
        val entranceStartedAt = SystemClock.elapsedRealtime()
        suspend fun awaitEntrance() {
            delay((LiveJoinMinimumMs - (SystemClock.elapsedRealtime() - entranceStartedAt)).coerceAtLeast(0))
        }
        suspend fun finishEntrance() {
            val remaining = LiveJoinMinimumMs - LiveJoinFinishMs - (SystemClock.elapsedRealtime() - entranceStartedAt)
            delay(remaining.coerceAtLeast(0))
            update { copy(joinReady = true) }
            delay(LiveJoinFinishMs.toLong())
        }
        update { copy(joiningRoom = discovery.firstOrNull { it.id == id }) }
        val joinSource = when {
            link -> "share_link"
            invite != null -> "dj_invite"
            fromPush -> "push"
            else -> "discovery"
        }
        fun joinFinished(outcome: String) = event("join_finished", outcome, joinSource, id)
        event("join_attempted", source = joinSource, roomId = id)
        runOperation(
            progress = if (invite == null) LiveProgress.TUNING else null,
            onError = { error ->
                joinFinished("failed")
                if (invite == null) showError(error) else showInviteJoinError(error)
            },
        ) {
            if (link) {
                when (repository.call("resolveShareLink", id).text("status")) {
                    "upcoming" -> {
                        joinFinished("upcoming")
                        update { copy(linkUpcoming = true) }
                        return@runOperation
                    }
                    "ended" -> {
                        val ended = repository.room(id)
                        update { copy(ended = ended, error = if (ended == null) context.getString(R.string.live_this_room_has_ended) else null) }
                        joinFinished("ended")
                        return@runOperation
                    }
                }
            }
            val original = repository.room(id)
            if (original == null) {
                if (invite == null) error(context.getString(R.string.live_this_room_is_unavailable))
                pendingJoin = null
                joinFinished("unavailable")
                update { copy(joiningInvite = false, inviteError = R.string.live_dj_invitation_unavailable) }
                return@runOperation
            }
            update { copy(joiningRoom = original) }
            if (state.value.room != null) {
                check(invite == null)
                exitCurrent(end = state.value.hosting, showSetlist = false)
            }
            liveProvider = selectedLiveProvider()
            if (liveProvider == MusicService.SPOTIFY && original.current?.playsFromSource != true) {
                try {
                    awaitEntrance()
                    spotify.authorizeForLink()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: SpotifyPlaybackError) {
                    liveProvider = null
                }
            }
            if (original.host.uid == state.value.uid) {
                command("tick", id, mapOf("hostActivity" to "foreground"))
                val room = repository.room(id) ?: original
                finishEntrance()
                joinFinished("resumed_host")
                mount(room)
                pendingJoin = null
                return@runOperation
            }
            var latest: LiveRoom = original
            repeat(3) { attempt ->
                try {
                    command("join", id, mapOf(
                        "revision" to latest.revision, "voiceProtocol" to 1,
                        "collaborationProtocol" to 1, "musicService" to liveProvider?.value,
                    ))
                } catch (error: FirebaseFunctionsException) {
                    if (attempt == 2 || error.code != FirebaseFunctionsException.Code.ABORTED) throw error
                    latest = repository.room(id) ?: throw error
                    update { copy(joiningRoom = latest) }
                    return@repeat
                }
                if (invite != null) {
                    try {
                        command("acceptDJInvite", id, mapOf("inviteExpiresAt" to invite.number("expiresAt")))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        try {
                            command("leave", id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) { }
                        throw error
                    }
                    clearInvite()
                } else {
                    finishEntrance()
                }
                mount(latest)
                pendingJoin = null
                joinFinished("joined")
                return@runOperation
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
    suspend fun chatReactionPeople(ids: List<String>) = repository.chatReactionPeople(ids)

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
                    update { copy(audioError = context.getString(R.string.live_connect_provider_failed, provider.displayLabel)) }
                }
            } finally {
                update { copy(connectingProvider = null) }
            }
        }
    }

    private suspend fun requestPlaybackExit(): Boolean {
        if (playbackExit != null) return false
        val current = state.value
        val recovered = current.recoverableHostRoom?.takeIf { current.room == null && it.host.uid == current.uid }
        val target = current.room ?: recovered
        if (target == null) {
            playbackRelease?.join()
            return true
        }
        val end = recovered != null || current.hosting
        val answer = CompletableDeferred<Boolean>()
        playbackExit = answer
        update { copy(playbackExitEndsShow = end) }
        try {
            if (!answer.await() || state.value.uid != current.uid || auth.currentUserId != current.uid) return false
            if (recovered != null) {
                if (state.value.room != null || state.value.setup != null) return false
                try {
                    command("end", recovered.id)
                    event("host_ended", source = "host", roomId = recovered.id)
                } catch (error: FirebaseFunctionsException) {
                    if (error.code != FirebaseFunctionsException.Code.NOT_FOUND) throw error
                }
                if (state.value.uid != current.uid || auth.currentUserId != current.uid) return false
                update {
                    if (recoverableHostRoom?.id == recovered.id) copy(recoverableHostRoom = null) else this
                }
                return state.value.room == null && state.value.recoverableHostRoom == null
            }
            if (state.value.room?.let { it.id != target.id || state.value.hosting != end } == true) return false
            exitCurrent(end = end, showSetlist = false)
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
                update { copy(playbackExitEndsShow = null) }
            }
        }
    }

    fun confirmPlaybackExit(leave: Boolean) { playbackExit?.complete(leave) }

    fun leave() = runOperation {
        val room = state.value.room ?: return@runOperation
        closeRoom(null)
        try {
            command("leave", room.id)
            event("listener_left")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Presence expires if the leave could not reach the server.
        }
    }
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

    fun requestAction(name: String, fields: LiveFields = emptyMap()) {
        val current = state.value
        val room = current.room ?: return
        if (current.requestBusy) return
        val sendingDJRequest = name == "requestDJ"
        if (sendingDJRequest && (current.myDJRequest.text("status") == "pending" ||
                current.djRequestFeedback == LiveDJRequestFeedback.SENT)) return
        if (sendingDJRequest) djRequestFeedbackExpiry?.cancel()
        update {
            copy(
                requestBusy = true,
                djRequestFeedback = if (sendingDJRequest) LiveDJRequestFeedback.SENDING else djRequestFeedback,
            )
        }
        requestJob = viewModelScope.launch {
            try {
                command(name, room.id, fields)
                if (sendingDJRequest && state.value.room?.id == room.id) {
                    update {
                        if (djRequestFeedback == LiveDJRequestFeedback.SENDING) {
                            copy(djRequestFeedback = LiveDJRequestFeedback.SENT)
                        } else this
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (state.value.room?.id == room.id) {
                    if (sendingDJRequest) {
                        update {
                            if (djRequestFeedback == LiveDJRequestFeedback.SENDING) {
                                copy(djRequestFeedback = LiveDJRequestFeedback.FAILED)
                            } else this
                        }
                    } else showError(error)
                }
            } finally {
                if (state.value.room?.id == room.id) update { copy(requestBusy = false) }
            }
            if (sendingDJRequest && state.value.djRequestFeedback == LiveDJRequestFeedback.SENT) {
                // The request listener takes over once it observes the committed request.
                djRequestFeedbackExpiry = viewModelScope.launch {
                    delay(5_000)
                    update {
                        if (this.room?.id == room.id && djRequestFeedback == LiveDJRequestFeedback.SENT) {
                            copy(djRequestFeedback = null)
                        } else this
                    }
                }
            }
        }
    }

    private var schedulingTalkAfterSong = false
    private var localTalkAfterSong: Pair<String, Long>? = null

    fun scheduleTalkAfterSong() {
        val room = state.value.room ?: return
        val target = room.id to room.revision
        if (schedulingTalkAfterSong || !state.value.hosting || localTalkAfterSong == target) return
        schedulingTalkAfterSong = true
        viewModelScope.launch {
            val enabled = !room.talkAfterSongArmed
            try {
                if (enabled) {
                    localTalkAfterSong = target
                    followAppleMusicQueue(room)
                }
                command("talkAfterSong", room.id, mapOf("enabled" to enabled, "revision" to room.revision))
            } catch (error: CancellationException) {
                localTalkAfterSong = null
                throw error
            } catch (error: Exception) {
                localTalkAfterSong = null
                if (state.value.room?.id == room.id) showError(error)
            } finally {
                schedulingTalkAfterSong = false
            }
        }
    }

    private fun holdingNextSongForTalk(room: LiveRoom) =
        room.talkAfterSongArmed || localTalkAfterSong == (room.id to room.revision)

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

    suspend fun chatMentions(roomId: String, prefix: String): List<LiveFields> =
        repository.call("listeners", roomId, mapOf("mentionPrefix" to prefix)).rows("mentions")

    suspend fun editMessage(roomId: String, messageId: String, text: String): Boolean {
        if (state.value.room?.id != roomId) return false
        return try {
            command("editMessage", roomId, mapOf("messageId" to messageId, "text" to text))
            state.value.room?.id == roomId
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (state.value.room?.id == roomId) showError(error)
            false
        }
    }

    fun setChatReaction(messageId: String, emoji: String, addOnly: Boolean = false): Boolean {
        val current = state.value
        val room = current.room ?: return false
        val uid = current.uid ?: return false
        val message = current.messages.find { it.id == messageId && it.supportsReactions } ?: return false
        val selected = uid in message.reactionUsers(emoji)
        val key = messageId to emoji
        if (!room.chatEnabled || (addOnly && selected) || key in current.pendingChatReactions) return false
        val pending = PendingLiveChatReaction(active = !selected)
        update { copy(pendingChatReactions = pendingChatReactions + (key to pending)) }
        viewModelScope.launch {
            try {
                command("setChatReaction", room.id, mapOf("messageId" to messageId, "emoji" to emoji, "active" to pending.active))
                withTimeoutOrNull(3_000L) {
                    state.first { snapshot ->
                        val updated = snapshot.messages.find { it.id == messageId }
                        snapshot.pendingChatReactions[key] !== pending || updated == null ||
                            (uid in updated.reactionUsers(emoji)) == pending.active
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (state.value.room?.id == room.id && state.value.uid == uid) showError(error)
            } finally {
                update {
                    if (pendingChatReactions[key] === pending) {
                        copy(pendingChatReactions = pendingChatReactions - key)
                    } else this
                }
            }
        }
        return true
    }

    fun resolveTalkRequest(
        requestId: String,
        approve: Boolean,
        onResolved: (approved: Boolean) -> Unit,
        onFinished: () -> Unit,
    ) {
        val room = state.value.room ?: return onFinished()
        if (!state.value.hosting || room.voiceMode == "talk") return onFinished()
        val fields = buildMap {
            put("talkRequestId", requestId)
            if (approve) {
                put("mode", "talk")
                put("expectedVoiceSession", room.voiceSession)
            }
        }
        viewModelScope.launch {
            try {
                command(if (approve) "voiceMode" else "declineTalkRequest", room.id, fields)
                if (state.value.room?.id == room.id) onResolved(approve)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (state.value.room?.id == room.id) {
                    if (error is FirebaseFunctionsException && error.code == FirebaseFunctionsException.Code.ABORTED) {
                        onResolved(false)
                    } else {
                        showError(error)
                    }
                }
            } finally {
                onFinished()
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

    fun openInvite(id: String, recipientId: String?, expiresAt: Long?) =
        update { copy(pendingInvitePush = LiveInvitePush(id, recipientId, expiresAt)) }

    suspend fun presentPendingInvite(push: LiveInvitePush?, candidate: LiveFields?) {
        val uid = state.value.uid ?: return
        val roomId = push?.roomId ?: candidate?.text("roomId") ?: return
        try {
            if (push?.recipientId != null && push.recipientId != uid) return
            val invite = (candidate ?: repository.invite(uid, roomId))?.takeIf {
                it.number("expiresAt") > now &&
                    (push?.expiresAt == null || it.number("expiresAt") == push.expiresAt)
            }
            val available = invite != null && try {
                command("inspect", roomId)
                invite.number("expiresAt") > now
            } catch (error: FirebaseFunctionsException) {
                if (!error.isUnavailableDJInvite()) throw error
                false
            }
            currentCoroutineContext().ensureActive()
            val current = state.value
            if (current.uid != uid || current.joiningInvite || current.pendingInvitePush != push) return
            if (push == null && current.shownInvite != null) return
            if (available && invite != null) {
                if (push == null && current.invites.none { it.inviteKey() == invite.inviteKey() }) return
                event("invite_opened", source = if (push == null) "in_app" else "notification")
                update { copy(shownInvite = invite, seenInviteKeys = seenInviteKeys + invite.inviteKey(), inviteError = null) }
            } else if (push != null) {
                val message = LanguageManager.wrapContext(context).getString(R.string.live_dj_invitation_unavailable)
                update { copy(error = message) }
            } else if (candidate != null) {
                update { copy(seenInviteKeys = seenInviteKeys + candidate.inviteKey()) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (push != null && state.value.uid == uid) {
                val message = LanguageManager.wrapContext(context).getString(R.string.live_dj_invitation_open_error)
                update { copy(error = message) }
            }
        } finally {
            if (push != null && currentCoroutineContext().isActive) {
                update { if (uid == this.uid && pendingInvitePush == push) copy(pendingInvitePush = null) else this }
            }
        }
    }

    private fun clearInvite() = update { copy(shownInvite = null, joiningInvite = false, inviteError = null) }

    fun closeInvite() {
        if (!state.value.joiningInvite) clearInvite()
    }

    private fun FirebaseFunctionsException.isUnavailableDJInvite() =
        code == FirebaseFunctionsException.Code.NOT_FOUND || code == FirebaseFunctionsException.Code.FAILED_PRECONDITION

    private fun showInviteJoinError(error: Exception) {
        pendingJoin = null
        val unavailable = (error as? FirebaseFunctionsException)?.isUnavailableDJInvite() == true
        update {
            copy(
                joiningInvite = false,
                inviteError = if (unavailable) R.string.live_dj_invitation_unavailable
                    else R.string.live_dj_invitation_join_error,
            )
        }
    }

    fun inviteDecision(invite: LiveFields, accept: Boolean) {
        val current = state.value
        if (current.shownInvite?.inviteKey() != invite.inviteKey() || current.joiningInvite) return
        val id = invite.text("roomId")
        val fields = mapOf("inviteExpiresAt" to invite.number("expiresAt"))
        if (accept) {
            if (current.room?.id?.let { it != id } == true || operation?.isActive == true || current.access == null) return
            if (invite.number("expiresAt") <= now) {
                update { copy(inviteError = R.string.live_dj_invitation_unavailable) }
                return
            }
            update { copy(joiningInvite = true, inviteError = null) }
            join(id, invite = invite)
        } else {
            clearInvite()
            viewModelScope.launch {
                try {
                    if (repository.invite(current.uid ?: return@launch, id)?.inviteKey() == invite.inviteKey() &&
                        state.value.uid == current.uid
                    ) command("declineDJInvite", id, fields)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) { }
            }
        }
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
        val current = state.value
        val room = current.room ?: return
        val uid = current.uid ?: return
        if (!room.chatEnabled || (text.isBlank() && gif == null)) return
        val messageId = UUID.randomUUID().toString()
        action("chat", buildMap {
            put("requestId", messageId)
            put("text", text.trim())
            gif?.let { put("gifURL", it) }
        }) {
            viewModelScope.launch { recoverSentChatMessage(room.id, uid, messageId) }
            done()
        }
    }

    private suspend fun recoverSentChatMessage(roomId: String, uid: String, messageId: String) {
        // Give the normal room query a chance to deliver the committed write.
        val delivered = withTimeoutOrNull(400L) {
            state.first { it.room?.id != roomId || it.uid != uid || it.messages.any { row -> row.id == messageId } }
        }
        if (delivered != null) return
        val message = try {
            repository.sentMessage(roomId, messageId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The room query can still deliver the message.
            return
        }
        if (message == null || message.author.uid != uid) return
        update {
            if (room?.id == roomId && this.uid == uid) withConfirmedChatMessage(message) else this
        }
    }

    fun heart() {
        val current = state.value
        val room = current.room?.takeIf { it.status == "playing" && it.current != null } ?: return
        if (current.hosting && !room.canLikeFromMiniPlayer(current.uid, now)) return
        if (heartRevision != room.revision) {
            heartJob?.cancel()
            heartsPending = 0
            heartRevision = room.revision
        }
        heartsPending = (heartsPending + 1).coerceAtMost(12)
        if (heartJob?.isActive == true) return
        var confirmed = confirmedHeartRevision == room.revision || current.membership.number("likedRevision") == room.revision
        if (!confirmed) update { copy(optimisticLikedRevision = room.revision) }
        if (!confirmed && !current.hosting && room.chatEnabled && current.uid != null) {
            val username = profile.value?.takeIf { it.id == current.uid }?.username.orEmpty().trimStart('@')
            val handle = username.takeIf { it.isNotEmpty() }?.let { "@$it" }.orEmpty()
            val id = "pending-like:${UUID.randomUUID()}"
            val text = if (handle.isEmpty()) context.getString(R.string.live_you_liked_song)
                else context.getString(R.string.live_user_liked_song, handle)
            val notice = LiveMessage(id, mapOf(
                "uid" to current.uid, "displayName" to handle, "kind" to "songLiked",
                "text" to text, "revision" to room.revision, "createdAt" to now,
            ))
            update { copy(pendingLikeNotice = PendingLiveLikeNotice(notice)) }
            viewModelScope.launch {
                delay(12_000)
                update { if (pendingLikeNotice?.message?.id == id) copy(pendingLikeNotice = null) else this }
            }
        }
        heartJob = viewModelScope.launch {
            var sentBatch = false
            while (heartsPending > 0 && state.value.room?.id == room.id && state.value.room?.revision == room.revision) {
                if (sentBatch || confirmed) delay(1_000)
                if (state.value.room?.id != room.id || state.value.room?.revision != room.revision) break
                val count = heartsPending
                heartsPending = 0
                sentBatch = true
                try {
                    val response = command("like", room.id, mapOf("revision" to room.revision, "heartCount" to count, "heartSender" to heartSender))
                    if (state.value.room?.id != room.id || state.value.room?.revision != room.revision) break
                    if (!confirmed) {
                        confirmed = true
                        confirmedHeartRevision = room.revision
                        update {
                            val notice = pendingLikeNotice?.takeUnless { response["firstLike"] == false }
                            copy(pendingLikeNotice = notice?.copy(batchId = response.text("noticeBatchId").ifBlank { null }))
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (!confirmed) update {
                        if (this.room?.id == room.id && this.room.revision == room.revision) {
                            copy(pendingLikeNotice = null, optimisticLikedRevision = null)
                        } else this
                    }
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
        val room = repository.room(track.roomId) ?: error(context.getString(R.string.live_couldnt_load_this_set))
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
            update { copy(error = context.getString(R.string.live_the_queue_changed_try_again)) }
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

    suspend fun exportSetlist(id: String): String {
        try {
            return repository.exportSetlist(id).also { event("setlist_export_result", "success", "spotify", id) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            event("setlist_export_result", "failed", "spotify", id)
            throw error
        }
    }
    suspend fun previewUrl(track: LiveTrack): String? {
        if (track.playsFromSource) return repository.sourceAudioUrl(track)
        val song = track.song
        return song.previewUrl ?: player.lookupPreviewUrl(
            song.id, song.name, song.artistName, song.isrc, song.appleMusicId, song.durationMs, song.albumName,
        )
    }

    suspend fun prepareQueueTrack(track: LiveTrack): LiveTrack {
        if (!track.playsFromSource) return track
        val url = repository.sourceAudioUrl(track) ?: error(context.getString(R.string.live_this_track_is_unavailable_for_live))
        if (track.song.source != TrackSource.BANDCAMP) return track
        val probe = newLiveStreamPlayer(context)
        try {
            probe.prepareLiveStream(url)
            val duration = probe.duration
            check(duration in 1_000L..7_200_000L) { context.getString(R.string.live_this_track_is_unavailable_for_live) }
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
    suspend fun share(id: String, setup: Boolean): String = try {
        command(
            "reserveShareLink", id, mapOf("shortLink" to true, "existingRoomOnly" to !setup)
        ).text("shareUrl")
    } catch (error: Exception) {
        if (setup || error is CancellationException) throw error
        "https://corus.fm/live/$id"
    }

    private fun mount(room: LiveRoom) {
        event("room_opened", source = "room_entry", roomId = room.id)
        heartJob?.cancel()
        heartRevision = 0
        confirmedHeartRevision = 0
        heartsPending = 0
        roomJobs.forEach(Job::cancel)
        roomJobs.clear()
        queueUpdateJob?.cancel()
        cancelEffectSaves()
        lastTick = 0
        update {
            copy(
                room = room, ended = null, minimized = false, pendingChatEnabled = null, pendingChatReactions = emptyMap(),
                recoverableHostRoom = null, muted = false, messages = emptyList(),
                listenerCount = 0, membership = emptyMap(), audioError = null, queueUpdating = false,
                incomingHearts = 0, pendingLikeNotice = null, optimisticLikedRevision = null,
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
                if (!stillHosting || next.talkAfterSongArmed || next.voiceMode == "talk" ||
                    localTalkAfterSong != (next.id to next.revision)
                ) localTalkAfterSong = null
                if (!stillHosting && previous?.host?.uid == uid) cancelEffectSaves()
                update {
                    copy(
                        room = next,
                        incomingHearts = incomingHearts + (previous?.let { next.remoteHeartsSince(it, heartSender) } ?: 0),
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
            repository.messages(id).catch { error -> showError(error) }.collectLatest { messages ->
                if (state.value.room?.id != id) return@collectLatest
                fun withAvatars(rows: List<LiveMessage>) = rows.map { message ->
                    val sender = message.author
                    val profile = knownAuthors[sender.uid]
                    val avatar = profile?.avatar ?: sender.avatar
                    if (avatar == sender.avatar) message else message.copy(author = sender.copy(avatar = avatar))
                }
                update { copy(messages = withAvatars(messages)) }
                val missing = messages.asSequence()
                    .filter { it.kind != "listenerJoined" && !it.isSongLikeNotice }
                    .map { it.author.uid }
                    .filter { it.isNotBlank() && it !in knownAuthors }
                    .distinct()
                    .toList()
                if (missing.isEmpty()) return@collectLatest
                try {
                    val profiles = repository.chatAuthors(missing)
                    missing.forEach { knownAuthors[it] = profiles[it] }
                    if (state.value.room?.id == id) update { copy(messages = withAvatars(this.messages)) }
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
                    update { copy(error = context.getString(R.string.live_you_are_no_longer_in_this_room)) }
                } else update { copy(membership = fields) }
            }
        }
        roomJobs += viewModelScope.launch {
            repository.ownRequest(id, "djRequests", uid).catch { }.collect { request ->
                if (state.value.room?.id == id) update {
                    val changed = request.isNotEmpty() &&
                        (request.number("createdAt") != myDJRequest.number("createdAt") ||
                            request.text("status") != myDJRequest.text("status"))
                    copy(myDJRequest = request, djRequestFeedback = if (changed) null else djRequestFeedback)
                }
            }
        }
        roomJobs += viewModelScope.launch {
            repository.ownRequest(id, "songRequests", uid).catch { }.collect { request ->
                if (state.value.room?.id == id) update { copy(mySongRequest = request) }
            }
        }
        if (initialHost) state.value.room?.let { observeHostRecords(it, uid) }
        roomJobs += viewModelScope.launch {
            var pausedSpotifyForTalk: Long? = null
            while (state.value.room?.id == id) {
                val room = state.value.room ?: break
                val serverNow = now
                val holdSpotify = room.pausedAt == null && expectedUri != null && holdingNextSongForTalk(room)
                if (holdSpotify && room.deadline - serverNow <= 1_000 && pausedSpotifyForTalk != room.revision) {
                    spotify.pauseImmediately()
                    pausedSpotifyForTalk = room.revision
                } else if (!holdSpotify) {
                    pausedSpotifyForTalk = null
                }
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
                delay(if (holdSpotify && room.deadline - serverNow <= 2_000) 100 else 500)
            }
        }
        roomJobs += viewModelScope.launch {
            while (state.value.room?.id == id) {
                val room = state.value.room ?: break
                maintain(room, "presence", mapOf("hostHandoffReady" to foreground))
                val current = state.value
                val latest = current.room
                if (latest?.id == id && current.hosting && !current.muted && foreground) synchronize(latest)
                delay(15_000)
            }
        }
        roomJobs += viewModelScope.launch {
            while (state.value.room?.id == id) {
                delay(8_000)
                val current = state.value
                val latest = current.room
                if (latest?.id == id && !current.hosting && !current.muted && foreground) synchronize(latest)
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
        if (holdingNextSongForTalk(room)) return null
        val current = state.value
        if (!current.hosting || current.access?.appleMusicHostAutoplay != true ||
            current.muted || welcomePlaying || auditionPlaying || operation?.isActive == true ||
            room.id != current.room?.id || room.status != "playing" || room.pausedAt != null ||
            room.voiceMode == "talk" || room.pendingSkip != null || confirmedRevision != room.revision ||
            room.current?.playsFromSource != false || liveProvider != MusicService.APPLE_MUSIC
        ) return null
        val nativeQueue = applePlayer?.takeIf { it.playerError == null } as? AppleMusicLiveQueue ?: return null
        val next = room.nextTrack?.takeUnless { it.playsFromSource } ?: return null
        return AppleQueueTarget(nativeQueue, room.id, room.revision, room.queueVersion, next)
    }

    // Let MusicKit cross the track boundary before publishing the new room clock.
    // The foreground host can fall back to a scheduled advance. In the background,
    // only publish an advance after MusicKit confirms that the staged track started.
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
        if (target == null) return false
        if (appleAdvanceConfirmed == target) return true
        if (foreground && now >= room.deadline + APPLE_QUEUE_GRACE_MS) return false
        val position = target.player.stagedPosition(target.track.id, target.revision)
        if (position == null) return now >= room.deadline && target.player.hasStagedNext(target.track.id, target.revision)
        if (position >= target.track.duration) return false
        if (state.value.access?.appleMusicHostEarlyAdvance != true && now - position < room.deadline - 4_000L) return true

        appleTrackKey = target.track.appleKey
        initialTrackRevision = room.revision + 1
        val confirmed = try {
            withTimeoutOrNull(APPLE_QUEUE_GRACE_MS) {
                command("confirmAppleMusicAdvance", room.id, mapOf(
                    "revision" to room.revision,
                    "queueVersion" to room.queueVersion,
                    "trackId" to target.track.id,
                    "trackSource" to target.track.song.source.raw,
                    "positionMs" to position,
                ))
                true
            } == true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (confirmed) {
            appleAdvanceConfirmed = target
            return true
        }
        if (!foreground) {
            // Keep streaming across a transient network failure and check again
            // against the latest room snapshot and native playback position.
            delay(750)
            return true
        }
        return false
    }

    private var listeningSession: Pair<String, Long>? = null

    private fun recordListenerPlayback(roomId: String, source: String) {
        if (state.value.hosting || listeningSession != null || state.value.room?.id != roomId) return
        listeningSession = source to SystemClock.elapsedRealtime()
        event("playback_started", source = source, roomId = roomId)
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
                        val alreadyStarted = initialTrackRevision == room.revision &&
                            sourceTrackId == track.id && sourcePlayer?.isPlaying == true
                        if (alreadyStarted) initialTrackRevision = null
                        else playSourceTrack(track, { room.position(now) }, force || confirmedRevision != room.revision)
                        confirmedRevision = room.revision
                        recordListenerPlayback(room.id, track.song.source.raw)
                        update { copy(audioReady = true, audioError = null) }
                        return@withLock
                    }
                    releaseSourcePlayer()
                    val hosting = state.value.hosting
                    fun listenerTarget(): Long? {
                        val state = state.value
                        val latest = state.room ?: return null
                        val now = now
                        return latest.position(now).takeIf {
                            !state.hosting && !state.muted && !welcomePlaying && !auditionPlaying &&
                                latest.id == room.id && latest.revision == room.revision &&
                                latest.current?.id == track.id && latest.startedAt == room.startedAt &&
                                latest.pendingSkip == null && latest.musicShouldPlay(now)
                        }
                    }
                    when (liveProvider) {
                        MusicService.APPLE_MUSIC -> {
                            val startedHere = initialTrackRevision == room.revision && appleTrackKey == track.appleKey &&
                                applePlayer?.isPlaying == true
                            if (startedHere) initialTrackRevision = null
                            else if (applePlayer?.playbackState != Player.STATE_ENDED || confirmedRevision != room.revision || force) {
                                playAppleTrack(
                                    track, { room.position(now) },
                                    forceSeek = confirmedRevision != room.revision || (hosting && force),
                                    listenerTarget = if (!hosting && confirmedRevision == room.revision) ::listenerTarget else null,
                                )
                            }
                            confirmedRevision = room.revision
                            recordListenerPlayback(room.id, MusicService.APPLE_MUSIC.value)
                            update { copy(audioReady = true, audioError = null) }
                            return@withLock
                        }
                        MusicService.SPOTIFY -> releaseApplePlayer()
                        else -> {
                            update { copy(audioReady = false, audioError = context.getString(R.string.live_connect_a_streaming_provider_to_hear_the_music)) }
                            return@withLock
                        }
                    }
                    if (holdingNextSongForTalk(room) && room.deadline - now <= 1_000) {
                        if (expectedUri != null) spotify.pauseImmediately()
                        return@withLock
                    }
                    if (!foreground && !spotify.isConnected) return@withLock
                    val uri = resolve(track)
                    if (uri == null) {
                        if (expectedUri != null) spotify.pause()
                        update { copy(audioReady = false, audioError = context.getString(R.string.live_song_unavailable_on_provider, "Spotify")) }
                        return@withLock
                    }
                    if (initialTrackRevision == room.revision && expectedUri == uri) {
                        initialTrackRevision = null
                    } else if (confirmedRevision != room.revision || (hosting && !spotify.isConnected)) {
                        prepareTrack(track, { room.position(now) }, uri)
                    } else {
                        val sameTrack = spotify.syncLiveTrack(uri, force, targetPosition = {
                            val latest = state.value.room
                            if (!hosting) listenerTarget()
                            else if (latest?.id == room.id && latest.revision == room.revision &&
                                !(latest.pendingSkip != null && state.value.hosting) &&
                                !state.value.muted && !welcomePlaying && !auditionPlaying && latest.musicShouldPlay(now)
                            ) latest.position(now) else null
                        }, confirmDrift = !hosting, onCorrectionFailed = { gap ->
                            recordDriftCorrection("spotify", room.id, gap, false)
                        }) { gap ->
                            recordDriftCorrection("spotify", room.id, gap, true)
                        } ?: return@withLock
                        if (!sameTrack) prepareTrack(track, { room.position(now) }, uri, forceSpotifyPlay = true)
                    }
                    confirmedRevision = room.revision
                    recordListenerPlayback(room.id, MusicService.SPOTIFY.value)
                    update { copy(audioReady = true, audioError = null) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when {
                    room.current?.playsFromSource == true -> {
                        releaseSourcePlayer()
                        context.getString(R.string.live_couldnt_play_this_live_track_try_again)
                    }
                    liveProvider == MusicService.APPLE_MUSIC -> {
                        releaseApplePlayer()
                        (error as? IllegalStateException)?.message ?: context.getString(R.string.live_provider_sync_failed, "Apple Music")
                    }
                    else -> context.getString(R.string.live_provider_sync_failed, "Spotify")
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
        val uri = knownUri ?: resolve(track) ?: error(context.getString(R.string.live_song_unavailable_on_provider, "Spotify"))
        claimLivePlayback()
        expectedUri = uri
        if (forceSpotifyPlay || !spotify.isConnected || !matchesSpotifyUri(uri) || !spotify.isPlaying.value) {
            onStartingProvider()
            spotify.play(uri.substringAfterLast(':'), uri, replaceQueue = false, confirmPlayback = true)
        }
        val target = position().coerceIn(0, track.duration.toLong() - 1)
        val observed = spotify.positionSeconds.value * 1_000
        if (target > 250 || observed > 750) {
            check(spotify.seek(target / 1_000.0)) { context.getString(R.string.live_provider_sync_failed, "Spotify") }
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
        check(paused || !spotify.isConnected) { context.getString(R.string.live_couldnt_pause_spotify) }
        expectedUri = null
    }

    private val LiveTrack.appleKey get() = song.source to id

    private suspend fun playAppleTrack(
        track: LiveTrack,
        position: () -> Long,
        forceSeek: Boolean,
        restart: Boolean = false,
        listenerTarget: (() -> Long?)? = null,
    ) {
        claimLivePlayback()
        pauseLiveSpotify()
        val current = applePlayer
        val audio = if (!restart && appleTrackKey == track.appleKey && current != null &&
            current.playerError == null && current.playbackState != Player.STATE_ENDED
        ) current else {
            releaseApplePlayer()
            check(appleMusic.isConnected()) { context.getString(R.string.live_connect_apple_music_to_hear_the_music) }
            val candidate = appleMusic.createPlayer(
                track.song.toQueuedTrack(), MediaItem.Builder().setMediaId(track.id).build(),
                interactive = false, explicit = false, onNext = {}, onPrevious = {},
            ) ?: error(context.getString(R.string.live_song_unavailable_on_provider, "Apple Music"))
            applePlayer = candidate
            appleTrackKey = track.appleKey
            candidate.play()
            candidate.prepare()
            candidate
        }
        val ready = withTimeoutOrNull(15_000L) {
            while (audio.playbackState != Player.STATE_READY) {
                audio.playerError?.let { throw it }
                check(audio.playbackState != Player.STATE_ENDED) { context.getString(R.string.live_apple_music_finished_the_song_before_it_could_sync) }
                delay(50)
            }
            true
        }
        check(ready == true) { context.getString(R.string.live_apple_music_took_too_long_to_start_try_again) }
        if (listenerTarget != null && audio === current) {
            audio.play()
            fun sample(): LiveDriftSample? {
                if (applePlayer !== audio || !audio.isPlaying || audio.currentMediaItem?.mediaId != track.id) {
                    return null
                }
                val position = audio.currentPosition
                val target = listenerTarget() ?: return null
                return LiveDriftSample(position, target)
            }
            val first = sample() ?: return
            val correction = confirmLiveListenerDrift(first, ::sample) ?: return
            if (!audio.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
            audio.seekTo(correction.target)
            val succeeded = withTimeoutOrNull(3_000L) {
                while (true) {
                    val target = listenerTarget() ?: return@withTimeoutOrNull false
                    if (audio.playerError != null || applePlayer !== audio) return@withTimeoutOrNull false
                    if (kotlin.math.abs(audio.currentPosition - target) <= 1_000L) return@withTimeoutOrNull true
                    delay(50)
                }
            } == true
            recordDriftCorrection(MusicService.APPLE_MUSIC.value, state.value.room?.id, kotlin.math.abs(correction.gap), succeeded)
            return
        }
        val target = position().coerceIn(0, track.duration.toLong() - 1)
        val threshold = if (forceSeek) 750L else 1_500L
        if (kotlin.math.abs(audio.currentPosition - target) > threshold) {
            check(audio.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                context.getString(R.string.live_apple_music_couldnt_seek_to_the_rooms_playback_position)
            }
            audio.seekTo(target)
            val synced = withTimeoutOrNull(3_000L) {
                while (kotlin.math.abs(audio.currentPosition - position()) > 1_500L) {
                    audio.playerError?.let { throw it }
                    delay(50)
                }
                true
            }
            check(synced == true) { context.getString(R.string.live_provider_sync_failed, "Apple Music") }
        }
        audio.play()
    }

    private fun recordDriftCorrection(provider: String, roomId: String?, gap: Long, succeeded: Boolean) {
        analytics.logEvent(if (succeeded) "live_sync_corrected" else "live_sync_correction_failed", buildMap {
            put("source", provider)
            put("milliseconds", gap.toInt())
            roomId?.let { put("room_id", it) }
        })
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
            val url = repository.sourceAudioUrl(track) ?: error(context.getString(R.string.live_this_track_is_unavailable_for_live))
            newLiveStreamPlayer(context).also {
                sourcePlayer = it
                sourceTrackId = track.id
                try {
                    it.prepareLiveStream(url, position().coerceIn(0, track.duration.toLong() - 1))
                    it.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            val room = state.value.room
                            if (sourcePlayer === it && room?.current?.id == track.id) {
                                update { copy(audioReady = false, audioError = context.getString(R.string.live_couldnt_play_this_live_track_try_again)) }
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
            update { copy(micEnabled = false, voiceLevels = emptyMap(), unmutedMicrophoneIDs = emptySet()) }
            return
        }
        if (state.value.muted) {
            voice.disconnect()
            update { copy(voiceLevels = emptyMap(), unmutedMicrophoneIDs = emptySet()) }
            return
        }
        val publish = state.value.micEnabled && (state.value.hosting || state.value.dj != null)
        update { copy(unmutedMicrophoneIDs = voice.unmutedMicrophoneIDs(session)) }
        voiceJob = viewModelScope.launch {
            try {
                voice.connect(room.id, session, publish)
                while (state.value.room?.voiceSession == session) {
                    update {
                        copy(voiceLevels = voice.levels(), unmutedMicrophoneIDs = voice.unmutedMicrophoneIDs(session))
                    }
                    delay(100)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                voice.disconnect()
                update {
                    copy(
                        micEnabled = false, voiceLevels = emptyMap(), unmutedMicrophoneIDs = emptySet(),
                        audioError = context.getString(R.string.live_couldnt_connect_to_live_voice),
                    )
                }
            }
        }
    }

    private suspend fun command(action: String, id: String, fields: LiveFields = emptyMap()): LiveFields {
        val before = System.currentTimeMillis()
        val started = SystemClock.elapsedRealtime()
        fun record(outcome: String) {
            if (action in measuredActions) {
                analytics.logEvent("live_command_result", mapOf(
                    "source" to "room_api", "action" to action, "room_id" to id,
                    "outcome" to outcome, "milliseconds" to SystemClock.elapsedRealtime() - started,
                ))
            }
        }
        val result = try {
            repository.call(action, id, fields).also { record("success") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            record("failed")
            throw error
        }
        val elapsed = System.currentTimeMillis() - before
        val server = result.number("serverNow")
        if (server > 0 && elapsed in 0..5_000) offset = server - (before + elapsed / 2)
        return result
    }

    private fun runOperation(
        progress: LiveProgress? = null,
        onError: ((Exception) -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            update { copy(progress = progress, error = null) }
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (onError != null) onError(error) else showError(error)
            } finally {
                update { copy(progress = null, joiningRoom = null, joinReady = false) }
            }
        }
    }

    private fun requireSpotify() {
        if (SpotifyPlaybackService.isSpotifyAppInstalled(context)) return
        update { copy(spotifyInstallNeeded = true) }
        error(context.getString(R.string.live_spotify_is_required_to_use_live))
    }

    private fun showError(error: Throwable) = update {
        copy(error = when (error) {
            is FirebaseFunctionsException -> when (error.details.liveFields()?.text("reason")) {
                "live-hosting-paused" -> context.getString(R.string.live_hosting_paused)
                else -> null
            }
            is IllegalStateException -> error.localizedMessage
            else -> null
        } ?: context.getString(R.string.live_something_went_wrong_try_again))
    }

    private fun closeRoom(ended: LiveRoom?) {
        listeningSession?.let { session ->
            analytics.logEvent("live_listening_finished", mapOf(
                "source" to session.first,
                "milliseconds" to SystemClock.elapsedRealtime() - session.second,
                "outcome" to if (ended != null) "ended" else "left",
            ))
        }
        listeningSession = null
        resetRoom()
        update {
            copy(room = null, ended = ended, minimized = false, pendingChatEnabled = null, pendingChatReactions = emptyMap(), messages = emptyList(),
                audioReady = false, audioError = null, micEnabled = false, voiceLevels = emptyMap(),
                unmutedMicrophoneIDs = emptySet(), membership = emptyMap(),
                incomingHearts = 0, pendingLikeNotice = null, optimisticLikedRevision = null,
                pendingDJs = emptyList(), djRequests = emptyList(), myDJRequest = emptyMap(), djRequestFeedback = null,
                mySongRequest = emptyMap(), requestBusy = false, queueUpdating = false,
                effectSelection = LiveEffectSelection())
        }
    }

    private fun resetRoom() {
        listeningSession = null
        requestJob?.cancel()
        requestJob = null
        djRequestFeedbackExpiry?.cancel()
        djRequestFeedbackExpiry = null
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

    fun event(name: String, outcome: String? = null, source: String = "spotify", roomId: String? = null) {
        analytics.logEvent("live_$name", buildMap {
            put("source", source)
            outcome?.let { put("outcome", it) }
            roomId?.let { put("room_id", it) }
        })
    }

    private inline fun update(crossinline change: LiveUiState.() -> LiveUiState) =
        _state.update { it.change() }

    companion object {
        private val measuredActions = setOf(
            "join", "leave", "end", "chat", "like", "effect", "voiceMode", "queue", "skip",
            "completeSkip", "playQueuedTrack", "renameRoom", "saveLiveTrack", "unsaveLiveTrack",
            "reserveShareLink", "reportRoom", "report", "removeMessage", "removeListener", "setChatReaction",
            "inviteDJs", "cancelDJInvite", "acceptDJInvite", "declineDJInvite", "requestDJ",
            "cancelDJRequest", "approveDJRequest", "declineDJRequest", "djQueue", "stopDJing",
            "moveDJToListeners", "removeParticipant", "requestSong", "cancelSongRequest",
            "dismissSongRequest", "queueSongRequest", "requestTalk", "declineTalkRequest",
            "cancelTalkRequest", "offerHost", "acceptHost", "declineHost", "stepDown",
            "setChatEnabled", "setRequestPermission",
        )

        private const val APPLE_QUEUE_GRACE_MS = 2_500L
        fun saveId(track: LiveTrack): String = MessageDigest.getInstance("SHA-256")
            .digest("${track.song.source.raw}:${track.id}".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

private fun Boolean?.orFalse(): Boolean = this == true

internal data class LiveDriftSample(val position: Long, val target: Long) {
    val gap get() = target - position
}

internal suspend fun confirmLiveListenerDrift(
    first: LiveDriftSample,
    sample: suspend () -> LiveDriftSample?,
): LiveDriftSample? {
    if (first.position < 0 || kotlin.math.abs(first.gap) <= 1_000L) return null
    delay(400)
    val second = sample() ?: return null
    return second.takeIf {
        it.position > first.position && kotlin.math.abs(it.gap) > 1_000L &&
            (it.gap > 0) == (first.gap > 0)
    }
}

/** True for a matching track, false when it needs to be cued again, null when sync should wait. */
internal suspend fun SpotifyPlaybackService.syncLiveTrack(
    expectedUri: String,
    force: Boolean,
    targetPosition: () -> Long?,
    confirmDrift: Boolean = true,
    onCorrectionFailed: (Long) -> Unit = {},
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
    if (confirmDrift) {
        if (snapshot.isPaused) return true
        val correction = confirmLiveListenerDrift(LiveDriftSample(snapshot.playbackPosition, target)) {
            val latest = try {
                refreshState()
            } catch (_: SpotifyPlaybackError.ApiError) {
                null
            } ?: return@confirmLiveListenerDrift null
            if (latest.isPaused || !SpotifyPlaybackService.spotifyURIsMatch(latest.track?.uri.orEmpty(), expectedUri)) {
                return@confirmLiveListenerDrift null
            }
            val latestTarget = targetPosition() ?: return@confirmLiveListenerDrift null
            LiveDriftSample(latest.playbackPosition, latestTarget)
        } ?: return true
        currentCoroutineContext().ensureActive()
        val gap = kotlin.math.abs(correction.gap)
        if (seek(correction.target / 1_000.0)) onDriftCorrected(gap) else onCorrectionFailed(gap)
        return true
    }
    if (force || difference > 1_800L) {
        currentCoroutineContext().ensureActive()
        val seekTarget = targetPosition() ?: return null
        check(seek(seekTarget / 1_000.0)) { "Couldn’t sync Spotify." }
        if (difference > 1_800L) onDriftCorrected(difference)
    }
    return true
}
