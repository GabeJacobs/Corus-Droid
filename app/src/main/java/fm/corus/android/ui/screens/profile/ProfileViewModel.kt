package fm.corus.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudFunctions: CloudFunctionsDataSource,
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    val nowPlayingManager: NowPlayingManager,
) : ViewModel() {

    val isClubMember = subscriptionRepository.isClubMember

    fun uploadAvatar(imageData: ByteArray) {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                userRepository.uploadAvatar(uid, imageData)
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
            } catch (_: Exception) { }
        }
    }

    fun generatePlaylist() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            nowPlayingManager.generateProfilePlaylist(userId)
        }
    }

    private val _profile = MutableStateFlow<CymbalUser?>(null)
    val profile: StateFlow<CymbalUser?> = _profile.asStateFlow()

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isSavingStyle = MutableStateFlow(false)
    val isSavingStyle: StateFlow<Boolean> = _isSavingStyle.asStateFlow()

    private val _currentSegment = MutableStateFlow(0)

    private val _hasMore = MutableStateFlow(mapOf(0 to true, 1 to true, 2 to true))
    val hasMore: StateFlow<Map<Int, Boolean>> = _hasMore.asStateFlow()

    private val lastTimestampPerSegment = mutableMapOf<Int, Long?>()

    private val PAGE_SIZE = 30

    fun loadProfile() {
        val userId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                loadSegment(0)
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
                loadSegment(_currentSegment.value)
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun saveStyleSelections(fields: Map<String, Any>) {
        val uid = authRepository.currentUserId ?: return
        _isSavingStyle.value = true
        viewModelScope.launch {
            try {
                userRepository.updateUserProfile(uid, fields)
                authRepository.refreshUserProfile()
                _profile.value = authRepository.userProfile.value
            } catch (_: Exception) { }
            _isSavingStyle.value = false
        }
    }

    fun loadSegment(index: Int) {
        val userId = authRepository.currentUserId ?: return
        _currentSegment.value = index
        // Reset pagination for this segment
        lastTimestampPerSegment[index] = null
        _hasMore.value = _hasMore.value.toMutableMap().apply { this[index] = true }
        viewModelScope.launch {
            try {
                val posts = fetchSegmentPosts(userId, index, lastTimestamp = null)
                _posts.value = posts
                if (posts.isNotEmpty()) {
                    lastTimestampPerSegment[index] = posts.last().timestamp.time
                }
                if (posts.size < PAGE_SIZE) {
                    _hasMore.value = _hasMore.value.toMutableMap().apply { this[index] = false }
                }
            } catch (_: Exception) { }
        }
    }

    fun loadMoreForSegment(segment: Int) {
        if (_hasMore.value[segment] != true) return
        if (_isLoadingMore.value) return
        val userId = authRepository.currentUserId ?: return
        val cursor = lastTimestampPerSegment[segment] ?: return

        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val newPosts = fetchSegmentPosts(userId, segment, lastTimestamp = cursor)
                _posts.value = _posts.value + newPosts
                if (newPosts.isNotEmpty()) {
                    lastTimestampPerSegment[segment] = newPosts.last().timestamp.time
                }
                if (newPosts.size < PAGE_SIZE) {
                    _hasMore.value = _hasMore.value.toMutableMap().apply { this[segment] = false }
                }
            } catch (_: Exception) { }
            _isLoadingMore.value = false
        }
    }

    private suspend fun fetchSegmentPosts(userId: String, segment: Int, lastTimestamp: Long?): List<CymbalPost> {
        return when (segment) {
            0 -> cloudFunctions.getProfilePosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = lastTimestamp)
            1 -> cloudFunctions.getLikedPosts(userId, userId, limit = PAGE_SIZE, lastTimestamp = lastTimestamp)
            2 -> cloudFunctions.getSavedPosts(userId, limit = PAGE_SIZE, lastTimestamp = lastTimestamp)
            else -> emptyList()
        }
    }
}
