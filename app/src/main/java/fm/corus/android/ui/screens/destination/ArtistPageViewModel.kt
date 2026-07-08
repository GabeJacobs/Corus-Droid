package fm.corus.android.ui.screens.destination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.ArtistDetail
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.UserLite
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.service.AnalyticsService
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
) : ViewModel() {

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
