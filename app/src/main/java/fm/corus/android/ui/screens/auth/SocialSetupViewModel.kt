package fm.corus.android.ui.screens.auth

import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.local.PreferencesDataStore
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.MusicServicePreference
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.NowPlayingState
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SocialSetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val firestoreDataSource: FirestoreDataSource,
    private val nowPlayingManager: NowPlayingManager,
    private val musicServicePreference: MusicServicePreference,
    private val preferencesDataStore: PreferencesDataStore,
    private val remoteConfigService: RemoteConfigService,
    val analyticsService: AnalyticsService,
) : ViewModel() {

    /** Whether the TIDAL option should appear in the music-service picker. */
    val tidalEnabled: Boolean
        get() = remoteConfigService.tidalEnabled

    /** Whether the Deezer option should appear in the music-service picker. */
    val deezerEnabled: Boolean
        get() = remoteConfigService.deezerEnabled

    // ── Contact Sync ──

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _contactsSynced = MutableStateFlow(false)
    val contactsSynced: StateFlow<Boolean> = _contactsSynced.asStateFlow()

    private val _contactMatches = MutableStateFlow<List<CymbalUser>>(emptyList())
    val contactMatches: StateFlow<List<CymbalUser>> = _contactMatches.asStateFlow()

    // ── Follow Friends ──

    private val _isLoadingSuggestions = MutableStateFlow(false)
    val isLoadingSuggestions: StateFlow<Boolean> = _isLoadingSuggestions.asStateFlow()

    private val _followedIds = MutableStateFlow<Set<String>>(emptySet())
    val followedIds: StateFlow<Set<String>> = _followedIds.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val searchResults: StateFlow<List<CymbalUser>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isFinishing = MutableStateFlow(false)
    val isFinishing: StateFlow<Boolean> = _isFinishing.asStateFlow()

    // ── User Preview Sheet ──

    /** The user being previewed in the half-sheet, or null when closed. */
    private val _previewSheetUser = MutableStateFlow<CymbalUser?>(null)
    val previewSheetUser: StateFlow<CymbalUser?> = _previewSheetUser.asStateFlow()

    private val _previewSheetPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val previewSheetPosts: StateFlow<List<CymbalPost>> = _previewSheetPosts.asStateFlow()

    private val _previewSheetIsLoading = MutableStateFlow(false)
    val previewSheetIsLoading: StateFlow<Boolean> = _previewSheetIsLoading.asStateFlow()

    private val _previewSheetIsLoadingMore = MutableStateFlow(false)
    val previewSheetIsLoadingMore: StateFlow<Boolean> = _previewSheetIsLoadingMore.asStateFlow()

    private val _previewSheetHasMore = MutableStateFlow(true)
    val previewSheetHasMore: StateFlow<Boolean> = _previewSheetHasMore.asStateFlow()

    val nowPlayingManagerInstance: NowPlayingManager get() = nowPlayingManager

    val nowPlayingState: StateFlow<NowPlayingState> = nowPlayingManager.state

    val currentUserId: String? get() = authRepository.currentUserId

    fun syncContacts(contentResolver: ContentResolver) {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val phoneNumbers = readContactPhoneNumbers(contentResolver)
                if (phoneNumbers.isNotEmpty()) {
                    // Store synced contacts and find matches in parallel
                    val storeJob = async { firestoreDataSource.storeSyncedContacts(userId, phoneNumbers) }
                    val matchesJob = async {
                        firestoreDataSource.fetchUsersByPhoneNumbers(phoneNumbers, setOf(userId))
                    }
                    val notifyJob = async { cloudFunctions.notifyContactsOnSync() }

                    storeJob.await()
                    _contactMatches.value = matchesJob.await()
                    notifyJob.await()
                }
            } catch (_: Exception) { }
            preferencesDataStore.setContactsSyncStatus("synced")
            _contactsSynced.value = true
            _isSyncing.value = false
            analyticsService.logContactsSynced(_contactMatches.value.size)
        }
    }

    private fun readContactPhoneNumbers(contentResolver: ContentResolver): List<String> {
        val numbers = mutableSetOf<String>()
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )
            cursor?.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)?.replace(Regex("[^+\\d]"), "")
                    if (!number.isNullOrBlank()) numbers.add(number)
                }
            }
        } catch (_: Exception) { }
        return numbers.toList()
    }

    /** Kept as a no-op so existing call sites (notably SocialSetupFlow.kt) don't
     *  break — popular users now load via the embedded HorizontalPopularUsersRail
     *  composable, and bots are no longer surfaced in onboarding. */
    fun loadSuggestions() {
        // intentionally empty
    }

    fun searchUsers(query: String) {
        _searchQuery.value = query
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                _searchResults.value = userRepository.searchUsers(query, limit = 10)
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    fun toggleFollow(userId: String) {
        val currentUserId = authRepository.currentUserId ?: return
        val isFollowed = _followedIds.value.contains(userId)

        // Optimistic update
        _followedIds.value = if (isFollowed) _followedIds.value - userId else _followedIds.value + userId

        viewModelScope.launch {
            try {
                if (isFollowed) {
                    userRepository.unfollowUser(currentUserId, userId)
                } else {
                    userRepository.followUser(currentUserId, userId)
                }
            } catch (_: Exception) {
                // Revert
                _followedIds.value = if (isFollowed) _followedIds.value + userId else _followedIds.value - userId
            }
        }
    }

    fun saveMusicService(service: MusicService) {
        analyticsService.logMusicServiceSelected(service.value)
        viewModelScope.launch {
            musicServicePreference.syncToFirestore(service)
        }
    }

    /** Open the user-preview half-sheet for [user] and start fetching their posts. */
    fun openUserPreview(user: CymbalUser) {
        val viewerId = authRepository.currentUserId ?: return
        _previewSheetUser.value = user
        _previewSheetPosts.value = emptyList()
        _previewSheetHasMore.value = true
        _previewSheetIsLoading.value = true
        viewModelScope.launch {
            try {
                val posts = postRepository.getProfilePosts(
                    userId = user.id,
                    viewerId = viewerId,
                    limit = PREVIEW_PAGE_SIZE,
                )
                _previewSheetPosts.value = posts
                _previewSheetHasMore.value = posts.size == PREVIEW_PAGE_SIZE
            } catch (_: Exception) {
                _previewSheetHasMore.value = false
            }
            _previewSheetIsLoading.value = false
        }
    }

    /** Fetch the next page of preview posts for the open sheet. */
    fun loadMorePreviewPosts() {
        val user = _previewSheetUser.value ?: return
        val viewerId = authRepository.currentUserId ?: return
        if (_previewSheetIsLoadingMore.value || !_previewSheetHasMore.value) return
        val cursor = _previewSheetPosts.value.lastOrNull()?.timestamp?.time ?: return
        _previewSheetIsLoadingMore.value = true
        viewModelScope.launch {
            try {
                val next = postRepository.getProfilePosts(
                    userId = user.id,
                    viewerId = viewerId,
                    limit = PREVIEW_PAGE_SIZE,
                    lastTimestamp = cursor,
                )
                val existingIds = _previewSheetPosts.value.map { it.id }.toSet()
                val deduped = next.filter { it.id !in existingIds }
                _previewSheetPosts.value = _previewSheetPosts.value + deduped
                _previewSheetHasMore.value = next.size == PREVIEW_PAGE_SIZE
            } catch (_: Exception) {
                _previewSheetHasMore.value = false
            }
            _previewSheetIsLoadingMore.value = false
        }
    }

    fun closeUserPreview() {
        _previewSheetUser.value = null
        _previewSheetPosts.value = emptyList()
        _previewSheetIsLoading.value = false
        _previewSheetIsLoadingMore.value = false
        _previewSheetHasMore.value = true
        nowPlayingManager.stop()
    }

    private companion object {
        private const val PREVIEW_PAGE_SIZE = 15
    }

    /** Remembers that we've shown the push-permission prompt so the MainTab fallback
     * doesn't re-prompt in the same session. */
    fun markPushPermissionRequested() {
        viewModelScope.launch {
            preferencesDataStore.setHasRequestedPushPermission()
        }
    }

    fun logFollowFriendsOnboardingCompleted() {
        analyticsService.logFollowFriendsOnboardingCompleted(_followedIds.value.size)
    }
}
