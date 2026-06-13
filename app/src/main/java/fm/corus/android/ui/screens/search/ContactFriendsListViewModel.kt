package fm.corus.android.ui.screens.search

import android.content.ContentResolver
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.local.readContactPhoneNumbers
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.SearchSection
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class ContactFriendsListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val firestoreDataSource: FirestoreDataSource,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val preferencesDataStore: PreferencesDataStore,
    private val analyticsService: AnalyticsService,
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    val contacts: StateFlow<List<CymbalUser>> = _contacts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

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

    /** Current persisted sync status: "notAsked", "skipped", or "synced". */
    suspend fun currentSyncStatus(): String = preferencesDataStore.contactsSyncStatus.first()

    fun logSyncContactsTapped() = analyticsService.logSyncContactsTapped()

    /**
     * Read the device contacts and refresh matches. Used by the Settings
     * entry point — both for a first-time sync after the onboarding/search
     * prompts were dismissed, and to pick up new contacts on a re-sync.
     */
    fun syncContacts(contentResolver: ContentResolver) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            syncWithPhoneNumbers(readContactPhoneNumbers(contentResolver))
        }
    }

    @VisibleForTesting
    internal suspend fun syncWithPhoneNumbers(phoneNumbers: List<String>) {
        val uid = authRepository.currentUserId ?: return
        _isSyncing.value = true
        val firstSync = preferencesDataStore.contactsSyncStatus.first() != "synced"
        preferencesDataStore.setContactsSyncStatus("synced")
        if (phoneNumbers.isNotEmpty()) {
            // supervisorScope + per-await runCatching so a failed callable
            // doesn't cancel the siblings or propagate to viewModelScope
            // (same rationale as SocialSetupViewModel.syncContacts).
            supervisorScope {
                val storeJob = async { firestoreDataSource.storeSyncedContacts(uid, phoneNumbers) }
                // Only ping contacts on the first sync — a re-sync from
                // Settings shouldn't re-notify everyone.
                val notifyJob = if (firstSync) async { cloudFunctions.notifyContactsOnSync() } else null
                val matchesJob = async {
                    val ids = cloudFunctions.findContactMatches(phoneNumbers).filter { it != uid }
                    userRepository.fetchUsersByIdsBatched(ids)
                }
                runCatching { storeJob.await() }
                notifyJob?.let { runCatching { it.await() } }
                // On failure keep whatever the init load already produced.
                runCatching { matchesJob.await() }.getOrNull()?.let { _contacts.value = it }
            }
        }
        if (firstSync) analyticsService.logContactsSynced(_contacts.value.size)
        _isLoading.value = false
        _isSyncing.value = false
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
