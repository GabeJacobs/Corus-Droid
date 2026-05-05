package fm.corus.android.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.SuggestionReason
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.ui.navigation.SuggestedUsersListRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SuggestedUsersListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val firestoreDataSource: FirestoreDataSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val source: String = savedStateHandle.toRoute<SuggestedUsersListRoute>().source

    private val _suggestions = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val suggestions: StateFlow<List<SuggestedUserMatch>> = _suggestions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // Follow state
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    private val _localFollowedIds = MutableStateFlow<Set<String>>(emptySet())

    val followedIds: StateFlow<Set<String>> = combine(_followingIds, _localFollowedIds) { a, b -> a + b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val pageSize = 20
    private val isPaginated: Boolean get() = source == "popular" || source == "new"

    init {
        val uid = authRepository.currentUserId
        if (uid != null) {
            viewModelScope.launch {
                userRepository.followingIds.collect { ids ->
                    _followingIds.value = ids
                }
            }
            viewModelScope.launch {
                try {
                    val initial = when (source) {
                        "mutualConnections" -> loadMutualConnections(uid)
                        "popular" -> loadPopularUsersPage(uid, afterDocId = null)
                        "new" -> loadNewUsersPage(uid, afterDocId = null)
                        else -> userRepository.getSuggestedUsers(uid)
                    }
                    _suggestions.value = initial
                    _hasMore.value = isPaginated && initial.size >= pageSize
                } catch (_: Exception) { }
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        val uid = authRepository.currentUserId ?: return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val initial = when (source) {
                    "mutualConnections" -> loadMutualConnections(uid)
                    "popular" -> loadPopularUsersPage(uid, afterDocId = null)
                    "new" -> loadNewUsersPage(uid, afterDocId = null)
                    else -> userRepository.getSuggestedUsers(uid)
                }
                _suggestions.value = initial
                _hasMore.value = isPaginated && initial.size >= pageSize
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        if (!isPaginated) return
        if (_isLoadingMore.value || !_hasMore.value) return
        val uid = authRepository.currentUserId ?: return
        val lastId = _suggestions.value.lastOrNull()?.user?.id ?: return

        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val page = when (source) {
                    "popular" -> loadPopularUsersPage(uid, afterDocId = lastId)
                    "new" -> loadNewUsersPage(uid, afterDocId = lastId)
                    else -> emptyList()
                }
                val existingIds = _suggestions.value.map { it.user.id }.toSet()
                val deduped = page.filter { it.user.id !in existingIds }
                _suggestions.value = _suggestions.value + deduped
                _hasMore.value = page.size >= pageSize
            } catch (_: Exception) {
                _hasMore.value = false
            }
            _isLoadingMore.value = false
        }
    }

    private suspend fun loadMutualConnections(uid: String): List<SuggestedUserMatch> {
        var mutuals = firestoreDataSource.fetchPrecomputedMutualConnections(uid, limit = 50)
        if (mutuals.isEmpty()) {
            val followingIds = firestoreDataSource.fetchFollowingIds(uid)
            val excludeIds = followingIds + uid
            mutuals = firestoreDataSource.fetchFriendsOfFriends(uid, excludeIds, limit = 50)
        }
        return mutuals
            .sortedByDescending { it.mutualCount }
            .map { mc ->
                SuggestedUserMatch(
                    user = mc.user,
                    matchData = null,
                    suggestionReason = SuggestionReason(
                        mutualNames = mc.mutualUsernames,
                        mutualCount = mc.mutualCount,
                    ),
                )
            }
    }

    private suspend fun loadPopularUsersPage(uid: String, afterDocId: String?): List<SuggestedUserMatch> {
        val users = userRepository.fetchPopularUsersPaginated(
            limit = pageSize,
            excludeIds = setOf(uid),
            afterDocId = afterDocId,
        )
        return users.map { SuggestedUserMatch(user = it, matchData = null, suggestionReason = null) }
    }

    private suspend fun loadNewUsersPage(uid: String, afterDocId: String?): List<SuggestedUserMatch> {
        val users = userRepository.fetchNewUsersPaginated(
            limit = pageSize,
            excludeIds = setOf(uid),
            afterDocId = afterDocId,
        )
        return users.map { SuggestedUserMatch(user = it, matchData = null, suggestionReason = null) }
    }

    fun isFollowed(userId: String): Boolean {
        return _localFollowedIds.value.contains(userId) || _followingIds.value.contains(userId)
    }

    fun toggleFollow(user: CymbalUser) {
        val uid = authRepository.currentUserId ?: return
        val isCurrentlyFollowed = isFollowed(user.id)
        viewModelScope.launch {
            if (isCurrentlyFollowed) {
                _localFollowedIds.value = _localFollowedIds.value - user.id
                _followingIds.value = _followingIds.value - user.id
                try { userRepository.unfollowUser(uid, user.id) } catch (_: Exception) {
                    _followingIds.value = _followingIds.value + user.id
                }
            } else {
                _localFollowedIds.value = _localFollowedIds.value + user.id
                try { userRepository.followUser(uid, user.id) } catch (_: Exception) {
                    _localFollowedIds.value = _localFollowedIds.value - user.id
                }
            }
        }
    }
}
