package fm.corus.android.ui.screens.messaging

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.local.MessageLocalStore
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MessageFailureReason
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
import fm.corus.android.data.model.MessagingRestriction
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.HashtagSuggestion
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.ExploreRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

/** Keep the history-fetch flag once older pages exist; the live tail is not the whole thread. */
internal fun hasMoreAfterLiveSnapshot(
    previous: Boolean,
    liveWindowCount: Int,
    hasRetainedOlder: Boolean,
    pageSize: Long,
): Boolean {
    if (liveWindowCount == 0) return false
    if (hasRetainedOlder) return previous
    return liveWindowCount.toLong() >= pageSize
}

/** Newer copies win when the live window overlaps a fetched history page. */
internal fun mergeMessagePages(
    preferred: List<CymbalMessage>,
    fallback: List<CymbalMessage>,
): List<CymbalMessage> {
    val byId = LinkedHashMap<String, CymbalMessage>(preferred.size + fallback.size)
    fallback.forEach { byId[it.id] = it }
    preferred.forEach { byId[it.id] = it }
    return byId.values.sortedByDescending { it.createdAt }
}

@HiltViewModel
class MessageThreadViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val exploreRepository: ExploreRepository,
    private val postRepository: fm.corus.android.data.repository.PostRepository,
    private val remoteConfigService: RemoteConfigService,
    private val gifRepository: fm.corus.android.data.repository.GifRepository,
    val nowPlayingManager: fm.corus.android.domain.NowPlayingManager,
    private val analyticsService: fm.corus.android.service.AnalyticsService,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val messageLocalStore = MessageLocalStore(context)

    val gifSupport: Boolean
        get() = remoteConfigService.gifSupport

    /** Send-side gate for the Artist/Album/Director items in the composer "+" menu. */
    val entityShareEnabled: Boolean
        get() = remoteConfigService.entityShareEnabled

    val groupMessagingEnabled: Boolean
        get() = remoteConfigService.groupMessagingEnabled

    // Group metadata (null/non-group for 1:1 threads) + resolved member profiles
    // for the header title, run-grouped sender labels/avatars, and reply names.
    private val _groupInfo = MutableStateFlow<MessageRepository.GroupThreadInfo?>(null)
    val groupInfo: StateFlow<MessageRepository.GroupThreadInfo?> = _groupInfo.asStateFlow()

    private val _membersById = MutableStateFlow<Map<String, fm.corus.android.data.model.CymbalUser>>(emptyMap())
    val membersById: StateFlow<Map<String, fm.corus.android.data.model.CymbalUser>> = _membersById.asStateFlow()

    private val _serverMessages = MutableStateFlow<List<CymbalMessage>>(emptyList())
    private val _olderMessages = MutableStateFlow<List<CymbalMessage>>(emptyList())
    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()
    private val _hasMoreMessages = MutableStateFlow(true)
    val hasMoreMessages: StateFlow<Boolean> = _hasMoreMessages.asStateFlow()
    /// False until the live window has decided `hasMore`, so a short thread
    /// can paint the profile card in the same frame as the bubbles.
    private val _liveWindowReady = MutableStateFlow(false)
    val liveWindowReady: StateFlow<Boolean> = _liveWindowReady.asStateFlow()

    /**
     * Optimistic copies of messages we've sent, keyed by client message id.
     *
     * An entry is removed ONLY by [startListening], once the canonical Firestore
     * doc for that id is actually in the snapshot. Do NOT drop it when the send
     * callable returns: the ack and the snapshot are separate round-trips, so
     * between them the message would exist in neither `pending` nor
     * `_serverMessages` and would blink out of the merged [messages] list. In the
     * UI that shrinks the reverseLayout LazyColumn by an item, which re-anchors it
     * and hides the just-sent bubble behind the composer until the snapshot lands.
     * The server reuses our clientMessageId as the doc id, so the prune always
     * matches and holding the copy longer can't duplicate a bubble.
     */
    private val _pendingMessages = MutableStateFlow<Map<String, CymbalMessage>>(emptyMap())

    /** Merged server + unconfirmed pending messages, reversed for reverseLayout LazyColumn. */
    val messages: StateFlow<List<CymbalMessage>> = combine(
        _serverMessages,
        _olderMessages,
        _pendingMessages,
    ) { server, older, pending ->
        val history = mergeMessagePages(server, older)
        val confirmedIds = history.map { it.id }.toSet()
        val unconfirmed = pending.values.filter { it.id !in confirmedIds }
        (history + unconfirmed).sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Starts true so the first frame of an opened thread cannot paint a blank
    // message list before loadMessages() runs. Cleared by the first publishable
    // snapshot (or a setup / listener failure).
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasLoadError = MutableStateFlow(false)
    val hasLoadError: StateFlow<Boolean> = _hasLoadError.asStateFlow()

    private val _otherUsername = MutableStateFlow("")
    val otherUsername: StateFlow<String> = _otherUsername.asStateFlow()

    private val _otherDisplayName = MutableStateFlow("")
    val otherDisplayName: StateFlow<String> = _otherDisplayName.asStateFlow()

    private val _otherAvatarURL = MutableStateFlow<String?>(null)
    val otherAvatarURL: StateFlow<String?> = _otherAvatarURL.asStateFlow()

    private val _otherAvatarThumbURL = MutableStateFlow<String?>(null)
    val otherAvatarThumbURL: StateFlow<String?> = _otherAvatarThumbURL.asStateFlow()

    private val _artistsInCommonCount = MutableStateFlow<Int?>(null)
    val artistsInCommonCount: StateFlow<Int?> = _artistsInCommonCount.asStateFlow()

    /** True when opened without a thread id (profile Message on an inbox miss). */
    private val _openedAsNewCompose = MutableStateFlow(false)
    val openedAsNewCompose: StateFlow<Boolean> = _openedAsNewCompose.asStateFlow()

    /** Peer id for inbox-miss compose; drives optimistic [threadAccess] + send fallback. */
    private val _composePeerUserId = MutableStateFlow("")

    val currentUserId: String? get() = authRepository.currentUserId

    private val _replyToMessage = MutableStateFlow<CymbalMessage?>(null)
    val replyToMessage: StateFlow<CymbalMessage?> = _replyToMessage.asStateFlow()

    private val _editingMessage = MutableStateFlow<CymbalMessage?>(null)
    val editingMessage: StateFlow<CymbalMessage?> = _editingMessage.asStateFlow()

    private val _recipientUnread = MutableStateFlow(0)
    val recipientUnread: StateFlow<Int> = _recipientUnread.asStateFlow()

    private val _myReadReceiptsEnabled = MutableStateFlow(true)
    val myReadReceiptsEnabled: StateFlow<Boolean> = _myReadReceiptsEnabled.asStateFlow()

    /** The thread id once resolved (may differ from the nav arg when entering
     *  from a profile, where the thread is created lazily). Drives the screen's
     *  active-thread tracking + notification suppression. */
    private val _resolvedThreadId = MutableStateFlow<String?>(null)
    val resolvedThreadId: StateFlow<String?> = _resolvedThreadId.asStateFlow()

    /** The caller's own inbox row for this conversation; null until one is known. */
    private val _threadRow = MutableStateFlow<MessageRepository.ThreadRowSnapshot?>(null)

    private val _messagingRestriction = MutableStateFlow<MessagingRestriction?>(null)
    val messagingRestriction: StateFlow<MessagingRestriction?> = _messagingRestriction.asStateFlow()

    /**
     * Whether this conversation may be shown, and the screen's whole answer to
     * that question — every way in lands here, including a tapped push and a
     * deep link, neither of which passes through the inbox.
     *
     * Inbox-miss compose opens immediately (peer card from cache) while
     * getOrCreate runs; create failure / the row still override.
     */
    val threadAccess: StateFlow<ThreadAccess> = combine(
        _threadRow,
        userRepository.blockedIds,
        _openedAsNewCompose,
        _composePeerUserId,
    ) { row, blockedIds, composeFromPeer, peerId ->
        resolveThreadAccess(
            row = row,
            blockedIds = blockedIds,
            isBanned = { userRepository.isUserBannedLocally(it) },
            composeFromPeer = composeFromPeer,
            composePeerUserId = peerId,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThreadAccess.RESOLVING)

    private var currentThreadId: String? = null
    /** In-flight getOrCreate for compose-from-peer; send awaits this if the user is faster. */
    private var threadCreateDeferred: CompletableDeferred<String>? = null
    private var listenerJob: Job? = null
    private var threadRowJob: Job? = null
    private var recipientUnreadJob: Job? = null
    private var readReceiptsJob: Job? = null
    private var groupInfoJob: Job? = null

    // Tracks whether the message listener has delivered its first snapshot, so we
    // only re-mark-read for messages that arrive live (the initial load is
    // already covered by markThreadRead in loadMessages).
    private var hasLoadedInitialMessages = false
    private var seenMessageIds: Set<String> = emptySet()
    private var isActivelyViewing = false

    /**
     * Updates whether this conversation is actually visible as the selected
     * NavHost's top destination. A retained thread underneath another page must
     * keep receiving snapshots for a fast return, but must not consume unread
     * state until the user comes back to it.
     */
    fun setActivelyViewing(active: Boolean) {
        val becameActive = active && !isActivelyViewing
        isActivelyViewing = active
        if (becameActive) {
            val threadId = currentThreadId?.takeIf { it.isNotBlank() } ?: return
            val userId = authRepository.currentUserId ?: return
            viewModelScope.launch {
                runCatching { messageRepository.markThreadRead(threadId, userId) }
            }
        }
    }


    fun loadMessages(threadId: String, otherUserId: String) {
        // Popping back onto this screen re-runs LaunchedEffect(threadId). The
        // ViewModel (and its Firestore listener) survived on the back stack, so
        // tearing the listener down and treating that cancel as a load failure
        // flashed "Couldn't connect" on empty 1:1 threads. Stay on the live
        // session instead of reloading.
        val alreadyLive = hasLoadedInitialMessages
            && !_hasLoadError.value
            && listenerJob?.isActive == true
            && threadId.isNotBlank()
            && threadId == currentThreadId
        if (alreadyLive) {
            _resolvedThreadId.value = threadId
            return
        }

        currentThreadId = threadId
        val composeFromPeer = threadId.isBlank() && otherUserId.isNotBlank()
        _openedAsNewCompose.value = composeFromPeer
        _composePeerUserId.value = if (composeFromPeer) otherUserId else ""
        // Fresh create wait for this open; prior deferred must not leak across loads.
        threadCreateDeferred = if (composeFromPeer) CompletableDeferred() else null
        // Arm active-thread tracking right away when the id is already known, so
        // suppression is in effect before the (async) profile fetch completes.
        if (threadId.isNotBlank()) _resolvedThreadId.value = threadId
        // iOS parity: seed the header (name/avatar, or group title/avatars) from the
        // inbox's already-loaded thread so it renders instantly instead of showing a
        // blank header during the profile/group fetch. Reconciled by the async load
        // and live listeners below. No-op when the thread wasn't in the inbox cache
        // (e.g. opened from a profile or notification), which keeps prior behavior.
        seedHeaderFromCachedInbox(threadId)
        // Profile → Message (inbox miss): the peer was just on screen, so their
        // cached profile lets the opener paint before getOrCreate returns.
        if (composeFromPeer) {
            seedHeaderFromUser(userRepository.peekCachedUser(otherUserId))
        }
        // Arm the spinner before the coroutine is scheduled so the first OPEN
        // frame cannot paint an empty list with isLoading still false. Compose
        // from peer skips covering the opener (see MessageThreadScreen).
        _hasLoadError.value = false
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Load other user's profile for the header (1:1 only; group threads
                // have no single otherUserId so skip to avoid an empty-string Firestore error).
                if (otherUserId.isNotBlank()) {
                    runCatching {
                        val profile = userRepository.fetchUserProfile(otherUserId)
                        seedHeaderFromUser(profile)
                    }
                }

                val userId = authRepository.currentUserId ?: throw IllegalStateException("Not signed in")

                // Resolve threadId if empty (e.g. navigating from a user profile).
                // Complete the deferred so a concurrent send can proceed.
                var resolvedId = threadId
                if (resolvedId.isBlank()) {
                    resolvedId = messageRepository.getOrCreateThread(userId, otherUserId)
                    currentThreadId = resolvedId
                    threadCreateDeferred?.complete(resolvedId)
                }
                _resolvedThreadId.value = resolvedId

                // Start real-time Firestore listener (matches iOS snapshot listener)
                startThreadRowListener(userId, resolvedId)
                startListening(resolvedId)
                startThreadDocListener(resolvedId, otherUserId)
                startGroupInfoListener(resolvedId)

                // Mark as read only while this thread is actually visible. Its
                // NavBackStackEntry can remain alive underneath a pushed page.
                if (isActivelyViewing) {
                    messageRepository.markThreadRead(resolvedId, userId)
                }
                startReadReceiptsListener(userId)
            } catch (e: CancellationException) {
                threadCreateDeferred?.cancel(e)
                throw e
            } catch (e: Exception) {
                // Setup failed before the message listener could deliver anything
                // (e.g. not signed in, getOrCreateThread error). Clear the loading
                // state so the thread doesn't spin forever. On the success path the
                // spinner is cleared by startListening's first snapshot instead, so
                // it stays up until messages are actually ready to render.
                _isLoading.value = false
                _messagingRestriction.value = messagingRestrictionFrom(e)
                threadCreateDeferred?.completeExceptionally(e)
                // Getting this far without a conversation IS the refusal on the
                // create path: getOrCreateThread enforces the same rule the row
                // does, so there is nothing to show and nothing left to wait for.
                _threadRow.value = MessageRepository.ThreadRowSnapshot(thread = null, fromCache = false)
            }
        }
    }

    /**
     * Thread id for an outgoing send. Compose-from-peer may still be waiting on
     * getOrCreate — await that (or create) so a fast tap does not send with a
     * blank id. Matches iOS `ensureThreadForSend`.
     */
    private suspend fun ensureThreadForSend(navThreadId: String): String {
        currentThreadId?.takeIf { it.isNotBlank() }?.let { return it }
        navThreadId.takeIf { it.isNotBlank() }?.let { return it }

        val deferred = threadCreateDeferred
        if (deferred != null) {
            return deferred.await()
        }

        val userId = authRepository.currentUserId
            ?: throw IllegalStateException("Not signed in")
        val peerId = _composePeerUserId.value
        if (peerId.isBlank()) throw IllegalStateException("Missing recipient user")
        val created = messageRepository.getOrCreateThread(userId, peerId)
        currentThreadId = created
        _resolvedThreadId.value = created
        return created
    }

    /** Placeholder thread id for optimistic bubbles before getOrCreate returns. */
    private fun provisionalThreadId(navThreadId: String): String =
        currentThreadId?.takeIf { it.isNotBlank() }
            ?: navThreadId.takeIf { it.isNotBlank() }
            ?: "pending"

    /**
     * Inserts [optimistic] immediately, then resolves the real thread id (awaiting
     * in-flight getOrCreate when needed) before calling [send].
     */
    private fun launchOutgoing(
        navThreadId: String,
        clientId: String,
        optimistic: CymbalMessage,
        send: suspend (resolvedThreadId: String) -> Unit,
    ) {
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)
        viewModelScope.launch {
            try {
                val resolvedId = ensureThreadForSend(navThreadId)
                if (resolvedId != optimistic.threadId) {
                    _pendingMessages.value = _pendingMessages.value.toMutableMap().also { map ->
                        map[clientId]?.let { map[clientId] = it.copy(threadId = resolvedId) }
                    }
                }
                send(resolvedId)
                // Ack: mark sent so the clock icon clears immediately (iOS parity); the
                // copy itself is held until the listener has the canonical doc.
                updatePendingStatus(clientId, MessageSendStatus.SENT)
            } catch (e: Exception) {
                updatePendingStatus(clientId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
        }
    }

    /**
     * Arms inbox-miss compose before the first paint so [threadAccess] is already
     * OPEN and the header can seed from cache — no ClosedThread spinner flash.
     * Idempotent; [loadMessages] still owns getOrCreate + listeners.
     */
    fun prepareComposeFromPeer(otherUserId: String) {
        if (otherUserId.isBlank()) return
        _openedAsNewCompose.value = true
        _composePeerUserId.value = otherUserId
        seedHeaderFromUser(userRepository.peekCachedUser(otherUserId))
    }

    private fun seedHeaderFromUser(user: CymbalUser?) {
        val u = user ?: return
        if (_otherUsername.value.isBlank()) _otherUsername.value = u.username
        if (_otherDisplayName.value.isBlank()) _otherDisplayName.value = u.displayName
        if (_otherAvatarURL.value == null) _otherAvatarURL.value = u.avatarURL
        if (_otherAvatarThumbURL.value == null) _otherAvatarThumbURL.value = u.avatarThumbURL
        if (_artistsInCommonCount.value == null) {
            _artistsInCommonCount.value = u.artistsInCommonCount
        }
    }

    /**
     * Seed the header state from the last-rendered inbox snapshot (kept in
     * [MessageRepository.cachedInbox]) so opening a thread from the inbox shows the
     * known name/avatar (1:1) or group title/avatars immediately, mirroring how iOS
     * seeds `MessageThreadView` from the inbox row. Only fills values that are still
     * empty, so it never clobbers anything the live load has already resolved.
     */
    private fun seedHeaderFromCachedInbox(threadId: String) {
        if (threadId.isBlank()) return
        val cached = messageRepository.cachedInbox
            ?.threads?.firstOrNull { it.id == threadId } ?: return
        if (cached.isGroup) {
            if (_groupInfo.value == null) {
                _groupInfo.value = MessageRepository.GroupThreadInfo(
                    isGroup = true,
                    name = cached.groupName,
                    photoURL = cached.groupPhotoURL,
                    memberIds = cached.memberIds,
                    createdBy = cached.createdBy,
                )
            }
            if (cached.members.isNotEmpty() && _membersById.value.isEmpty()) {
                _membersById.value = cached.members.associateBy { it.id }
            }
        } else {
            seedHeaderFromUser(cached.otherUser)
        }
    }

    private fun startThreadRowListener(userId: String, threadId: String) {
        threadRowJob?.cancel()
        threadRowJob = viewModelScope.launch {
            messageRepository.listenToThreadRow(userId, threadId).collect { _threadRow.value = it }
        }
    }

    private fun startListening(threadId: String) {
        listenerJob?.cancel()
        hasLoadedInitialMessages = false
        seenMessageIds = emptySet()
        _olderMessages.value = emptyList()
        _hasMoreMessages.value = true
        _liveWindowReady.value = false
        _isLoadingOlder.value = false
        authRepository.currentUserId?.let { userId ->
            val cached = messageLocalStore.load(userId, threadId)
            if (cached.isNotEmpty()) {
                _olderMessages.value = cached
                _isLoading.value = false
            }
        }
        listenerJob = viewModelScope.launch {
            try {
                messageRepository.listenToMessages(threadId).collect { serverMessages ->
                    val liveOldest = serverMessages.minOfOrNull { it.createdAt }
                    val hasRetainedOlder = liveOldest != null &&
                        _olderMessages.value.any { it.createdAt < liveOldest }
                    _hasMoreMessages.value = hasMoreAfterLiveSnapshot(
                        previous = _hasMoreMessages.value,
                        liveWindowCount = serverMessages.size,
                        hasRetainedOlder = hasRetainedOlder,
                        pageSize = MessageRepository.MESSAGE_PAGE_SIZE,
                    )
                    val confirmedIds = serverMessages.map { it.id }.toSet()
                    // Publish the server snapshot BEFORE pruning the matching pending
                    // copy. The `messages` combine filters pending by the current
                    // server ids, so setting server first means the confirmed message
                    // is already present when pending drops — it never blinks out of
                    // the merged list, which would otherwise re-anchor the reverseLayout
                    // list and hide the newest bubble behind the composer.
                    _serverMessages.value = serverMessages
                    // Remove pending messages that the server has now confirmed
                    _pendingMessages.value = _pendingMessages.value.filterKeys { it !in confirmedIds }
                    authRepository.currentUserId?.let { userId ->
                        messageLocalStore.save(
                            userId,
                            threadId,
                            mergeMessagePages(serverMessages, _olderMessages.value),
                        )
                    }

                    // Re-mark the thread read whenever a NEW message arrives from the
                    // other user while we're viewing it, so the unread badge clears
                    // instead of ticking up (mirrors iOS MessageThreadView). The
                    // initial snapshot is already covered by loadMessages.
                    val myId = authRepository.currentUserId
                    val hadNewIncoming = serverMessages.any {
                        it.id !in seenMessageIds && it.fromUserId != myId
                    }
                    seenMessageIds = confirmedIds

                    if (hasLoadedInitialMessages && hadNewIncoming && myId != null && isActivelyViewing) {
                        messageRepository.markThreadRead(currentThreadId ?: threadId, myId)
                    }
                    hasLoadedInitialMessages = true
                    // First publishable snapshot has landed (cached messages, or a
                    // server snapshot even if empty) — drop the spinner. Empty cache
                    // misses never reach here; see shouldPublishMessagesSnapshot.
                    _hasLoadError.value = false
                    _isLoading.value = false
                    _liveWindowReady.value = true
                }
            } catch (e: CancellationException) {
                // Reloading (or leaving) cancels this collect. An empty thread
                // is a valid snapshot, not a connection failure — swallowing
                // cancel as Exception flashed OfflineRetryState on pop-back.
                throw e
            } catch (_: Exception) {
                // Listener refused or dropped. Leave whatever we already have on
                // screen; if there's nothing, the screen swaps to retry.
                if (_serverMessages.value.isEmpty() && _pendingMessages.value.isEmpty()) {
                    _hasLoadError.value = true
                }
                _isLoading.value = false
                _liveWindowReady.value = true
            }
        }
    }

    /** Fetches one page older than the oldest retained message. Safe to call
     * repeatedly from scroll observation; concurrent and end-of-history calls
     * are ignored. Stable IDs let LazyColumn preserve the visible anchor while
     * the page is appended to its reverse-layout data. */
    fun loadOlderMessages() {
        val threadId = currentThreadId?.takeIf { it.isNotBlank() } ?: return
        if (_isLoadingOlder.value || !_hasMoreMessages.value) return
        val oldest = (_serverMessages.value + _olderMessages.value)
            .minByOrNull { it.createdAt } ?: return
        _isLoadingOlder.value = true
        viewModelScope.launch {
            try {
                val page = messageRepository.listMessages(
                    threadId = threadId,
                    // One look-ahead record distinguishes an exact-size final
                    // page from a page that genuinely has more history.
                    limit = MessageRepository.MESSAGE_PAGE_SIZE.toInt() + 1,
                    lastTimestamp = oldest.createdAt.time,
                )
                val visiblePage = page.takeLast(MessageRepository.MESSAGE_PAGE_SIZE.toInt())
                val knownIds = (_serverMessages.value + _olderMessages.value).map { it.id }.toSet()
                val addedOlder = visiblePage.any { it.id !in knownIds }
                _olderMessages.value = mergeMessagePages(_olderMessages.value, visiblePage)
                _hasMoreMessages.value =
                    addedOlder && page.size.toLong() > MessageRepository.MESSAGE_PAGE_SIZE
                authRepository.currentUserId?.let { userId ->
                    messageLocalStore.save(
                        userId,
                        threadId,
                        mergeMessagePages(_serverMessages.value, _olderMessages.value),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the boundary retryable; reaching the top again can retry.
            } finally {
                _isLoadingOlder.value = false
            }
        }
    }

    private fun startThreadDocListener(threadId: String, otherUserId: String) {
        recipientUnreadJob?.cancel()
        recipientUnreadJob = viewModelScope.launch {
            messageRepository.listenToRecipientUnreadCount(threadId, otherUserId).collect {
                _recipientUnread.value = it
            }
        }
    }

    private fun startReadReceiptsListener(userId: String) {
        readReceiptsJob?.cancel()
        readReceiptsJob = viewModelScope.launch {
            messageRepository.listenToReadReceiptsEnabled(userId).collect {
                _myReadReceiptsEnabled.value = it
            }
        }
    }

    private fun startGroupInfoListener(threadId: String) {
        groupInfoJob?.cancel()
        groupInfoJob = viewModelScope.launch {
            messageRepository.listenToGroupThreadInfo(threadId).collect { info ->
                _groupInfo.value = info
                if (info != null && info.isGroup) {
                    val missing = info.memberIds.filter { it !in _membersById.value }
                    if (missing.isNotEmpty()) {
                        val resolved = _membersById.value.toMutableMap()
                        for (id in missing) {
                            userRepository.fetchUserProfile(id)?.let { resolved[id] = it }
                        }
                        _membersById.value = resolved
                    }
                }
            }
        }
    }

    // Resolve a shared-post message's post by id for the DM card. Mirrors iOS,
    // where the `sharedPost` message stores only the id and the bubble fetches
    // the post to render artwork/title. Cached so re-renders don't re-fetch.
    private val sharedPostCache = mutableMapOf<String, CymbalPost>()

    suspend fun fetchSharedPost(postId: String): CymbalPost? {
        if (postId.isBlank()) return null
        sharedPostCache[postId]?.let { return it }
        val userId = authRepository.currentUserId ?: return null
        val post = runCatching { postRepository.getPostDetail(postId, userId) }.getOrNull()
        if (post != null) sharedPostCache[postId] = post
        return post
    }

    // ── Group actions (driven by the Group Info sheet) ──

    fun renameGroup(name: String) {
        val id = currentThreadId ?: return
        viewModelScope.launch {
            try {
                messageRepository.renameGroup(id, name.trim())
                analyticsService.logGroupRenamed(id)
            } catch (_: Exception) {}
        }
    }

    fun setGroupPhoto(url: String) {
        val id = currentThreadId ?: return
        viewModelScope.launch {
            try {
                messageRepository.setGroupPhoto(id, url)
                analyticsService.logGroupPhotoChanged(id)
            } catch (_: Exception) {}
        }
    }

    fun addGroupMembers(users: List<fm.corus.android.data.model.CymbalUser>) {
        val id = currentThreadId ?: return
        if (users.isEmpty()) return
        val userIds = users.map { it.id }

        // Optimistically reflect the new members so the group-info list updates
        // immediately; the live group-info listener reconciles with the server.
        _membersById.value = _membersById.value.toMutableMap().apply {
            for (u in users) putIfAbsent(u.id, u)
        }
        _groupInfo.value?.let { info ->
            val merged = (info.memberIds + userIds).distinct()
            if (merged.size != info.memberIds.size) {
                _groupInfo.value = info.copy(memberIds = merged)
            }
        }

        viewModelScope.launch {
            try {
                val result = messageRepository.addGroupMembers(id, userIds)
                if (result.added.isNotEmpty()) analyticsService.logGroupMembersAdded(id, result.added.size)
            } catch (_: Exception) {}
        }
    }

    fun removeGroupMember(userId: String) {
        val id = currentThreadId ?: return
        viewModelScope.launch {
            try {
                messageRepository.removeGroupMember(id, userId)
                analyticsService.logGroupMemberRemoved(id)
            } catch (_: Exception) {}
        }
    }

    fun leaveGroup(onDone: () -> Unit) {
        val id = currentThreadId ?: return
        viewModelScope.launch {
            try {
                messageRepository.leaveGroup(id)
                analyticsService.logGroupLeft(id)
            } catch (_: Exception) {}
            onDone()
        }
    }

    suspend fun checkAddable(userIds: List<String>): Map<String, fm.corus.android.data.remote.CloudFunctionsDataSource.GroupAddability> =
        messageRepository.checkGroupAddable(userIds, currentThreadId)

    suspend fun fetchSuggestionsList(): List<fm.corus.android.data.model.CymbalUser> {
        val userId = authRepository.currentUserId ?: return emptyList()
        val contacts = runCatching { messageRepository.listThreads(userId) }
            .getOrDefault(emptyList()).mapNotNull { it.otherUser }
        if (contacts.isNotEmpty()) return contacts.take(20)
        runCatching { userRepository.prefetchFollowingSet(userId) }
        return userRepository.followingIds.value.take(20)
            .mapNotNull { runCatching { userRepository.fetchUserProfile(it) }.getOrNull() }
            .filter { !it.isBot }
    }

    suspend fun searchUsersList(query: String): List<fm.corus.android.data.model.CymbalUser> =
        runCatching { userRepository.searchUsers(query, limit = 15, includeFollowed = true) }
            .getOrDefault(emptyList())

    /** Upload an image and set it as the group photo. Returns true on success. */
    private val _isUploadingGroupPhoto = MutableStateFlow(false)
    val isUploadingGroupPhoto: StateFlow<Boolean> = _isUploadingGroupPhoto.asStateFlow()

    fun uploadAndSetGroupPhoto(imageData: ByteArray) {
        val id = currentThreadId ?: return
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isUploadingGroupPhoto.value = true
            try {
                val url = messageRepository.uploadGroupPhoto(userId, id, imageData)
                messageRepository.setGroupPhoto(id, url)
                analyticsService.logGroupPhotoChanged(id)
            } catch (_: Exception) {} finally {
                _isUploadingGroupPhoto.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
        threadRowJob?.cancel()
        recipientUnreadJob?.cancel()
        readReceiptsJob?.cancel()
        groupInfoJob?.cancel()
    }

    fun setReplyTo(message: CymbalMessage?) {
        if (message != null) _editingMessage.value = null
        _replyToMessage.value = message
    }

    /** Begin editing one of the caller's own text messages. Mutually exclusive with reply. */
    fun startEditing(message: CymbalMessage) {
        _replyToMessage.value = null
        _editingMessage.value = message
        clearMentions()
        clearHashtags()
    }

    fun cancelEditing() {
        _editingMessage.value = null
    }

    /**
     * Commit an in-progress edit. Optimistically updates the message in place; the
     * Firestore listener reconciles to the canonical server value (text + editedAt)
     * shortly after. On failure the optimistic change is reverted and the editor
     * re-opens so the user can retry.
     */
    fun editMessage(threadId: String, newText: String) {
        val target = _editingMessage.value ?: return
        val resolvedId = currentThreadId ?: threadId
        val trimmed = newText.trim()
        _editingMessage.value = null
        if (trimmed.isEmpty()) return
        if (trimmed == (target.text ?: "")) return // unchanged: no round-trip

        val original = _serverMessages.value.firstOrNull { it.id == target.id }
        _serverMessages.value = _serverMessages.value.map {
            if (it.id == target.id) it.copy(text = trimmed, editedAt = Date()) else it
        }

        viewModelScope.launch {
            try {
                messageRepository.editMessage(resolvedId, target.id, trimmed)
            } catch (e: Exception) {
                if (original != null) {
                    _serverMessages.value = _serverMessages.value.map {
                        if (it.id == target.id) original else it
                    }
                }
                _editingMessage.value = target
            }
        }
    }

    // ── Optimistic send: text ──

    fun sendMessage(threadId: String, text: String) {
        val userId = authRepository.currentUserId ?: return
        val provisionalId = provisionalThreadId(threadId)
        val reply = _replyToMessage.value
        val replySnippet = reply?.let { replyPreviewText(it, context) }
        val clientId = UUID.randomUUID().toString()

        // Optimistic insert
        val optimistic = CymbalMessage(
            id = clientId,
            threadId = provisionalId,
            fromUserId = userId,
            text = text,
            type = MessageType.TEXT,
            createdAt = Date(),
            sendStatus = MessageSendStatus.SENDING,
            replyToMessageId = reply?.id,
            replyToText = replySnippet,
            replyToUserId = reply?.fromUserId,
        )
        _replyToMessage.value = null
        launchOutgoing(navThreadId = threadId, clientId = clientId, optimistic = optimistic) { resolvedId ->
            messageRepository.sendTextMessage(
                threadId = resolvedId,
                fromUserId = userId,
                text = text,
                replyToMessageId = reply?.id,
                replyToText = replySnippet,
                replyToUserId = reply?.fromUserId,
                clientMessageId = clientId,
            )
        }
    }

    // ── Optimistic send: image ──

    fun sendImageMessage(threadId: String, imageData: ByteArray) {
        val userId = authRepository.currentUserId ?: return
        val provisionalId = provisionalThreadId(threadId)
        val clientId = UUID.randomUUID().toString()

        val optimistic = CymbalMessage(
            id = clientId,
            threadId = provisionalId,
            fromUserId = userId,
            text = null,
            type = MessageType.IMAGE,
            createdAt = Date(),
            sendStatus = MessageSendStatus.SENDING,
        )
        launchOutgoing(navThreadId = threadId, clientId = clientId, optimistic = optimistic) { resolvedId ->
            messageRepository.sendImageMessage(
                threadId = resolvedId,
                fromUserId = userId,
                imageData = imageData,
                clientMessageId = clientId,
            )
        }
    }

    // ── Optimistic send: GIF ──

    fun sendGifMessage(threadId: String, gifURL: String, slug: String = "") {
        val userId = authRepository.currentUserId ?: return
        val provisionalId = provisionalThreadId(threadId)
        val clientId = UUID.randomUUID().toString()

        val optimistic = CymbalMessage(
            id = clientId,
            threadId = provisionalId,
            fromUserId = userId,
            text = null,
            type = MessageType.GIF,
            mediaURL = gifURL,
            createdAt = Date(),
            sendStatus = MessageSendStatus.SENDING,
        )
        launchOutgoing(navThreadId = threadId, clientId = clientId, optimistic = optimistic) { resolvedId ->
            if (slug.isNotEmpty()) {
                gifRepository.triggerShare(slug)
            }
            messageRepository.sendGifMessage(
                threadId = resolvedId,
                fromUserId = userId,
                gifURL = gifURL,
                clientMessageId = clientId,
            )
        }
    }

    // ── Optimistic send: song ──

    fun sendSongMessage(threadId: String, track: CymbalTrack) {
        val userId = authRepository.currentUserId ?: return
        val provisionalId = provisionalThreadId(threadId)
        val clientId = UUID.randomUUID().toString()

        val isSoundCloud = track.source == TrackSource.SOUNDCLOUD
        val optimistic = CymbalMessage(
            id = clientId,
            threadId = provisionalId,
            fromUserId = userId,
            text = null,
            type = MessageType.SHARED_TRACK,
            createdAt = Date(),
            sendStatus = MessageSendStatus.SENDING,
            trackId = track.id,
            trackName = track.name,
            artistName = track.artistName,
            albumName = track.albumName,
            albumArtURL = track.albumArtURL,
            albumArtLargeURL = track.albumArtLargeURL,
            spotifyURI = track.spotifyURI.ifBlank { null },
            spotifyURL = track.spotifyWebURL.ifBlank { null },
            previewUrl = track.previewUrl,
            isrc = track.isrc,
            durationMs = track.durationMs,
            trackSource = if (isSoundCloud) "soundcloud" else "spotify",
            soundcloudId = track.soundcloudId,
            soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
        )
        launchOutgoing(navThreadId = threadId, clientId = clientId, optimistic = optimistic) { resolvedId ->
            messageRepository.sendSharedTrackMessage(
                threadId = resolvedId,
                fromUserId = userId,
                track = track,
                clientMessageId = clientId,
            )
        }
    }

    // ── Optimistic send: film ──

    fun sendFilmMessage(threadId: String, movie: CymbalMovie) {
        val userId = authRepository.currentUserId ?: return
        val provisionalId = provisionalThreadId(threadId)
        val clientId = UUID.randomUUID().toString()

        val optimistic = CymbalMessage(
            id = clientId,
            threadId = provisionalId,
            fromUserId = userId,
            text = null,
            type = MessageType.SHARED_FILM,
            createdAt = Date(),
            sendStatus = MessageSendStatus.SENDING,
            movieId = movie.id,
            movieTitle = movie.title,
            directorName = movie.directorName,
            releaseYear = movie.year,
            posterURL = movie.posterURL,
            posterLargeURL = movie.posterLargeURL,
            tmdbWebURL = movie.tmdbWebURL.ifBlank { null },
        )
        launchOutgoing(navThreadId = threadId, clientId = clientId, optimistic = optimistic) { resolvedId ->
            messageRepository.sendSharedFilmMessage(
                threadId = resolvedId,
                fromUserId = userId,
                movie = movie,
                clientMessageId = clientId,
            )
        }
    }

    fun sendArtistMessage(threadId: String, artistId: String, name: String, imageUrl: String?) {
        val userId = authRepository.currentUserId ?: return
        val provisionalId = provisionalThreadId(threadId)
        val clientId = UUID.randomUUID().toString()
        val optimistic = CymbalMessage(
            id = clientId, threadId = provisionalId, fromUserId = userId, text = null,
            type = MessageType.SHARED_ARTIST, createdAt = Date(), sendStatus = MessageSendStatus.SENDING,
            artistId = artistId, artistName = name, artistImageURL = imageUrl,
        )
        launchOutgoing(navThreadId = threadId, clientId = clientId, optimistic = optimistic) { resolvedId ->
            messageRepository.sendSharedArtistMessage(
                threadId = resolvedId, fromUserId = userId, artistId = artistId,
                name = name, imageUrl = imageUrl, clientMessageId = clientId,
            )
        }
    }

    fun sendAlbumMessage(threadId: String, albumId: String, title: String, artistName: String?, coverUrl: String?, year: Int?) {
        val userId = authRepository.currentUserId ?: return
        val provisionalId = provisionalThreadId(threadId)
        val clientId = UUID.randomUUID().toString()
        val optimistic = CymbalMessage(
            id = clientId, threadId = provisionalId, fromUserId = userId, text = null,
            type = MessageType.SHARED_ALBUM, createdAt = Date(), sendStatus = MessageSendStatus.SENDING,
            albumId = albumId, albumTitle = title, albumArtistName = artistName,
            albumCoverURL = coverUrl, albumYear = year?.toString(),
        )
        launchOutgoing(navThreadId = threadId, clientId = clientId, optimistic = optimistic) { resolvedId ->
            messageRepository.sendSharedAlbumMessage(
                threadId = resolvedId, fromUserId = userId, albumId = albumId,
                title = title, artistName = artistName ?: "", coverUrl = coverUrl,
                year = year?.toString(), clientMessageId = clientId,
            )
        }
    }

    fun sendDirectorMessage(threadId: String, directorId: String, name: String, imageUrl: String?) {
        val userId = authRepository.currentUserId ?: return
        val provisionalId = provisionalThreadId(threadId)
        val clientId = UUID.randomUUID().toString()
        val optimistic = CymbalMessage(
            id = clientId, threadId = provisionalId, fromUserId = userId, text = null,
            type = MessageType.SHARED_DIRECTOR, createdAt = Date(), sendStatus = MessageSendStatus.SENDING,
            directorId = directorId, directorName = name, directorImageURL = imageUrl,
        )
        launchOutgoing(navThreadId = threadId, clientId = clientId, optimistic = optimistic) { resolvedId ->
            messageRepository.sendSharedDirectorMessage(
                threadId = resolvedId, fromUserId = userId, directorId = directorId,
                name = name, imageUrl = imageUrl, clientMessageId = clientId,
            )
        }
    }

    // ── Retry ──

    fun retrySendMessage(messageId: String) {
        val message = _pendingMessages.value[messageId] ?: return
        if (message.failureReason == MessageFailureReason.MESSAGING_DISABLED) return

        updatePendingStatus(messageId, MessageSendStatus.SENDING)

        viewModelScope.launch {
            try {
                when (message.type) {
                    MessageType.TEXT -> messageRepository.sendTextMessage(
                        threadId = message.threadId,
                        fromUserId = message.fromUserId,
                        text = message.text ?: "",
                        replyToMessageId = message.replyToMessageId,
                        replyToText = message.replyToText,
                        replyToUserId = message.replyToUserId,
                        clientMessageId = messageId,
                    )
                    MessageType.GIF -> messageRepository.sendGifMessage(
                        threadId = message.threadId,
                        fromUserId = message.fromUserId,
                        gifURL = message.mediaURL ?: "",
                        clientMessageId = messageId,
                    )
                    MessageType.SHARED_TRACK -> {
                        val song = message.attachedSong
                        val source = message.attachedSongSource ?: TrackSource.SPOTIFY
                        if (song != null) {
                            val track = song.asCymbalTrack().copy(
                                source = source,
                                soundcloudId = message.soundcloudId,
                                soundcloudPermalinkUrl = message.soundcloudPermalinkUrl,
                            )
                            messageRepository.sendSharedTrackMessage(
                                threadId = message.threadId,
                                fromUserId = message.fromUserId,
                                track = track,
                                clientMessageId = messageId,
                            )
                        }
                    }
                    MessageType.SHARED_FILM -> {
                        val film = message.attachedFilm
                        if (film != null) {
                            messageRepository.sendSharedFilmMessage(
                                threadId = message.threadId,
                                fromUserId = message.fromUserId,
                                movie = film.asCymbalMovie(),
                                clientMessageId = messageId,
                            )
                        }
                    }
                    MessageType.SHARED_POST -> {
                        val sharedPostId = message.sharedPostId
                        if (!sharedPostId.isNullOrBlank()) {
                            messageRepository.sendSharedPostMessage(
                                threadId = message.threadId,
                                fromUserId = message.fromUserId,
                                postId = sharedPostId,
                                text = message.text ?: "",
                                clientMessageId = messageId,
                            )
                        }
                    }
                    // Image retry is not supported — the original imageData is not retained
                    else -> {}
                }
                // Ack: mark sent so the retry affordance clears immediately; the copy
                // itself is held until the listener has the canonical doc.
                updatePendingStatus(messageId, MessageSendStatus.SENT)
            } catch (e: Exception) {
                updatePendingStatus(messageId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
        }
    }

    // ── Reactions ──

    /**
     * Toggle the caller's [emoji] reaction on a message. Optimistically updates the
     * reaction in place (mirrors iOS `toggleReaction`) so the pill appears instantly;
     * the Firestore listener reconciles to the canonical server value shortly after.
     * On failure the optimistic change is reverted.
     *
     * [emoji] must be the raw emoji character the server allowlists (e.g. "❤️"),
     * not a key string like "heart".
     */
    fun toggleReaction(threadId: String, messageId: String, emoji: String) {
        val uid = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val target = _serverMessages.value.firstOrNull { it.id == messageId } ?: return
        val originalReactions = target.reactions

        // Toggle the caller into/out of this emoji's reactor list, dropping the
        // emoji entirely when it has no reactors left.
        val users = originalReactions[emoji] ?: emptyList()
        val newReactions = originalReactions.toMutableMap().apply {
            if (uid in users) {
                val remaining = users - uid
                if (remaining.isEmpty()) remove(emoji) else put(emoji, remaining)
            } else {
                put(emoji, users + uid)
            }
        }
        _serverMessages.value = _serverMessages.value.map {
            if (it.id == messageId) it.copy(reactions = newReactions) else it
        }

        viewModelScope.launch {
            try {
                messageRepository.toggleReaction(resolvedId, messageId, emoji)
            } catch (_: Exception) {
                // Roll back to the pre-tap reactions for this message.
                _serverMessages.value = _serverMessages.value.map {
                    if (it.id == messageId) it.copy(reactions = originalReactions) else it
                }
            }
        }
    }

    // ── Mention / hashtag search (mirrors CommentsViewModel) ──

    private val _mentionSuggestions = MutableStateFlow<List<CymbalUser>>(emptyList())
    val mentionSuggestions: StateFlow<List<CymbalUser>> = _mentionSuggestions.asStateFlow()

    private val _isSearchingMentions = MutableStateFlow(false)
    val isSearchingMentions: StateFlow<Boolean> = _isSearchingMentions.asStateFlow()

    private var mentionSearchJob: Job? = null

    private val _hashtagSuggestions = MutableStateFlow<List<HashtagSuggestion>>(emptyList())
    val hashtagSuggestions: StateFlow<List<HashtagSuggestion>> = _hashtagSuggestions.asStateFlow()

    private var hashtagSearchJob: Job? = null

    fun searchMentions(query: String) {
        mentionSearchJob?.cancel()
        if (query.length < 2) {
            _mentionSuggestions.value = emptyList()
            _isSearchingMentions.value = false
            return
        }
        _isSearchingMentions.value = true
        mentionSearchJob = viewModelScope.launch {
            try {
                val results = userRepository.searchUsers(query, limit = 4)
                _mentionSuggestions.value = results
            } catch (_: Exception) {
                _mentionSuggestions.value = emptyList()
            } finally {
                _isSearchingMentions.value = false
            }
        }
    }

    fun clearMentions() {
        mentionSearchJob?.cancel()
        mentionSearchJob = null
        _mentionSuggestions.value = emptyList()
        _isSearchingMentions.value = false
    }

    fun searchHashtags(query: String) {
        hashtagSearchJob?.cancel()
        hashtagSearchJob = viewModelScope.launch {
            try {
                _hashtagSuggestions.value = exploreRepository.fetchHashtagSuggestions(query, limit = 3)
            } catch (_: Exception) {
                _hashtagSuggestions.value = emptyList()
            }
        }
    }

    fun clearHashtags() {
        hashtagSearchJob?.cancel()
        hashtagSearchJob = null
        _hashtagSuggestions.value = emptyList()
    }

    suspend fun resolveUsernameToId(username: String): String? {
        return try {
            userRepository.fetchUserByUsername(username)?.id
        } catch (_: Exception) {
            null
        }
    }

    fun logHashtagTapped(tag: String) {
        analyticsService.logTrendingHashtagTapped(tag)
    }

    // ── Helpers ──

    private fun updatePendingStatus(
        messageId: String,
        status: MessageSendStatus,
        reason: MessageFailureReason = MessageFailureReason.GENERIC,
    ) {
        val current = _pendingMessages.value[messageId] ?: return
        _pendingMessages.value = _pendingMessages.value + (messageId to current.copy(
            sendStatus = status,
            failureReason = reason,
        ))
    }

    private fun failureReasonFrom(error: Exception): MessageFailureReason {
        messagingRestrictionFrom(error)?.let { _messagingRestriction.value = it }
        return if (messagingRestrictionFrom(error) != null) {
            MessageFailureReason.MESSAGING_DISABLED
        } else {
            MessageFailureReason.GENERIC
        }
    }
}
