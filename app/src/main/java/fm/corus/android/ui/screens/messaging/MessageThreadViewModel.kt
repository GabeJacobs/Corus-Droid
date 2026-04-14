package fm.corus.android.ui.screens.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _messages = MutableStateFlow<List<CymbalMessage>>(emptyList())
    val messages: StateFlow<List<CymbalMessage>> = _messages.asStateFlow()

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
            messageRepository.listenToMessages(threadId).collect { messages ->
                // Reverse so newest is first — LazyColumn uses reverseLayout = true
                _messages.value = messages.asReversed()
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

    fun sendMessage(threadId: String, text: String) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        val reply = _replyToMessage.value
        viewModelScope.launch {
            try {
                messageRepository.sendTextMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    text = text,
                    replyToMessageId = reply?.id,
                    replyToText = reply?.text,
                    replyToUserId = reply?.fromUserId,
                )
                _replyToMessage.value = null
            } catch (_: Exception) { }
        }
    }

    fun sendImageMessage(threadId: String, imageData: ByteArray) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        viewModelScope.launch {
            try {
                messageRepository.sendImageMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    imageData = imageData,
                )
            } catch (_: Exception) { }
        }
    }

    fun sendGifMessage(threadId: String, gifURL: String) {
        val userId = authRepository.currentUserId ?: return
        val resolvedId = currentThreadId ?: threadId
        viewModelScope.launch {
            try {
                messageRepository.sendGifMessage(
                    threadId = resolvedId,
                    fromUserId = userId,
                    gifURL = gifURL,
                )
            } catch (_: Exception) { }
        }
    }

    fun toggleReaction(threadId: String, messageId: String, emoji: String) {
        val resolvedId = currentThreadId ?: threadId
        viewModelScope.launch {
            try {
                messageRepository.toggleReaction(resolvedId, messageId, emoji)
            } catch (_: Exception) { }
        }
    }
}
