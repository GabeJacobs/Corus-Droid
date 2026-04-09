package fm.corus.android.ui.screens.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalMessage
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
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
) : ViewModel() {

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

    fun loadMessages(threadId: String, otherUserId: String) {
        currentThreadId = threadId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load other user's profile for the header
                val profile = userRepository.fetchUserProfile(otherUserId)
                _otherUsername.value = profile?.username ?: ""

                // Load messages
                _messages.value = messageRepository.listMessages(threadId)

                // Mark as read
                val userId = authRepository.currentUserId
                if (userId != null) {
                    messageRepository.markThreadRead(threadId, userId)
                }
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun setReplyTo(message: CymbalMessage?) {
        _replyToMessage.value = message
    }

    fun sendMessage(threadId: String, text: String) {
        val userId = authRepository.currentUserId ?: return
        val reply = _replyToMessage.value
        viewModelScope.launch {
            try {
                messageRepository.sendTextMessage(
                    threadId = threadId,
                    fromUserId = userId,
                    text = text,
                    replyToMessageId = reply?.id,
                    replyToText = reply?.text,
                    replyToUserId = reply?.fromUserId,
                )
                _replyToMessage.value = null
                _messages.value = messageRepository.listMessages(threadId)
            } catch (_: Exception) { }
        }
    }

    fun sendImageMessage(threadId: String, imageData: ByteArray) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                messageRepository.sendImageMessage(
                    threadId = threadId,
                    fromUserId = userId,
                    imageData = imageData,
                )
                _messages.value = messageRepository.listMessages(threadId)
            } catch (_: Exception) { }
        }
    }

    fun toggleReaction(threadId: String, messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                messageRepository.toggleReaction(threadId, messageId, emoji)
                _messages.value = messageRepository.listMessages(threadId)
            } catch (_: Exception) { }
        }
    }
}
