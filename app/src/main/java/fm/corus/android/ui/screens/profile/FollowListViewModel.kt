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

/** Which follow list a [FollowListViewModel] instance is driving. */
enum class FollowListMode { FOLLOWERS, FOLLOWING, MUTUAL }

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

    // Mutual tab only: total mutual count (capped server-side). -1 until the
    // first page resolves, so the tab can stay hidden until we know it's > 0.
    private val _mutualCount = MutableStateFlow(-1)
    val mutualCount: StateFlow<Int> = _mutualCount.asStateFlow()
    private val _mutualCountCapped = MutableStateFlow(false)
    val mutualCountCapped: StateFlow<Boolean> = _mutualCountCapped.asStateFlow()
    // Flips true once the mutuals first-page load has settled (success OR error)
    // so the tab strip can wait and paint complete instead of adding the Mutual
    // tab a beat after the list is already on screen.
    private val _mutualResolved = MutableStateFlow(false)
    val mutualResolved: StateFlow<Boolean> = _mutualResolved.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId

    private var lastDocument: DocumentSnapshot? = null
    private var mutualCursor: String? = null
    private var currentUserId_: String = ""
    private var currentMode: FollowListMode = FollowListMode.FOLLOWERS
    private var didStartLoad = false

    private var didStartMutualCount = false

    /**
     * Cheap mutual COUNT for the tab label/visibility, computed on-device from
     * the cached following set (reads ≈ overlap) and cached per profile. Drives
     * the tab strip; the paginated mutual LIST is loaded separately by
     * [loadFollowList] only when the Mutual tab is actually opened.
     */
    fun loadMutualCount(viewerId: String, profileId: String) {
        if (didStartMutualCount) return
        didStartMutualCount = true
        viewModelScope.launch {
            try {
                val r = userRepository.mutualFollowerCount(viewerId, profileId)
                _mutualCount.value = r.count
                _mutualCountCapped.value = r.capped
            } catch (_: Exception) {
                _mutualCount.value = 0 // resolve (tab hidden) on error
            } finally {
                _mutualResolved.value = true
            }
        }
    }

    /**
     * Idempotent: the tab wrapper may trigger this from both an eager
     * mutual-count load and the pager page's own LaunchedEffect, so only the
     * first call does work.
     */
    fun loadFollowList(userId: String, mode: FollowListMode) {
        if (didStartLoad) return
        didStartLoad = true
        viewModelScope.launch {
            _isLoading.value = true
            _hasMore.value = true
            lastDocument = null
            mutualCursor = null
            currentUserId_ = userId
            currentMode = mode
            try {
                if (mode == FollowListMode.MUTUAL) {
                    val page = userRepository.fetchMutualFollowers(userId, null, PAGE_SIZE)
                    _mutualCount.value = page.mutualCount
                    _mutualCountCapped.value = page.mutualCountCapped
                    mutualCursor = page.nextCursor
                    _users.value = page.users
                    if (page.nextCursor == null) _hasMore.value = false
                    // The viewer follows every mutual by definition.
                    _followingStatus.value = page.users.associate { it.id to true }
                } else {
                    val result = if (mode == FollowListMode.FOLLOWERS) {
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
                    if (mode == FollowListMode.FOLLOWERS) {
                        _followersOfTarget.value = result.users.map { it.id }.toSet()
                    }
                }
            } catch (_: Exception) { }
            if (mode == FollowListMode.MUTUAL) _mutualResolved.value = true
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

                // Keep only candidates who are in this list. For mutual that's
                // the intersection of the profile's followers and the viewer's
                // following (someone you follow who also follows the profile).
                val memberIds = when (currentMode) {
                    FollowListMode.FOLLOWERS ->
                        userRepository.checkFollowerStatusBatch(currentUserId_, candidateIds)
                    FollowListMode.FOLLOWING ->
                        userRepository.checkFollowingStatusBatch(currentUserId_, candidateIds)
                    FollowListMode.MUTUAL -> {
                        val inProfileFollowers =
                            userRepository.checkFollowerStatusBatch(currentUserId_, candidateIds)
                        val viewerId = authRepository.currentUserId
                        if (viewerId != null) {
                            val inViewerFollowing =
                                userRepository.checkFollowingStatusBatch(viewerId, candidateIds)
                            inProfileFollowers intersect inViewerFollowing
                        } else {
                            emptySet()
                        }
                    }
                }
                val members = candidates.filter { memberIds.contains(it.id) }

                // Follow-button state for the matches, relative to the viewer.
                // Mutual matches are followed by the viewer by definition.
                val myFollowing = userRepository.followingIds.value
                _followingStatus.value = _followingStatus.value +
                    members.associate {
                        it.id to (currentMode == FollowListMode.MUTUAL || myFollowing.contains(it.id))
                    }
                if (currentMode == FollowListMode.FOLLOWERS) {
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
        if (currentMode == FollowListMode.MUTUAL) {
            val cursor = mutualCursor
            if (cursor == null) { _hasMore.value = false; return }
            val page = userRepository.fetchMutualFollowers(currentUserId_, cursor, PAGE_SIZE)
            _users.value = _users.value + page.users
            mutualCursor = page.nextCursor
            if (page.nextCursor == null) _hasMore.value = false
            _followingStatus.value = _followingStatus.value +
                page.users.associate { it.id to true }
            return
        }

        val result = if (currentMode == FollowListMode.FOLLOWERS) {
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
        if (currentMode == FollowListMode.FOLLOWERS) {
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
