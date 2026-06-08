package fm.corus.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.SearchSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactFriendsListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val firestoreDataSource: FirestoreDataSource,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val analyticsService: AnalyticsService,
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    val contacts: StateFlow<List<CymbalUser>> = _contacts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Follow state
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    private val _localFollowedIds = MutableStateFlow<Set<String>>(emptySet())

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
                    val phoneNumbers = firestoreDataSource.fetchSyncedContacts(uid)
                    if (phoneNumbers.isNotEmpty()) {
                        val ids = cloudFunctions.findContactMatches(phoneNumbers).filter { it != uid }
                        _contacts.value = userRepository.fetchUsersByIdsBatched(ids)
                    }
                } catch (_: Exception) { }
                _isLoading.value = false
            }
        }
    }

    fun isFollowed(userId: String): Boolean {
        return _localFollowedIds.value.contains(userId) || _followingIds.value.contains(userId)
    }

    fun toggleFollow(user: CymbalUser) {
        val uid = authRepository.currentUserId ?: return
        val isCurrentlyFollowed = isFollowed(user.id)
        // This screen only ever shows the FriendsOnCorus section, so the
        // section is fixed.
        if (isCurrentlyFollowed) {
            analyticsService.logSearchSectionUserUnfollowed(SearchSection.FriendsOnCorus, user.id)
        } else {
            analyticsService.logSearchSectionUserFollowed(SearchSection.FriendsOnCorus, user.id)
        }
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

    /** Fire `search_section_user_tapped` for the FriendsOnCorus see-all list. */
    fun logUserTapped(userId: String) {
        analyticsService.logSearchSectionUserTapped(SearchSection.FriendsOnCorus, userId)
    }
}
