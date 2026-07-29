package fm.corus.android.ui.screens.destination

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.AlbumCatalog
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.NowPlayingManager
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
 * Album page: catalog header + tracklist via getAlbumCatalog, posts via
 * getAlbumPosts. The albumId may be a Spotify album id OR `am:{appleAlbumId}`
 * — it is passed through untouched everywhere. Catalog and posts load
 * independently (per-section errors, never an infinite skeleton).
 */
@HiltViewModel
class AlbumPageViewModel @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    val nowPlayingManager: NowPlayingManager,
    val analyticsService: AnalyticsService,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val remoteConfigService: RemoteConfigService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Send-side gate for the "..." Share entry on this page. */
    val entityShareEnabled: Boolean get() = remoteConfigService.entityShareEnabled

    /** Prototype gate for the immersive (blurred-cover hero + frosted collapsing
     *  bar) album header. Shares the artist header's debug-on / RC-gated flag. */
    val immersiveHeaderEnabled: Boolean
        get() = remoteConfigService.immersiveArtistHeaderEnabled

    val prereleaseAlbumPagesEnabled: Boolean
        get() = remoteConfigService.prereleaseAlbumPagesEnabled

    // ── Album share sheet ── (mirrors song-detail share plumbing; sends a
    // `sharedAlbum` DM deep-linking to this page. Reuses rankShareContacts.)

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

    fun sendAlbumToUser(
        userId: String,
        albumId: String,
        title: String,
        artistName: String,
        coverUrl: String?,
        year: String?,
        message: String,
    ) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThread(currentUserId, userId)
                messageRepository.sendSharedAlbumMessage(
                    threadId = threadId,
                    fromUserId = currentUserId,
                    text = message.trim(),
                    albumId = albumId,
                    title = title,
                    artistName = artistName,
                    coverUrl = coverUrl,
                    year = year,
                )
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_send_post))
            }
        }
    }

    private val _catalog = MutableStateFlow<AlbumCatalog?>(null)
    val catalog: StateFlow<AlbumCatalog?> = _catalog.asStateFlow()

    private val _isCatalogLoading = MutableStateFlow(true)
    val isCatalogLoading: StateFlow<Boolean> = _isCatalogLoading.asStateFlow()

    private val _catalogError = MutableStateFlow(false)
    val catalogError: StateFlow<Boolean> = _catalogError.asStateFlow()

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _uniquePosterCount = MutableStateFlow(0)
    val uniquePosterCount: StateFlow<Int> = _uniquePosterCount.asStateFlow()

    // Per-track share counts (track id → count) for the tracklist's trailing
    // "N shared" slot, from getAlbumPosts. Only tracks with a non-zero count are
    // present; absent keys render a blank trailing slot.
    private val _trackShareCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val trackShareCounts: StateFlow<Map<String, Int>> = _trackShareCounts.asStateFlow()

    private val _isPostsLoading = MutableStateFlow(true)
    val isPostsLoading: StateFlow<Boolean> = _isPostsLoading.asStateFlow()

    private val _postsError = MutableStateFlow(false)
    val postsError: StateFlow<Boolean> = _postsError.asStateFlow()

    private var loadedAlbumId: String? = null

    fun load(albumId: String) {
        if (loadedAlbumId == albumId) return
        loadedAlbumId = albumId
        loadCatalog(albumId)
        loadPosts(albumId)
    }

    /** Also the "Try again" hook for a failed catalog fetch. */
    fun loadCatalog(albumId: String) {
        viewModelScope.launch {
            _isCatalogLoading.value = true
            _catalogError.value = false
            try {
                _catalog.value = cloudFunctions.fetchAlbumCatalog(albumId)
            } catch (_: Exception) {
                _catalogError.value = true
            }
            _isCatalogLoading.value = false
        }
    }

    private fun loadPosts(albumId: String) {
        viewModelScope.launch {
            _isPostsLoading.value = true
            _postsError.value = false
            try {
                val page = cloudFunctions.fetchAlbumPosts(albumId, pageSize = PAGE_SIZE)
                _posts.value = page.posts
                _uniquePosterCount.value = page.uniquePosterCount
                _trackShareCounts.value = page.trackShareCounts
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
