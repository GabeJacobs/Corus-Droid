package fm.corus.android.ui.screens.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalThread
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _threads = MutableStateFlow<List<CymbalThread>>(emptyList())
    val threads: StateFlow<List<CymbalThread>> = _threads.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // New Message picker state
    private val _suggestedContacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    val suggestedContacts: StateFlow<List<CymbalUser>> = _suggestedContacts.asStateFlow()

    private val _isLoadingSuggestions = MutableStateFlow(false)
    val isLoadingSuggestions: StateFlow<Boolean> = _isLoadingSuggestions.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val searchResults: StateFlow<List<CymbalUser>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId

    private var searchJob: Job? = null

    fun loadThreads() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _threads.value = messageRepository.listThreads(userId)
                    .filter { it.lastMessageFromUserId != null }
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun loadSuggestedContacts() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoadingSuggestions.value = true
            try {
                // Use recent DM contacts (same as share sheet)
                val threads = messageRepository.listThreads(userId)
                val contacts = threads.mapNotNull { it.otherUser }

                if (contacts.isNotEmpty()) {
                    _suggestedContacts.value = contacts
                } else {
                    // Fallback: following users (exclude bots)
                    userRepository.prefetchFollowingSet(userId)
                    val followIds = userRepository.followingIds.value
                    val users = followIds.take(20).mapNotNull { uid ->
                        try { userRepository.fetchUserProfile(uid) } catch (_: Exception) { null }
                    }.filter { !it.isBot }
                    _suggestedContacts.value = users
                }
            } catch (_: Exception) {
                _suggestedContacts.value = emptyList()
            }
            _isLoadingSuggestions.value = false
        }
    }

    fun searchUsers(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(300)
            try {
                _searchResults.value = userRepository.searchUsers(query, limit = 15, includeFollowed = true)
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    suspend fun getOrCreateThread(otherUserId: String): String {
        val userId = authRepository.currentUserId ?: throw IllegalStateException("Not signed in")
        return messageRepository.getOrCreateThread(userId, otherUserId)
    }
}
