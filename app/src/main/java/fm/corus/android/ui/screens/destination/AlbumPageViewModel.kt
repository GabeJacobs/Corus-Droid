package fm.corus.android.ui.screens.destination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.AlbumCatalog
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.remote.CloudFunctionsDataSource
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.service.AnalyticsService
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
) : ViewModel() {

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
