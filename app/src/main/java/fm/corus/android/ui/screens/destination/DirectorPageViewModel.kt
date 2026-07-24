package fm.corus.android.ui.screens.destination

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.DirectorDetail
import fm.corus.android.data.model.UserLite
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.screens.feed.SHARE_CONTACTS_TARGET
import fm.corus.android.ui.screens.feed.rankShareContacts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the director page AND its filmography see-all screen (the see-all
 * calls [loadCatalog] only; the in-memory catalog cache dedupes the fetch).
 * Direct mirror of [ArtistPageViewModel] with getDirectorDetail /
 * getDirectorPosts.
 */
@HiltViewModel
class DirectorPageViewModel @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    val analyticsService: AnalyticsService,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val remoteConfigService: RemoteConfigService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Send-side gate for the "..." Share entry on this page. */
    val entityShareEnabled: Boolean get() = remoteConfigService.entityShareEnabled

    /** Prototype gate for the immersive (blurred-photo hero + frosted collapsing
     *  bar) director header. Shares the artist header's debug-on / RC-gated flag. */
    val immersiveHeaderEnabled: Boolean
        get() = remoteConfigService.immersiveArtistHeaderEnabled

    // ── Director share sheet ── (mirrors song-detail share plumbing; sends a
    // `sharedDirector` DM deep-linking to this page. Reuses rankShareContacts.)

    private val _shareSearchResults = MutableStateFlow<List<CymbalUser>>(emptyList())
    val shareSearchResults: StateFlow<List<CymbalUser>> = _shareSearchResults.asStateFlow()

    private val _recentShareContacts = MutableStateFlow<List<CymbalUser>>(emptyList())
    val recentShareContacts: StateFlow<List<CymbalUser>> = _recentShareContacts.asStateFlow()

    private val _isShareSearching = MutableStateFlow(false)
    val isShareSearching: StateFlow<Boolean> = _isShareSearching.asStateFlow()

    private val _isLoadingShareContacts = MutableStateFlow(true)
    val isLoadingShareContacts: StateFlow<Boolean> = _isLoadingShareContacts.asStateFlow()

    private var shareSearchJob: Job? = null

    fun loadRecentShareContacts() {
        val userId = authRepository.currentUserId ?: return
        _isLoadingShareContacts.value = true
        viewModelScope.launch {
            try {
                val shareRecipients = runCatching { messageRepository.listShareRecipients(12) }.getOrDefault(emptyList())
                val threadContacts = messageRepository.listThreads(userId).mapNotNull { it.otherUser }
                val followFallback = if (rankShareContacts(shareRecipients, threadContacts, emptyList()).size < SHARE_CONTACTS_TARGET) {
                    val following = userRepository.fetchFollowingPaginated(userId, limit = 20).users
                    val followers = userRepository.fetchFollowersPaginated(userId, limit = 20).users
                    following + followers
                } else {
                    emptyList()
                }
                _recentShareContacts.value = rankShareContacts(shareRecipients, threadContacts, followFallback)
            } catch (_: Exception) { }
            _isLoadingShareContacts.value = false
        }
    }

    fun searchShareUsers(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            shareSearchJob?.cancel()
            _shareSearchResults.value = emptyList()
            _isShareSearching.value = false
            return
        }
        shareSearchJob?.cancel()
        shareSearchJob = viewModelScope.launch {
            _isShareSearching.value = true
            delay(250)
            try {
                _shareSearchResults.value = userRepository.searchUsers(trimmed, includeFollowed = true)
            } catch (_: Exception) {
                _shareSearchResults.value = emptyList()
            }
            _isShareSearching.value = false
        }
    }

    fun sendDirectorToUser(userId: String, directorId: String, name: String, imageUrl: String?, message: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThread(currentUserId, userId)
                messageRepository.sendSharedDirectorMessage(
                    threadId = threadId,
                    fromUserId = currentUserId,
                    text = message.trim(),
                    directorId = directorId,
                    name = name,
                    imageUrl = imageUrl,
                )
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_send_post))
            }
        }
    }

    private val _detail = MutableStateFlow<DirectorDetail?>(null)
    val detail: StateFlow<DirectorDetail?> = _detail.asStateFlow()

    private val _isCatalogLoading = MutableStateFlow(true)
    val isCatalogLoading: StateFlow<Boolean> = _isCatalogLoading.asStateFlow()

    private val _catalogError = MutableStateFlow(false)
    val catalogError: StateFlow<Boolean> = _catalogError.asStateFlow()

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _viewerPosts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val viewerPosts: StateFlow<List<CymbalPost>> = _viewerPosts.asStateFlow()

    private val _posters = MutableStateFlow<List<UserLite>>(emptyList())
    val posters: StateFlow<List<UserLite>> = _posters.asStateFlow()

    private val _uniquePosterCount = MutableStateFlow(0)
    val uniquePosterCount: StateFlow<Int> = _uniquePosterCount.asStateFlow()

    private val _isPostsLoading = MutableStateFlow(true)
    val isPostsLoading: StateFlow<Boolean> = _isPostsLoading.asStateFlow()

    private val _postsError = MutableStateFlow(false)
    val postsError: StateFlow<Boolean> = _postsError.asStateFlow()

    private var loadedCatalogDirectorId: String? = null
    private var loadedPostsDirectorId: String? = null

    fun loadCatalog(directorId: String) {
        if (loadedCatalogDirectorId == directorId) return
        loadedCatalogDirectorId = directorId
        viewModelScope.launch {
            _isCatalogLoading.value = true
            _catalogError.value = false
            try {
                _detail.value = cloudFunctions.fetchDirectorDetail(directorId)
            } catch (_: Exception) {
                _catalogError.value = true
            }
            _isCatalogLoading.value = false
        }
    }

    fun loadPosts(directorId: String, directorNameHint: String? = null) {
        if (loadedPostsDirectorId == directorId) return
        loadedPostsDirectorId = directorId
        viewModelScope.launch {
            _isPostsLoading.value = true
            _postsError.value = false
            try {
                val page = cloudFunctions.fetchDirectorPosts(
                    directorId = directorId,
                    directorName = _detail.value?.name ?: directorNameHint,
                    pageSize = PAGE_SIZE,
                    includeViewerPosts = true,
                )
                _posts.value = page.posts
                _viewerPosts.value = page.viewerPosts
                _posters.value = page.posters
                _uniquePosterCount.value = page.uniquePosterCount
            } catch (_: Exception) {
                _postsError.value = true
            }
            _isPostsLoading.value = false
        }
    }

    companion object {
        const val PAGE_SIZE = 15
    }
}
