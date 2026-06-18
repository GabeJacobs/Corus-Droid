package fm.corus.android.ui.screens.messaging

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalMovie
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
    private val remoteConfigService: RemoteConfigService,
    private val gifRepository: fm.corus.android.data.repository.GifRepository,
    val nowPlayingManager: fm.corus.android.domain.NowPlayingManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val gifSupport: Boolean
        get() = remoteConfigService.gifSupport

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

    private var currentThreadId: String? = null
    private var listenerJob: Job? = null
    private var recipientUnreadJob: Job? = null
    private var readReceiptsJob: Job? = null


    fun loadMessages(threadId: String, otherUserId: String) {
        currentThreadId = threadId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load other user's profile for the header
                val profile = userRepository.fetchUserProfile(otherUserId)
                _otherUsername.value = profile?.username ?: ""
                _otherDisplayName.value = profile?.displayName ?: ""
                _otherAvatarURL.value = profile?.avatarURL
                _otherAvatarThumbURL.value = profile?.avatarThumbURL

                // Resolve threadId if empty (e.g. navigating from a user profile)
                var resolvedId = threadId
                if (resolvedId.isBlank()) {
                    val userId = authRepository.currentUserId ?: throw IllegalStateException("Not signed in")
                    resolvedId = messageRepository.getOrCreateThread(userId, otherUserId)
                    currentThreadId = resolvedId
                }

                // Start real-time Firestore listener (matches iOS snapshot listener)
                startListening(resolvedId)
                startThreadDocListener(resolvedId, otherUserId)

                // Mark as read
                val userId = authRepository.currentUserId
                if (userId != null) {
                    messageRepository.markThreadRead(resolvedId, userId)
                    startReadReceiptsListener(userId)
                }
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    private fun startListening(threadId: String) {
        listenerJob?.cancel()
        listenerJob = viewModelScope.launch {
            messageRepository.listenToMessages(threadId).collect { serverMessages ->
                // Remove pending messages that the server has now confirmed
                val confirmedIds = serverMessages.map { it.id }.toSet()
                _pendingMessages.value = _pendingMessages.value.filterKeys { it !in confirmedIds }
                _serverMessages.value = serverMessages
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

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
        recipientUnreadJob?.cancel()
        readReceiptsJob?.cancel()
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

    fun toggleReaction(threadId: String, messageId: String, emoji: String) {
        val resolvedId = currentThreadId ?: threadId
        viewModelScope.launch {
            try {
                messageRepository.toggleReaction(resolvedId, messageId, emoji)
            } catch (_: Exception) { }
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
