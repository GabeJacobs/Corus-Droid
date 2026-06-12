package fm.corus.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.data.remote.CloudFunctionsDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FollowListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
    }

    private val _users = MutableStateFlow<List<CymbalUser>>(emptyList())
    val users: StateFlow<List<CymbalUser>> = _users.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // Search runs as a scoped global user search (same ranking as the main
    // Users search), filtered to members of this list — not a client-side
    // filter over the loaded pages.
    private val _searchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val searchResults: StateFlow<List<CymbalUser>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    private val _followingStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val followingStatus: StateFlow<Map<String, Boolean>> = _followingStatus.asStateFlow()

    private val _followersOfTarget = MutableStateFlow<Set<String>>(emptySet())

    val currentUserId: String? get() = authRepository.currentUserId

    private var lastDocument: DocumentSnapshot? = null
    private var currentUserId_: String = ""
    private var currentIsFollowers: Boolean = true

    fun loadFollowList(userId: String, isFollowers: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _hasMore.value = true
            lastDocument = null
            currentUserId_ = userId
            currentIsFollowers = isFollowers
            try {
                val result = if (isFollowers) {
                    userRepository.fetchFollowersPaginated(userId, PAGE_SIZE)
                } else {
                    userRepository.fetchFollowingPaginated(userId, PAGE_SIZE)
                }

                _users.value = result.users
                lastDocument = result.lastDocument
                if (result.users.size < PAGE_SIZE) {
                    _hasMore.value = false
                }

                // Check follow status for each
                val myFollowing = userRepository.followingIds.value.ifEmpty {
                    authRepository.currentUserId?.let { myId ->
                        userRepository.prefetchFollowingSet(myId)
                    }
                    userRepository.followingIds.value
                }
                val statuses = result.users.associate { it.id to myFollowing.contains(it.id) }
                _followingStatus.value = statuses

                // If viewing followers, track who follows back
                if (isFollowers) {
                    _followersOfTarget.value = result.users.map { it.id }.toSet()
                }
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (!_hasMore.value || _isLoadingMore.value || _isLoading.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                appendNextPage()
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    /**
     * Scoped search: runs the same ranked global user search as the main Users
     * tab, then keeps only candidates who belong to this follow list (members of
     * `currentUserId_`'s followers/following). One search call plus a batched
     * membership read — debounced, results land all at once. A blank query
     * returns to the browse list.
     */
    fun search(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }

        _isSearching.value = true
        // Adaptive debounce matching the main Users search.
        val debounceMs = when (trimmed.length) {
            1 -> 400L
            2 -> 300L
            in 3..5 -> 200L
            else -> 150L
        }

        searchJob = viewModelScope.launch {
            delay(debounceMs)
            try {
                // No followed-set augmentation: searchTokens already include name
                // prefixes, so prefix + token search surfaces the same people
                // without building the (potentially huge) followed-profile dict.
                val candidates = userRepository.searchUsers(
                    trimmed.lowercase(), limit = 30, includeFollowed = false,
                )
                val candidateIds = candidates.map { it.id }

                // Keep only candidates who are in this list (followers/following
                // of the viewed profile).
                val memberIds = if (currentIsFollowers) {
                    userRepository.checkFollowerStatusBatch(currentUserId_, candidateIds)
                } else {
                    userRepository.checkFollowingStatusBatch(currentUserId_, candidateIds)
                }
                val members = candidates.filter { memberIds.contains(it.id) }

                // Follow-button state for the matches, relative to the viewer.
                val myFollowing = userRepository.followingIds.value
                _followingStatus.value = _followingStatus.value +
                    members.associate { it.id to myFollowing.contains(it.id) }
                if (currentIsFollowers) {
                    _followersOfTarget.value =
                        _followersOfTarget.value + members.map { it.id }.toSet()
                }
                _searchResults.value = members
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    private suspend fun appendNextPage() {
        val result = if (currentIsFollowers) {
            userRepository.fetchFollowersPaginated(currentUserId_, PAGE_SIZE, lastDocument)
        } else {
            userRepository.fetchFollowingPaginated(currentUserId_, PAGE_SIZE, lastDocument)
        }

        _users.value = _users.value + result.users
        lastDocument = result.lastDocument
        if (result.users.size < PAGE_SIZE) {
            _hasMore.value = false
        }

        // Update follow status for new users
        val myFollowing = userRepository.followingIds.value
        val newStatuses = result.users.associate { it.id to myFollowing.contains(it.id) }
        _followingStatus.value = _followingStatus.value + newStatuses

        // If viewing followers, update follow-back tracking
        if (currentIsFollowers) {
            _followersOfTarget.value = _followersOfTarget.value + result.users.map { it.id }.toSet()
        }
    }

    fun toggleFollow(targetUserId: String) {
        val myId = authRepository.currentUserId ?: return
        val currentlyFollowing = _followingStatus.value[targetUserId] ?: false

        // Optimistic update
        _followingStatus.value = _followingStatus.value + (targetUserId to !currentlyFollowing)

        viewModelScope.launch {
            try {
                if (currentlyFollowing) {
                    userRepository.unfollowUser(myId, targetUserId)
                } else {
                    userRepository.followUser(myId, targetUserId)
                }
            } catch (_: Exception) {
                // Rollback
                _followingStatus.value = _followingStatus.value + (targetUserId to currentlyFollowing)
            }
        }
    }

    fun isFollowedByTarget(userId: String): Boolean {
        return _followersOfTarget.value.contains(userId)
    }
}
