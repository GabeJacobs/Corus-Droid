package fm.corus.android.ui.screens.destination

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fm.corus.android.R
import fm.corus.android.data.model.ArtistDetail
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.UserLite
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.MessageRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.domain.MusicServicePreference
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.SongPlayRouting
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
 * Backs the artist page AND its discography see-all screen (the see-all calls
 * [loadCatalog] only; the CloudFunctionsDataSource in-memory catalog cache
 * makes the second fetch a no-op). Catalog and posts load independently so a
 * posts failure never blanks the discography and vice versa — per-section
 * errors, never an infinite skeleton.
 */
@HiltViewModel
class ArtistPageViewModel @Inject constructor(
    private val cloudFunctions: CloudFunctionsDataSource,
    val nowPlayingManager: NowPlayingManager,
    val analyticsService: AnalyticsService,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val remoteConfigService: RemoteConfigService,
    private val musicServicePreference: MusicServicePreference,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    fun catalogListeningEntitled(): Boolean = SongPlayRouting.catalogListeningEntitled(
        context = context,
        service = musicServicePreference.current.value,
        remoteConfig = remoteConfigService,
    )

    /** Send-side gate for the "..." Share entry on this page. */
    val entityShareEnabled: Boolean get() = remoteConfigService.entityShareEnabled

    /** Prototype gate for the immersive (full-bleed hero + frosted collapsing
     *  bar) artist-page header. Debug-on, release RC-gated. */
    val immersiveArtistHeaderEnabled: Boolean
        get() = remoteConfigService.immersiveArtistHeaderEnabled

    // ── Artist share sheet ──
    // Mirrors the song-detail share plumbing (recipient picker + DM send), but
    // shares an *artist*: DMs send a `sharedArtist` message deep-linking to this
    // page. Reuses the package-level rankShareContacts helper.

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

    fun sendArtistToUser(userId: String, artistId: String, name: String, imageUrl: String?, message: String) {
        val currentUserId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            try {
                val threadId = messageRepository.getOrCreateThread(currentUserId, userId)
                messageRepository.sendSharedArtistMessage(
                    threadId = threadId,
                    fromUserId = currentUserId,
                    text = message.trim(),
                    artistId = artistId,
                    name = name,
                    imageUrl = imageUrl,
                )
            } catch (_: Exception) {
                ToastManager.show(context.getString(R.string.feed_toast_failed_send_post))
            }
        }
    }

    private val _detail = MutableStateFlow<ArtistDetail?>(null)
    val detail: StateFlow<ArtistDetail?> = _detail.asStateFlow()

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

    private var loadedCatalogArtistId: String? = null
    private var loadedPostsArtistId: String? = null

    fun loadCatalog(artistId: String, artistNameHint: String? = null) {
        if (loadedCatalogArtistId == artistId) return
        loadedCatalogArtistId = artistId
        viewModelScope.launch {
            _isCatalogLoading.value = true
            _catalogError.value = false
            try {
                _detail.value = cloudFunctions.fetchArtistDetail(artistId, artistNameHint)
            } catch (_: Exception) {
                _catalogError.value = true
            }
            _isCatalogLoading.value = false
        }
    }

    fun loadPosts(artistId: String, artistNameHint: String? = null) {
        if (loadedPostsArtistId == artistId) return
        loadedPostsArtistId = artistId
        viewModelScope.launch {
            _isPostsLoading.value = true
            _postsError.value = false
            try {
                val page = cloudFunctions.fetchArtistPosts(
                    artistId = artistId,
                    artistName = _detail.value?.name ?: artistNameHint,
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

        /** Recent posts shown inline before "See all" (matches web's inlinePostsCap). */
        const val INLINE_POSTS_CAP = 6
    }
}
