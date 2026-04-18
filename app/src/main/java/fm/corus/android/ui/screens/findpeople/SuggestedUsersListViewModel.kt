package fm.corus.android.ui.screens.findpeople

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

    // Follow state
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    private val _localFollowedIds = MutableStateFlow<Set<String>>(emptySet())

    val followedIds: StateFlow<Set<String>> = combine(_followingIds, _localFollowedIds) { a, b -> a + b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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
                    _suggestions.value = when (source) {
                        "mutualConnections" -> loadMutualConnections(uid)
                        "popular" -> loadPopularUsers(uid)
                        else -> userRepository.getSuggestedUsers(uid)
                    }
                } catch (_: Exception) { }
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadMutualConnections(uid: String): List<SuggestedUserMatch> {
        var mutuals = firestoreDataSource.fetchPrecomputedMutualConnections(uid, limit = 50)
        if (mutuals.isEmpty()) {
            val followingIds = firestoreDataSource.fetchFollowingIds(uid)
            val excludeIds = followingIds + uid
            mutuals = firestoreDataSource.fetchFriendsOfFriends(uid, excludeIds, limit = 50)
        }
        return mutuals.map { (user, names) ->
            SuggestedUserMatch(
                user = user,
                matchData = null,
                suggestionReason = SuggestionReason(mutualNames = names),
            )
        }
    }

    private suspend fun loadPopularUsers(uid: String): List<SuggestedUserMatch> {
        val users = userRepository.fetchPopularUsers(limit = 50, excludeIds = setOf(uid))
        return users.map { user ->
            SuggestedUserMatch(user = user, matchData = null, suggestionReason = null)
        }
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
