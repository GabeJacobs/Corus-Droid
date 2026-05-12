package fm.corus.android.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.HashtagContributor
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HashtagFeedViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val firestoreDataSource: FirestoreDataSource,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<CymbalPost>>(emptyList())
    val posts: StateFlow<List<CymbalPost>> = _posts.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _hasLoadedFollowState = MutableStateFlow(false)
    val hasLoadedFollowState: StateFlow<Boolean> = _hasLoadedFollowState.asStateFlow()

    private val _isTogglingFollow = MutableStateFlow(false)
    val isTogglingFollow: StateFlow<Boolean> = _isTogglingFollow.asStateFlow()

    // Aggregates from `hashtags/{tag}` — gated by `hasLoadedAggregates` so the
    // 3-stat row doesn't flash "0 followers / 0 contributors" on first paint.
    private val _followerCount = MutableStateFlow(0)
    val followerCount: StateFlow<Int> = _followerCount.asStateFlow()

    private val _contributorCount = MutableStateFlow(0)
    val contributorCount: StateFlow<Int> = _contributorCount.asStateFlow()

    private val _recentCount = MutableStateFlow(0)
    val recentCount: StateFlow<Int> = _recentCount.asStateFlow()

    private val _hasLoadedAggregates = MutableStateFlow(false)
    val hasLoadedAggregates: StateFlow<Boolean> = _hasLoadedAggregates.asStateFlow()

    // Gates the posts stat so it shows a skeleton instead of "0 coruses"
    // until the first page returns with the real totalCount.
    private val _hasLoadedPostsPage = MutableStateFlow(false)
    val hasLoadedPostsPage: StateFlow<Boolean> = _hasLoadedPostsPage.asStateFlow()

    private val _topContributors = MutableStateFlow<List<HashtagContributor>>(emptyList())
    val topContributors: StateFlow<List<HashtagContributor>> = _topContributors.asStateFlow()

    private var lastTimestamp: Long? = null
    private var currentHashtag: String? = null
    private var hasInitiatedFollowLoad = false
    private var hasInitiatedAggregateLoad = false

    fun loadHashtagPosts(hashtag: String, refresh: Boolean = false) {
        authRepository.currentUserId ?: return
        currentHashtag = hashtag
        viewModelScope.launch {
            if (_isLoading.value) return@launch
            _isLoading.value = true
            _loadError.value = null
            if (refresh) lastTimestamp = null
            try {
                val page = postRepository.getHashtagPosts(
                    hashtag = hashtag,
                    pageSize = 15,
                    beforeMs = if (refresh) null else lastTimestamp,
                )
                if (refresh) {
                    _posts.value = page.posts
                } else {
                    _posts.value = _posts.value + page.posts
                }
                _totalCount.value = page.totalCount
                _hasMore.value = page.hasMore
                if (page.posts.isNotEmpty()) lastTimestamp = page.posts.last().timestamp.time
                _hasLoadedPostsPage.value = true
            } catch (_: Exception) {
                _loadError.value = "Couldn't load posts"
            }
            _isLoading.value = false
        }
    }

    fun retry() {
        _loadError.value = null
        currentHashtag?.let { loadHashtagPosts(it, refresh = true) }
    }

    fun loadFollowState(hashtag: String) {
        if (hasInitiatedFollowLoad) return
        hasInitiatedFollowLoad = true
        val uid = authRepository.currentUserId
        if (uid == null) {
            _hasLoadedFollowState.value = true
            return
        }
        viewModelScope.launch {
            try {
                _isFollowing.value = firestoreDataSource.isHashtagFollowed(uid, hashtag)
            } catch (_: Exception) { }
            _hasLoadedFollowState.value = true
        }
    }

    fun toggleFollow(hashtag: String) {
        val uid = authRepository.currentUserId ?: return
        if (_isTogglingFollow.value) return
        viewModelScope.launch {
            _isTogglingFollow.value = true
            val wasFollowing = _isFollowing.value
            _isFollowing.value = !wasFollowing
            // Optimistic count bump — server triggers will reconcile, but
            // waiting for round-trip leaves the stats stale for a beat.
            _followerCount.value = (_followerCount.value + if (wasFollowing) -1 else 1)
                .coerceAtLeast(0)
            try {
                if (wasFollowing) {
                    firestoreDataSource.unfollowHashtag(uid, hashtag)
                } else {
                    firestoreDataSource.followHashtag(uid, hashtag)
                }
            } catch (_: Exception) {
                _isFollowing.value = wasFollowing
                _followerCount.value = (_followerCount.value + if (wasFollowing) 1 else -1)
                    .coerceAtLeast(0)
            }
            _isTogglingFollow.value = false
        }
    }

    /** Fetch aggregates + facepile data from `hashtags/{tag}`. Mirrors iOS
     *  `loadHashtagAggregates` + `loadTopContributors`. */
    fun loadAggregatesAndContributors(hashtag: String) {
        if (hasInitiatedAggregateLoad) return
        hasInitiatedAggregateLoad = true
        viewModelScope.launch {
            try {
                val agg = firestoreDataSource.fetchHashtag(hashtag)
                if (agg != null) {
                    _followerCount.value = agg.followerCount.coerceAtLeast(_followerCount.value)
                    _contributorCount.value = agg.contributorCount
                    _recentCount.value = agg.recentCount
                }
            } catch (_: Exception) { }
            try {
                _topContributors.value =
                    firestoreDataSource.fetchTopHashtagContributors(hashtag, limit = 6)
            } catch (_: Exception) { }
            // Mark loaded even on error so the redacted placeholders disappear —
            // zero is the correct value for a tag with no aggregates.
            _hasLoadedAggregates.value = true
        }
    }
}
