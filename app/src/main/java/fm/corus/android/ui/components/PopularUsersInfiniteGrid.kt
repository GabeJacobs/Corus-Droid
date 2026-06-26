package fm.corus.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MusicMatchData
import fm.corus.android.data.model.SharedMoviePreview
import fm.corus.android.data.model.SharedTrackPreview
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.repository.AuthRepository
import fm.corus.android.data.repository.PostRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Vertically-scrolling, paginated 2-column grid of popular real users.
 *
 * Mirrors the iOS `PopularUsersInfiniteGrid` used in onboarding and the
 * empty-feed state: the entire screen scrolls as a single surface, the grid
 * hands paging off to the user's scroll, and skeleton cards fill the trailing
 * row while another page is in flight.
 *
 * Pass non-grid content (e.g. the empty-feed "invite friends" section) via
 * [topContent] — it's rendered as a full-span row above the section header so
 * the whole page lives inside one scrollable.
 */
@Composable
fun PopularUsersInfiniteGrid(
    excludeIds: Set<String>,
    // Pass the followed-id set (not a lambda) so this grid recomposes when
    // the viewer follows/unfollows someone — see ClubMembersCardRail for the
    // skippable-composable rationale.
    followedIds: Set<String>,
    onUserTap: (CymbalUser) -> Unit,
    onFollowTap: (CymbalUser) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    // Vertical padding around the "POPULAR ON …" header. Onboarding passes 0 so
    // the header spacing is driven entirely by the surrounding layout (matching
    // iOS); the empty-feed state keeps the default breathing room.
    headerVerticalPadding: Dp = CorusSpacing.sm,
    topContent: (@Composable () -> Unit)? = null,
    viewModel: PopularUsersInfiniteGridViewModel = hiltViewModel(),
) {
    val matches by viewModel.matches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val endReached by viewModel.endReached.collectAsState()

    LaunchedEffect(excludeIds) {
        viewModel.loadInitial(excludeIds)
    }

    LaunchedEffect(state, endReached, isLoading) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (!endReached && !isLoading && total > 0 && lastVisible >= total - 4) {
                    viewModel.loadMore(excludeIds)
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = CorusSpacing.lg,
            end = CorusSpacing.lg,
            bottom = CorusSpacing.xxl,
        ),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        if (topContent != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "top") {
                topContent()
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            SectionHeader(verticalPadding = headerVerticalPadding)
        }

        if (matches.isEmpty() && isLoading) {
            items(4, key = { "skeleton-initial-$it" }) {
                SkeletonTasteMatchCard()
            }
        } else {
            items(matches, key = { it.user.id }) { match ->
                TasteMatchCard(
                    match = match,
                    isFollowing = match.user.id in followedIds,
                    onUserTap = { onUserTap(match.user) },
                    onFollowTap = { onFollowTap(match.user) },
                    subtitle = followerCountSubtitle(match.user.followerCount),
                    subtitleLines = 1,
                    // Cards stream in before their artwork; shimmer the collage
                    // until this user's previews have loaded.
                    isArtLoading = match.matchData == null,
                )
            }
            if (isLoading && matches.isNotEmpty()) {
                items(2, key = { "skeleton-more-$it" }) {
                    SkeletonTasteMatchCard()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(verticalPadding: Dp = CorusSpacing.sm) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = CorusColors.Accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Text(
            text = "POPULAR ON CORUS",
            style = CorusFont.sectionHeader,
            color = CorusColors.Secondary,
        )
    }
}

// ── ViewModel ──

@HiltViewModel
class PopularUsersInfiniteGridViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _matches = MutableStateFlow<List<SuggestedUserMatch>>(emptyList())
    val matches: StateFlow<List<SuggestedUserMatch>> = _matches.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached.asStateFlow()

    private var afterDocId: String? = null
    private val seenIds = mutableSetOf<String>()
    private var hasLoadedInitial = false
    private val pageSize = 8

    fun loadInitial(excludeIds: Set<String>) {
        if (hasLoadedInitial) return
        hasLoadedInitial = true
        viewModelScope.launch { fetchPage(excludeIds) }
    }

    fun loadMore(excludeIds: Set<String>) {
        if (_isLoading.value || _endReached.value) return
        viewModelScope.launch { fetchPage(excludeIds) }
    }

    /** Public for test injection: reset the grid (e.g. after sign-out). */
    fun reset() {
        _matches.value = emptyList()
        _isLoading.value = false
        _endReached.value = false
        afterDocId = null
        seenIds.clear()
        hasLoadedInitial = false
    }

    private suspend fun fetchPage(excludeIds: Set<String>) {
        _isLoading.value = true
        try {
            val viewerExcludes = excludeIds + seenIds + listOfNotNull(authRepository.currentUserId)
            val users = runCatching {
                userRepository.fetchPopularUsersPaginated(
                    limit = pageSize,
                    excludeIds = viewerExcludes,
                    afterDocId = afterDocId,
                )
            }.getOrDefault(emptyList())

            if (users.isEmpty()) {
                _endReached.value = true
                return
            }

            afterDocId = users.lastOrNull()?.id ?: afterDocId

            // Show the user cards immediately. Avatar, username and follower count
            // all live on CymbalUser, so a card renders fully without its artwork —
            // the 2x2 collage just falls through to placeholder tiles until it loads.
            // Only the collage needs the per-user getProfilePosts cloud call, which
            // can be slow; fetching it inline (awaitAll) used to block the ENTIRE
            // page on the single slowest call, leaving blank skeletons on screen.
            // Instead we append the cards now and patch each one's artwork in as it
            // arrives, so no card waits on another.
            val fresh = users.filter { seenIds.add(it.id) }
            if (fresh.isEmpty()) return
            _matches.update { it + fresh.map { user -> SuggestedUserMatch(user) } }

            fresh.forEach { user ->
                viewModelScope.launch {
                    val match = matchWithPreviews(user)
                    // Atomic patch: concurrent per-user updates can't clobber each other.
                    _matches.update { current ->
                        current.map { if (it.user.id == user.id) match else it }
                    }
                }
            }
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun matchWithPreviews(user: CymbalUser): SuggestedUserMatch {
        val viewerId = authRepository.currentUserId ?: return SuggestedUserMatch(user)
        // Fetch more than the 4 tiles we show: the card dedups tiles by album art,
        // so a user who posted the same song (or album) twice would otherwise leave
        // an empty tile. The extra posts give enough unique artwork to fill the grid.
        val posts = runCatching {
            postRepository.getProfilePosts(user.id, viewerId, limit = 8)
        }.getOrDefault(emptyList())

        // Prefer the high-res field — the 2x2 grid tiles are big enough on phones
        // that the thumbnail-sized URL renders blurry.
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
        return SuggestedUserMatch(
            user = user,
            matchData = MusicMatchData(
                sharedTrackPreviews = trackPreviews,
                sharedMoviePreviews = moviePreviews,
            ),
        )
    }
}
