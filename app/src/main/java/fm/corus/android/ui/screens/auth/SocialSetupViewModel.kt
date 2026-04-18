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
) : ViewModel() {

    // ── Contact Sync ──

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _contactsSynced = MutableStateFlow(false)
    val contactsSynced: StateFlow<Boolean> = _contactsSynced.asStateFlow()

    private val _contactMatches = MutableStateFlow<List<CymbalUser>>(emptyList())
    val contactMatches: StateFlow<List<CymbalUser>> = _contactMatches.asStateFlow()

    // ── Follow Friends ──

    private val _popularUsers = MutableStateFlow<List<CymbalUser>>(emptyList())
    val popularUsers: StateFlow<List<CymbalUser>> = _popularUsers.asStateFlow()

    private val _musicBotMatches = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val musicBotMatches: StateFlow<List<SuggestedUserMatch>> = _musicBotMatches.asStateFlow()

    private val _filmBotMatches = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val filmBotMatches: StateFlow<List<SuggestedUserMatch>> = _filmBotMatches.asStateFlow()

    private val _isLoadingSuggestions = MutableStateFlow(true)
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

    // ── Film Bot Preview ──

    private val _filmBotPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val filmBotPosts: StateFlow<List<CymbalPost>> = _filmBotPosts.asStateFlow()

    private val _isLoadingFilmBotPosts = MutableStateFlow(false)
    val isLoadingFilmBotPosts: StateFlow<Boolean> = _isLoadingFilmBotPosts.asStateFlow()

    // ── Preview Playback ──

    /** The user ID whose preview is currently loading or playing. */
    private val _previewingUserId = MutableStateFlow<String?>(null)
    val previewingUserId: StateFlow<String?> = _previewingUserId.asStateFlow()

    /** True while the preview track is being resolved (fetching posts / looking up URL). */
    private val _isPreviewLoading = MutableStateFlow(false)
    val isPreviewLoading: StateFlow<Boolean> = _isPreviewLoading.asStateFlow()

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

    fun loadSuggestions() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoadingSuggestions.value = true
            try {
                // Match iOS: fetch popular users from Firestore directly (not cloud function)
                // so new users with no taste data still see popular accounts.
                val popularDeferred = async {
                    userRepository.fetchPopularUsers(limit = 15, excludeIds = setOf(userId))
                }
                val musicBotsDeferred = async { cloudFunctions.getBotSuggestions(userId, botType = "music") }
                val filmBotsDeferred = async { cloudFunctions.getBotSuggestions(userId, botType = "film") }

                _popularUsers.value = popularDeferred.await()
                _musicBotMatches.value = musicBotsDeferred.await()
                _filmBotMatches.value = filmBotsDeferred.await()
            } catch (_: Exception) { }
            _isLoadingSuggestions.value = false
        }
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

    fun loadFilmBotPosts(userId: String) {
        val viewerId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoadingFilmBotPosts.value = true
            try {
                val posts = postRepository.getProfilePosts(userId, viewerId, limit = 15)
                _filmBotPosts.value = posts.filter { it.isMovie }
            } catch (_: Exception) { }
            _isLoadingFilmBotPosts.value = false
        }
    }

    fun saveMusicService(service: MusicService) {
        viewModelScope.launch {
            musicServicePreference.syncToFirestore(service)
        }
    }

    fun playUserPreview(userId: String) {
        val viewerId = authRepository.currentUserId ?: return

        // If same user is already playing, toggle pause
        if (_previewingUserId.value == userId && nowPlayingManager.isPlaying) {
            nowPlayingManager.togglePlayPause()
            return
        }
        // If same user is paused, resume
        if (_previewingUserId.value == userId && nowPlayingManager.currentTrackId != null) {
            nowPlayingManager.togglePlayPause()
            return
        }

        _previewingUserId.value = userId
        _isPreviewLoading.value = true
        viewModelScope.launch {
            try {
                val posts = postRepository.getProfilePosts(userId, viewerId, limit = 5)
                val post = posts.firstOrNull { it.isTrack } ?: run {
                    _isPreviewLoading.value = false
                    _previewingUserId.value = null
                    return@launch
                }
                val track = post.track
                nowPlayingManager.play(
                    trackId = track.id,
                    trackName = track.name,
                    artistName = track.artistName,
                    albumArtURL = track.albumArtURL,
                    previewUrl = track.previewUrl,
                )
            } catch (_: Exception) {
                _previewingUserId.value = null
            }
            _isPreviewLoading.value = false
        }
    }

    fun stopPreview() {
        nowPlayingManager.stop()
        _previewingUserId.value = null
    }

    /** Remembers that we've shown the push-permission prompt so the MainTab fallback
     * doesn't re-prompt in the same session. */
    fun markPushPermissionRequested() {
        viewModelScope.launch {
            preferencesDataStore.setHasRequestedPushPermission()
        }
    }
}
