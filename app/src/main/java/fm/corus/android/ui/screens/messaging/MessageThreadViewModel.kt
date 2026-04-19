package fm.corus.android.ui.screens.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MessageFailureReason
import fm.corus.android.data.model.MessageSendStatus
import fm.corus.android.data.model.MessageType
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
) : ViewModel() {

    val giphySupport: Boolean
        get() = remoteConfigService.giphySupport

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

    val currentUserId: String? get() = authRepository.currentUserId

    private val _replyToMessage = MutableStateFlow<CymbalMessage?>(null)
    val replyToMessage: StateFlow<CymbalMessage?> = _replyToMessage.asStateFlow()

    private var currentThreadId: String? = null
    private var listenerJob: Job? = null

    fun loadMessages(threadId: String, otherUserId: String) {
        currentThreadId = threadId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load other user's profile for the header
                val profile = userRepository.fetchUserProfile(otherUserId)
                _otherUsername.value = profile?.username ?: ""

                // Resolve threadId if empty (e.g. navigating from a user profile)
                var resolvedId = threadId
                if (resolvedId.isBlank()) {
                    val userId = authRepository.currentUserId ?: throw IllegalStateException("Not signed in")
                    resolvedId = messageRepository.getOrCreateThread(userId, otherUserId)
                    currentThreadId = resolvedId
                }

                // Start real-time Firestore listener (matches iOS snapshot listener)
                startListening(resolvedId)

                // Mark as read
                val userId = authRepository.currentUserId
                if (userId != null) {
                    messageRepository.markThreadRead(resolvedId, userId)
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

    override fun onCleared() {
        super.onCleared()
        listenerJob?.cancel()
    }

    fun setReplyTo(message: CymbalMessage?) {
        _replyToMessage.value = message
    }

    // ── Optimistic send: text ──

    fun sendMessage(threadId: String, text: String) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val reply = _replyToMessage.value
        val replySnippet = reply?.let { replyPreviewText(it) }
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

    fun sendGifMessage(threadId: String, gifURL: String) {
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

        val optimistic = CymbalMessage(
            id = clientId,
            threadId = resolvedId,
            fromUserId = userId,
            text = null,
            type = MessageType.SHARED_TRACK,
            createdAt = Date(),
            sendStatus = MessageSendStatus.SENDING,
            trackName = track.name,
            artistName = track.artistName,
            albumArtURL = track.albumArtURL,
            spotifyURL = track.spotifyWebURL.ifBlank { null },
        )
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)

        viewModelScope.launch {
            try {
                messageRepository.sendSharedTrackMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    trackName = track.name,
                    artistName = track.artistName,
                    albumArtURL = track.albumArtURL,
                    spotifyURL = track.spotifyWebURL.ifBlank { null },
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
            movieTitle = movie.title,
            directorName = movie.directorName,
            posterURL = movie.posterURL,
            tmdbWebURL = movie.tmdbWebURL.ifBlank { null },
        )
        _pendingMessages.value = _pendingMessages.value + (clientId to optimistic)

        viewModelScope.launch {
            try {
                messageRepository.sendSharedFilmMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    movieTitle = movie.title,
                    directorName = movie.directorName,
                    posterURL = movie.posterURL,
                    tmdbWebURL = movie.tmdbWebURL.ifBlank { null },
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
                    MessageType.SHARED_TRACK -> messageRepository.sendSharedTrackMessage(
                        threadId = message.threadId,
                        fromUserId = message.fromUserId,
                        trackName = message.trackName ?: "",
                        artistName = message.artistName ?: "",
                        albumArtURL = message.albumArtURL,
                        spotifyURL = message.spotifyURL,
                        clientMessageId = messageId,
                    )
                    MessageType.SHARED_FILM -> messageRepository.sendSharedFilmMessage(
                        threadId = message.threadId,
                        fromUserId = message.fromUserId,
                        movieTitle = message.movieTitle ?: "",
                        directorName = message.directorName ?: "",
                        posterURL = message.posterURL,
                        tmdbWebURL = message.tmdbWebURL,
                        clientMessageId = messageId,
                    )
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
