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

/**
 * Merges a refreshed first page into the already-loaded thread list. Threads
 * not in the refreshed page keep their positions below it (they're older than
 * everything in the newest page), so the list never shrinks and the scroll
 * position holds.
 */
internal fun mergeRefreshedThreads(
    existing: List<CymbalThread>,
    refreshed: List<CymbalThread>,
): List<CymbalThread> {
    val refreshedIds = refreshed.map { it.id }.toSet()
    return refreshed + existing.filter { it.id !in refreshedIds }
}

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

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMoreThreads = MutableStateFlow(false)
    val hasMoreThreads: StateFlow<Boolean> = _hasMoreThreads.asStateFlow()

    private val pageSize = 30

    /** Cursor for the next page — the `updatedAt` of the last thread fetched, in millis. */
    private var nextCursor: Long? = null

    /**
     * The screen's LaunchedEffect re-runs every time it's recomposed after a
     * back navigation. After the first successful load, reloads refresh in
     * place instead of resetting to the first page, which would drop
     * paginated-in threads and yank the scroll position.
     */
    private var hasLoadedThreads = false

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
        if (hasLoadedThreads) {
            refreshThreads(userId)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val page = messageRepository.listThreadsPage(userId, limit = pageSize)
                _threads.value = page.threads.filter { it.lastMessageFromUserId != null }
                nextCursor = page.nextCursor
                _hasMoreThreads.value = page.hasMore && page.nextCursor != null
                hasLoadedThreads = true
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    private fun refreshThreads(userId: String) {
        viewModelScope.launch {
            try {
                val page = messageRepository.listThreadsPage(userId, limit = pageSize)
                val refreshed = page.threads.filter { it.lastMessageFromUserId != null }
                val merged = mergeRefreshedThreads(_threads.value, refreshed)
                val tailEmpty = merged.size == refreshed.size
                _threads.value = merged
                if (tailEmpty) {
                    // Everything loaded fits in the refreshed page, so its cursor is
                    // the list's cursor. With a tail, the existing cursor still
                    // points at the last fetched thread (the tail's end) and stays
                    // valid.
                    nextCursor = page.nextCursor
                    _hasMoreThreads.value = page.hasMore && page.nextCursor != null
                }
            } catch (_: Exception) { }
        }
    }

    fun loadMoreThreads() {
        val userId = authRepository.currentUserId ?: return
        if (_isLoadingMore.value || !_hasMoreThreads.value) return
        val cursor = nextCursor ?: return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val page = messageRepository.listThreadsPage(userId, limit = pageSize, startAfter = cursor)
                val existingIds = _threads.value.map { it.id }.toSet()
                val newThreads = page.threads
                    .filter { it.lastMessageFromUserId != null }
                    .filter { it.id !in existingIds }
                _threads.value = _threads.value + newThreads
                nextCursor = page.nextCursor
                // The recency cursor strictly decreases each page, so it can't loop;
                // stop when the server reports no more or the cursor didn't advance.
                _hasMoreThreads.value = page.hasMore && page.nextCursor != null && page.nextCursor != cursor
            } catch (_: Exception) {
                _hasMoreThreads.value = false
            }
            _isLoadingMore.value = false
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
