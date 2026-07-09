package fm.corus.android.ui.screens.messaging

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MessageFailureReason
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
import fm.corus.android.data.model.TrackSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.RemoteConfigService
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

@HiltViewModel
class MessageThreadViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val postRepository: fm.corus.android.data.repository.PostRepository,
    private val remoteConfigService: RemoteConfigService,
    private val gifRepository: fm.corus.android.data.repository.GifRepository,
    val nowPlayingManager: fm.corus.android.domain.NowPlayingManager,
    private val analyticsService: fm.corus.android.service.AnalyticsService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

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

    private val _pendingMessages = MutableStateFlow<Map<String, CymbalMessage>>(emptyMap())

    /** Merged server + unconfirmed pending messages, reversed for reverseLayout LazyColumn. */
    val messages: StateFlow<List<CymbalMessage>> = combine(
        _serverMessages,
        _pendingMessages,
    ) { server, pending ->
        val confirmedIds = server.map { it.id }.toSet()
        val unconfirmed = pending.values.filter { it.id !in confirmedIds }
        (server + unconfirmed).sortedByDescending { it.createdAt }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _otherUsername = MutableStateFlow("")
    val otherUsername: StateFlow<String> = _otherUsername.asStateFlow()

    private val _otherDisplayName = MutableStateFlow("")
    val otherDisplayName: StateFlow<String> = _otherDisplayName.asStateFlow()

    private val _otherAvatarURL = MutableStateFlow<String?>(null)
    val otherAvatarURL: StateFlow<String?> = _otherAvatarURL.asStateFlow()

    private val _otherAvatarThumbURL = MutableStateFlow<String?>(null)
    val otherAvatarThumbURL: StateFlow<String?> = _otherAvatarThumbURL.asStateFlow()

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

    private var currentThreadId: String? = null
    private var listenerJob: Job? = null
    private var recipientUnreadJob: Job? = null
    private var readReceiptsJob: Job? = null
    private var groupInfoJob: Job? = null

    // Tracks whether the message listener has delivered its first snapshot, so we
    // only re-mark-read for messages that arrive live (the initial load is
    // already covered by markThreadRead in loadMessages).
    private var hasLoadedInitialMessages = false
    private var seenMessageIds: Set<String> = emptySet()


    fun loadMessages(threadId: String, otherUserId: String) {
        currentThreadId = threadId
        // Arm active-thread tracking right away when the id is already known, so
        // suppression is in effect before the (async) profile fetch completes.
        if (threadId.isNotBlank()) _resolvedThreadId.value = threadId
        // iOS parity: seed the header (name/avatar, or group title/avatars) from the
        // inbox's already-loaded thread so it renders instantly instead of showing a
        // blank header during the profile/group fetch. Reconciled by the async load
        // and live listeners below. No-op when the thread wasn't in the inbox cache
        // (e.g. opened from a profile or notification), which keeps prior behavior.
        seedHeaderFromCachedInbox(threadId)
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load other user's profile for the header (1:1 only; group threads
                // have no single otherUserId so skip to avoid an empty-string Firestore error).
                if (otherUserId.isNotBlank()) {
                    runCatching {
                        val profile = userRepository.fetchUserProfile(otherUserId)
                        _otherUsername.value = profile?.username ?: ""
                        _otherDisplayName.value = profile?.displayName ?: ""
                        _otherAvatarURL.value = profile?.avatarURL
                        _otherAvatarThumbURL.value = profile?.avatarThumbURL
                    }
                }

                // Resolve threadId if empty (e.g. navigating from a user profile)
                var resolvedId = threadId
                if (resolvedId.isBlank()) {
                    val userId = authRepository.currentUserId ?: throw IllegalStateException("Not signed in")
                    resolvedId = messageRepository.getOrCreateThread(userId, otherUserId)
                    currentThreadId = resolvedId
                }
                _resolvedThreadId.value = resolvedId

                // Start real-time Firestore listener (matches iOS snapshot listener)
                startListening(resolvedId)
                startThreadDocListener(resolvedId, otherUserId)
                startGroupInfoListener(resolvedId)

                // Mark as read
                val userId = authRepository.currentUserId
                if (userId != null) {
                    messageRepository.markThreadRead(resolvedId, userId)
                    startReadReceiptsListener(userId)
                }
            } catch (_: Exception) {
                // Setup failed before the message listener could deliver anything
                // (e.g. not signed in, getOrCreateThread error). Clear the loading
                // state so the thread doesn't spin forever. On the success path the
                // spinner is cleared by startListening's first snapshot instead, so
                // it stays up until messages are actually ready to render.
                _isLoading.value = false
            }
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
            cached.otherUser?.let { u ->
                if (_otherUsername.value.isBlank()) _otherUsername.value = u.username
                if (_otherDisplayName.value.isBlank()) _otherDisplayName.value = u.displayName
                if (_otherAvatarURL.value == null) _otherAvatarURL.value = u.avatarURL
                if (_otherAvatarThumbURL.value == null) _otherAvatarThumbURL.value = u.avatarThumbURL
            }
        }
    }

    private fun startListening(threadId: String) {
        listenerJob?.cancel()
        hasLoadedInitialMessages = false
        seenMessageIds = emptySet()
        listenerJob = viewModelScope.launch {
            messageRepository.listenToMessages(threadId).collect { serverMessages ->
                // Remove pending messages that the server has now confirmed
                val confirmedIds = serverMessages.map { it.id }.toSet()
                _pendingMessages.value = _pendingMessages.value.filterKeys { it !in confirmedIds }

                // Re-mark the thread read whenever a NEW message arrives from the
                // other user while we're viewing it, so the unread badge clears
                // instead of ticking up (mirrors iOS MessageThreadView). The
                // initial snapshot is already covered by loadMessages.
                val myId = authRepository.currentUserId
                val hadNewIncoming = serverMessages.any {
                    it.id !in seenMessageIds && it.fromUserId != myId
                }
                seenMessageIds = confirmedIds
                _serverMessages.value = serverMessages

                if (hasLoadedInitialMessages && hadNewIncoming && myId != null) {
                    messageRepository.markThreadRead(currentThreadId ?: threadId, myId)
                }
                hasLoadedInitialMessages = true
                // First snapshot has landed (even if empty) — messages are ready to
                // render, so drop the initial-load spinner. No-op on later snapshots.
                _isLoading.value = false
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
        val resolvedId = currentThreadId ?: threadId
        val reply = _replyToMessage.value
        val replySnippet = reply?.let { replyPreviewText(it, context) }
        val clientId = UUID.randomUUID().toString()

        // Optimistic insert
        val optimistic = CymbalMessage(
            id = clientId,
            threadId = resolvedId,
            fromUserId = userId,
            text = text,
            type = MessageType.TEXT,
            createdAt = Date(),
            sendStatus = MessageSendStatus.SENDING,
            replyToMessageId = reply?.id,
            replyToText = replySnippet,
            replyToUserId = reply?.fromUserId,
        )
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)
        _replyToMessage.value = null

        viewModelScope.launch {
            try {
                messageRepository.sendTextMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    text = text,
                    replyToMessageId = reply?.id,
                    replyToText = replySnippet,
                    replyToUserId = reply?.fromUserId,
                    clientMessageId = clientId,
                )
                // Server confirmed — Firestore listener will add the real message and
                // startListening will prune the pending copy.
                _pendingMessages.value = _pendingMessages.value - clientId
            } catch (e: Exception) {
                updatePendingStatus(clientId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
        }
    }

    // ── Optimistic send: image ──

    fun sendImageMessage(threadId: String, imageData: ByteArray) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val clientId = UUID.randomUUID().toString()

        val optimistic = CymbalMessage(
            id = clientId,
            threadId = resolvedId,
            fromUserId = userId,
            text = null,
            type = MessageType.IMAGE,
            createdAt = Date(),
            sendStatus = MessageSendStatus.SENDING,
        )
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)

        viewModelScope.launch {
            try {
                messageRepository.sendImageMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    imageData = imageData,
                    clientMessageId = clientId,
                )
                _pendingMessages.value = _pendingMessages.value - clientId
            } catch (e: Exception) {
                updatePendingStatus(clientId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
        }
    }

    // ── Optimistic send: GIF ──

    fun sendGifMessage(threadId: String, gifURL: String, slug: String = "") {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val clientId = UUID.randomUUID().toString()

        val optimistic = CymbalMessage(
            id = clientId,
            threadId = resolvedId,
            fromUserId = userId,
            text = null,
            type = MessageType.GIF,
            mediaURL = gifURL,
            createdAt = Date(),
            sendStatus = MessageSendStatus.SENDING,
        )
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)

        viewModelScope.launch {
            try {
                if (slug.isNotEmpty()) {
                    gifRepository.triggerShare(slug)
                }
                messageRepository.sendGifMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    gifURL = gifURL,
                    clientMessageId = clientId,
                )
                _pendingMessages.value = _pendingMessages.value - clientId
            } catch (e: Exception) {
                updatePendingStatus(clientId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
        }
    }

    // ── Optimistic send: song ──

    fun sendSongMessage(threadId: String, track: CymbalTrack) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val clientId = UUID.randomUUID().toString()

        val isSoundCloud = track.source == TrackSource.SOUNDCLOUD
        val optimistic = CymbalMessage(
            id = clientId,
            threadId = resolvedId,
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
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)

        viewModelScope.launch {
            try {
                messageRepository.sendSharedTrackMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    track = track,
                    clientMessageId = clientId,
                )
                _pendingMessages.value = _pendingMessages.value - clientId
            } catch (e: Exception) {
                updatePendingStatus(clientId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
        }
    }

    // ── Optimistic send: film ──

    fun sendFilmMessage(threadId: String, movie: CymbalMovie) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val clientId = UUID.randomUUID().toString()

        val optimistic = CymbalMessage(
            id = clientId,
            threadId = resolvedId,
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
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)

        viewModelScope.launch {
            try {
                messageRepository.sendSharedFilmMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    movie = movie,
                    clientMessageId = clientId,
                )
                _pendingMessages.value = _pendingMessages.value - clientId
            } catch (e: Exception) {
                updatePendingStatus(clientId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
        }
    }

    fun sendArtistMessage(threadId: String, artistId: String, name: String, imageUrl: String?) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val clientId = UUID.randomUUID().toString()
        val optimistic = CymbalMessage(
            id = clientId, threadId = resolvedId, fromUserId = userId, text = null,
            type = MessageType.SHARED_ARTIST, createdAt = Date(), sendStatus = MessageSendStatus.SENDING,
            artistId = artistId, artistName = name, artistImageURL = imageUrl,
        )
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)
        viewModelScope.launch {
            try {
                messageRepository.sendSharedArtistMessage(
                    threadId = resolvedId, fromUserId = userId, artistId = artistId,
                    name = name, imageUrl = imageUrl, clientMessageId = clientId,
                )
                _pendingMessages.value = _pendingMessages.value - clientId
            } catch (e: Exception) {
                updatePendingStatus(clientId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
        }
    }

    fun sendAlbumMessage(threadId: String, albumId: String, title: String, artistName: String?, coverUrl: String?, year: Int?) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val clientId = UUID.randomUUID().toString()
        val optimistic = CymbalMessage(
            id = clientId, threadId = resolvedId, fromUserId = userId, text = null,
            type = MessageType.SHARED_ALBUM, createdAt = Date(), sendStatus = MessageSendStatus.SENDING,
            albumId = albumId, albumTitle = title, albumArtistName = artistName,
            albumCoverURL = coverUrl, albumYear = year?.toString(),
        )
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)
        viewModelScope.launch {
            try {
                messageRepository.sendSharedAlbumMessage(
                    threadId = resolvedId, fromUserId = userId, albumId = albumId,
                    title = title, artistName = artistName ?: "", coverUrl = coverUrl,
                    year = year?.toString(), clientMessageId = clientId,
                )
                _pendingMessages.value = _pendingMessages.value - clientId
            } catch (e: Exception) {
                updatePendingStatus(clientId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
        }
    }

    fun sendDirectorMessage(threadId: String, directorId: String, name: String, imageUrl: String?) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val clientId = UUID.randomUUID().toString()
        val optimistic = CymbalMessage(
            id = clientId, threadId = resolvedId, fromUserId = userId, text = null,
            type = MessageType.SHARED_DIRECTOR, createdAt = Date(), sendStatus = MessageSendStatus.SENDING,
            directorId = directorId, directorName = name, directorImageURL = imageUrl,
        )
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)
        viewModelScope.launch {
            try {
                messageRepository.sendSharedDirectorMessage(
                    threadId = resolvedId, fromUserId = userId, directorId = directorId,
                    name = name, imageUrl = imageUrl, clientMessageId = clientId,
                )
                _pendingMessages.value = _pendingMessages.value - clientId
            } catch (e: Exception) {
                updatePendingStatus(clientId, MessageSendStatus.FAILED, failureReasonFrom(e))
            }
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
                _pendingMessages.value = _pendingMessages.value - messageId
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
        return if (error.message?.contains("turned off messaging") == true) {
            MessageFailureReason.MESSAGING_DISABLED
        } else {
            MessageFailureReason.GENERIC
        }
    }
}
