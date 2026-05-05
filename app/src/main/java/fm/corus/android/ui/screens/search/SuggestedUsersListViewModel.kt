package fm.corus.android.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.model.SharedMoviePreview
import fm.corus.android.data.model.SharedTrackPreview
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.SuggestionReason
import fm.corus.android.data.remote.FirestoreDataSource
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.ui.navigation.SuggestedUsersListRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SuggestedUsersListViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val firestoreDataSource: FirestoreDataSource,
    private val postRepository: PostRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val source: String = savedStateHandle.toRoute<SuggestedUsersListRoute>().source

    private val _suggestions = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val suggestions: StateFlow<List<SuggestedUserMatch>> = _suggestions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    // Follow state
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    private val _localFollowedIds = MutableStateFlow<Set<String>>(emptySet())

    val followedIds: StateFlow<Set<String>> = combine(_followingIds, _localFollowedIds) { a, b -> a + b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val pageSize = 20
    private val isPaginated: Boolean get() = source == "popular" || source == "new"

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
                    val initial = when (source) {
                        "mutualConnections" -> loadMutualConnections(uid)
                        "popular" -> loadPopularUsersPage(uid, afterDocId = null)
                        "new" -> loadNewUsersPage(uid, afterDocId = null)
                        else -> userRepository.getSuggestedUsers(uid)
                    }
                    _suggestions.value = initial
                    _hasMore.value = isPaginated && initial.size >= pageSize
                } catch (_: Exception) { }
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        val uid = authRepository.currentUserId ?: return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val initial = when (source) {
                    "mutualConnections" -> loadMutualConnections(uid)
                    "popular" -> loadPopularUsersPage(uid, afterDocId = null)
                    "new" -> loadNewUsersPage(uid, afterDocId = null)
                    else -> userRepository.getSuggestedUsers(uid)
                }
                _suggestions.value = initial
                _hasMore.value = isPaginated && initial.size >= pageSize
            } catch (_: Exception) { }
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        if (!isPaginated) return
        if (_isLoadingMore.value || !_hasMore.value) return
        val uid = authRepository.currentUserId ?: return
        val lastId = _suggestions.value.lastOrNull()?.user?.id ?: return

        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val page = when (source) {
                    "popular" -> loadPopularUsersPage(uid, afterDocId = lastId)
                    "new" -> loadNewUsersPage(uid, afterDocId = lastId)
                    else -> emptyList()
                }
                val existingIds = _suggestions.value.map { it.user.id }.toSet()
                val deduped = page.filter { it.user.id !in existingIds }
                _suggestions.value = _suggestions.value + deduped
                _hasMore.value = page.size >= pageSize
            } catch (_: Exception) {
                _hasMore.value = false
            }
            _isLoadingMore.value = false
        }
    }

    private suspend fun loadMutualConnections(uid: String): List<SuggestedUserMatch> {
        var mutuals = firestoreDataSource.fetchPrecomputedMutualConnections(uid, limit = 50)
        if (mutuals.isEmpty()) {
            val followingIds = firestoreDataSource.fetchFollowingIds(uid)
            val excludeIds = followingIds + uid
            mutuals = firestoreDataSource.fetchFriendsOfFriends(uid, excludeIds, limit = 50)
        }
        return mutuals
            .sortedByDescending { it.mutualCount }
            .map { mc ->
                SuggestedUserMatch(
                    user = mc.user,
                    matchData = null,
                    suggestionReason = SuggestionReason(
                        mutualNames = mc.mutualUsernames,
                        mutualCount = mc.mutualCount,
                    ),
                )
            }
    }

    private suspend fun loadPopularUsersPage(uid: String, afterDocId: String?): List<SuggestedUserMatch> {
        val users = userRepository.fetchPopularUsersPaginated(
            limit = pageSize,
            excludeIds = setOf(uid),
            afterDocId = afterDocId,
        )
        return matchesWithPostPreviews(users, viewerId = uid)
    }

    /** Fetches up to 4 recent posts per user in parallel and synthesizes the
     *  [MusicMatchData] previews that drives [TasteMatchCard]'s 2x2 album-art
     *  grid. Mirrors `PopularUsersRailViewModel.matchesWithPreviews` so the
     *  See-All grid renders the same card UI as the inline rail. */
    private suspend fun matchesWithPostPreviews(
        users: List<CymbalUser>,
        viewerId: String,
    ): List<SuggestedUserMatch> = coroutineScope {
        users.map { user ->
            async {
                val posts = runCatching {
                    postRepository.getProfilePosts(user.id, viewerId, limit = 4)
                }.getOrDefault(emptyList())

                // Prefer the high-res field — the 2x2 grid tiles are big
                // enough on phones that the thumbnail-sized URL renders blurry.
                val trackPreviews = posts.filter { it.isTrack }.map { post ->
                    SharedTrackPreview(
                        trackId = post.track.id,
                        trackName = post.track.name,
                        artistName = post.track.artistName,
                        albumArtURL = post.track.albumArtLargeURL ?: post.track.albumArtURL,
                        posterURL = null,
                        isMovie = false,
                    )
                }
                val moviePreviews = posts.filter { it.isMovie }.map { post ->
                    SharedMoviePreview(
                        movieId = post.movieId.orEmpty(),
                        movieTitle = post.movieTitle.orEmpty(),
                        directorName = post.directorName.orEmpty(),
                        posterURL = post.posterLargeURL ?: post.posterURL,
                    )
                }
                SuggestedUserMatch(
                    user = user,
                    matchData = MusicMatchData(
                        sharedTrackPreviews = trackPreviews,
                        sharedMoviePreviews = moviePreviews,
                    ),
                )
            }
        }.awaitAll()
    }

    private suspend fun loadNewUsersPage(uid: String, afterDocId: String?): List<SuggestedUserMatch> {
        val users = userRepository.fetchNewUsersPaginated(
            limit = pageSize,
            excludeIds = setOf(uid),
            afterDocId = afterDocId,
        )
        return users.map { SuggestedUserMatch(user = it, matchData = null, suggestionReason = null) }
    }

    fun isFollowed(userId: String): Boolean {
        return _localFollowedIds.value.contains(userId) || _followingIds.value.contains(userId)
    }

    fun toggleFollow(user: CymbalUser) {
        val uid = authRepository.currentUserId ?: return
        val isCurrentlyFollowed = isFollowed(user.id)
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
}
