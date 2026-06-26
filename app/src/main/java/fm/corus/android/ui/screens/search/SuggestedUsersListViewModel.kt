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
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.SearchSection
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
    private val analyticsService: AnalyticsService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val source: String = savedStateHandle.toRoute<SuggestedUsersListRoute>().source

    /** Map the legacy `source` route param onto the canonical [SearchSection]
     *  used in cross-platform analytics. Returns null for unknown sources so
     *  we never emit garbage. */
    private val analyticsSection: SearchSection? = when (source) {
        "tasteMatches" -> SearchSection.TasteMatches
        "mutualConnections" -> SearchSection.MutualConnections
        "popular" -> SearchSection.Popular
        "clubMembers" -> SearchSection.ClubMembers
        "new" -> SearchSection.NewOnCorus
        else -> null
    }

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
    // Cursor for the live taste-matches pagination (opaque, from the backend).
    private var tmCursor: String? = null
    private val isPaginated: Boolean
        get() = source == "popular" || source == "new" || source == "tasteMatches"

    /** Fetch one page of the live taste-matches list, advancing the cursor and
     *  hasMore from the backend response (not page-size heuristics). */
    private suspend fun loadTasteMatchesPage(reset: Boolean): List<SuggestedUserMatch> {
        if (reset) tmCursor = null
        val page = userRepository.getTasteMatchesPage(cursor = tmCursor, limit = 15)
        tmCursor = page.nextCursor
        _hasMore.value = page.hasMore
        return page.matches
    }

    /** Ids currently being enriched with post previews (clubMembers only).
     *  Mutated only from the main dispatcher via viewModelScope, so no lock. */
    private val inFlightEnrichmentIds = mutableSetOf<String>()

    /** Eager prefetch count for clubMembers — enough to fill the first ~6
     *  rows of the 2-up grid above the fold on common phone sizes. */
    private val clubMembersEagerPrefetch = 12

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
                        "clubMembers" -> loadClubMembers(uid)
                        "tasteMatches" -> loadTasteMatchesPage(reset = true)
                        else -> userRepository.getSuggestedUsers(uid)
                    }
                    _suggestions.value = initial
                    // tasteMatches sets hasMore from the cursor response above.
                    if (source != "tasteMatches") {
                        _hasMore.value = isPaginated && initial.size >= pageSize
                    }
                } catch (_: Exception) { }
                _isLoading.value = false
                if (source == "clubMembers") {
                    ensureClubMembersEnriched(0, clubMembersEagerPrefetch - 1)
                }
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
                    "clubMembers" -> loadClubMembers(uid)
                    "tasteMatches" -> loadTasteMatchesPage(reset = true)
                    // Pull-to-refresh must bypass the 4-hour suggested-matches
                    // cache: serving cached data here returns synchronously (no
                    // network suspension), so isRefreshing flips true→false
                    // within a frame and the PullToRefreshBox indicator freezes
                    // at the pulled position instead of retracting. Forcing a
                    // real fetch also gives the user genuinely fresh matches.
                    else -> userRepository.getSuggestedUsers(uid, forceRefresh = true)
                }
                _suggestions.value = initial
                if (source != "tasteMatches") {
                    _hasMore.value = isPaginated && initial.size >= pageSize
                }
            } catch (_: Exception) { }
            _isRefreshing.value = false
            if (source == "clubMembers") {
                inFlightEnrichmentIds.clear()
                ensureClubMembersEnriched(0, clubMembersEagerPrefetch - 1)
            }
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
                    "tasteMatches" -> loadTasteMatchesPage(reset = false)
                    else -> emptyList()
                }
                val existingIds = _suggestions.value.map { it.user.id }.toSet()
                val deduped = page.filter { it.user.id !in existingIds }
                _suggestions.value = _suggestions.value + deduped
                // tasteMatches sets hasMore from the cursor response inside
                // loadTasteMatchesPage; the size heuristic is for popular/new.
                if (source != "tasteMatches") {
                    _hasMore.value = page.size >= pageSize
                }
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

    /**
     * Full Club Members See-All list. Capped at 100 most-recent sign-ups
     * intentionally — we don't want the full active-member count to be
     * trivially screenshottable.
     *
     * Returns un-enriched matches (matchData = null) so the user list paints
     * immediately. The 2x2 album-art previews are loaded lazily by
     * [ensureClubMembersEnriched]: the screen requests enrichment for the
     * visible window plus a small prefetch margin, and [init] kicks off the
     * first [clubMembersEagerPrefetch] cards as soon as the user list lands.
     *
     * Bias unfollowed members to the top of the grid so brand-new sign-ups
     * the viewer can still follow surface first. We compute "followed" once
     * here against a snapshot of the current following set; without that
     * snapshot, tapping Follow would immediately shuffle the just-followed
     * card to the back of the grid mid-scroll. Each subgroup keeps the
     * server's `clubMemberSince desc` order. Mirrors iOS
     * `ClubMembersListView.orderedMatches`.
     */
    private suspend fun loadClubMembers(uid: String): List<SuggestedUserMatch> {
        val users = userRepository.fetchClubMembers(
            limit = 100,
            excludeIds = setOf(uid),
        )
        val followedSnapshot = _followingIds.value + _localFollowedIds.value
        val unfollowed = users.filter { it.id !in followedSnapshot }
        val followed = users.filter { it.id in followedSnapshot }
        return (unfollowed + followed).map {
            SuggestedUserMatch(user = it, matchData = null, suggestionReason = null)
        }
    }

    /** Lazily enriches the matches in the given visible-index window with the
     *  2x2 album-art previews that drive [TasteMatchCard]. The screen calls
     *  this whenever the LazyVerticalGrid's visible range changes; we add a
     *  small forward prefetch so the next row is ready by the time it scrolls
     *  in. Skips entries already enriched or in-flight, so repeated calls
     *  during scrolling are cheap. */
    fun ensureClubMembersEnriched(visibleStart: Int, visibleEnd: Int) {
        if (source != "clubMembers") return
        val uid = authRepository.currentUserId ?: return
        val list = _suggestions.value
        if (list.isEmpty()) return
        val from = visibleStart.coerceAtLeast(0)
        // Forward prefetch — enrich the next ~6 cards past what's on screen
        // so they don't pop in mid-scroll.
        val to = (visibleEnd + 6).coerceAtMost(list.lastIndex)
        if (from > to) return
        val targets = (from..to).mapNotNull { i ->
            val m = list[i]
            if (m.matchData == null && m.user.id !in inFlightEnrichmentIds) m.user else null
        }
        if (targets.isEmpty()) return
        targets.forEach { inFlightEnrichmentIds += it.id }

        viewModelScope.launch {
            try {
                val enriched = matchesWithPostPreviews(targets, viewerId = uid)
                val byId = enriched.associateBy { it.user.id }
                _suggestions.value = _suggestions.value.map { existing ->
                    byId[existing.user.id] ?: existing
                }
            } catch (_: Exception) { }
            targets.forEach { inFlightEnrichmentIds -= it.id }
        }
    }

    fun isFollowed(userId: String): Boolean {
        return _localFollowedIds.value.contains(userId) || _followingIds.value.contains(userId)
    }

    fun toggleFollow(user: CymbalUser) {
        val uid = authRepository.currentUserId ?: return
        val isCurrentlyFollowed = isFollowed(user.id)
        // Fire the section-aware analytics event before the network call so the
        // event reflects the user's intent regardless of whether the request
        // ultimately persists.
        analyticsSection?.let { section ->
            if (isCurrentlyFollowed) {
                analyticsService.logSearchSectionUserUnfollowed(section, user.id)
            } else {
                analyticsService.logSearchSectionUserFollowed(section, user.id)
            }
        }
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

    /** Fire `search_section_user_tapped` with the section derived from
     *  [source]. Called by [SuggestedUsersListScreen] when the user taps a
     *  card or row in the destination list. */
    fun logUserTapped(userId: String) {
        analyticsSection?.let {
            analyticsService.logSearchSectionUserTapped(it, userId)
        }
    }
}
