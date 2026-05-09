package fm.corus.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import fm.corus.android.ui.theme.horizontalRailCardWidth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Horizontally-scrolling, paginated rail of popular real users.
 *
 * Shown in place of the old Curated Music Bots / Film Bots / Popular sections
 * on the search-root, social-setup onboarding, and feed empty-state screens.
 * The card UI ([TasteMatchCard]) is identical to the one bots used; previews
 * are populated by fetching each user's last few posts.
 *
 * Bots are still queryable via search results — they're filtered out of this
 * rail by [UserRepository.fetchPopularUsersPaginated], not blocked anywhere
 * else.
 */
@Composable
fun HorizontalPopularUsersRail(
    excludeIds: Set<String>,
    // Pass the followed-id set (not a lambda) so this rail recomposes when
    // the viewer follows/unfollows someone — see ClubMembersCardRail for the
    // skippable-composable rationale.
    followedIds: Set<String>,
    onUserTap: (CymbalUser) -> Unit,
    onFollowTap: (CymbalUser) -> Unit,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    viewModel: PopularUsersRailViewModel = hiltViewModel(),
) {
    val matches by viewModel.matches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val endReached by viewModel.endReached.collectAsState()

    LaunchedEffect(excludeIds) {
        viewModel.loadInitial(excludeIds)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState, endReached, isLoading) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (!endReached && !isLoading && total > 0 && lastVisible >= total - 3) {
                    viewModel.loadMore(excludeIds)
                }
            }
    }

    val cardWidth = horizontalRailCardWidth()

    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Header(onSeeAll = onSeeAll)

        if (matches.isEmpty() && isLoading) {
            SkeletonRow(cardWidth = cardWidth)
        } else if (matches.isNotEmpty()) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                items(matches, key = { it.user.id }) { match ->
                    TasteMatchCard(
                        match = match,
                        isFollowing = match.user.id in followedIds,
                        onUserTap = { onUserTap(match.user) },
                        onFollowTap = { onFollowTap(match.user) },
                        subtitle = followerCountSubtitle(match.user.followerCount),
                        modifier = Modifier.width(cardWidth),
                    )
                }
                if (isLoading) {
                    item("loading") {
                        Box(
                            modifier = Modifier.size(width = 48.dp, height = cardWidth),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = CorusColors.Accent,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "1.2K followers" / "342 followers" / "1 follower". Matches iOS rail. */
internal fun followerCountSubtitle(count: Int): String {
    val formatted = when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
    return "$formatted ${if (count == 1) "follower" else "followers"}"
}

@Composable
private fun Header(onSeeAll: (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
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
        Spacer(modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text(
                    stringResource(fm.corus.android.R.string.search_see_all),
                    style = CorusFont.captionMedium,
                    color = CorusColors.Accent,
                )
            }
        }
    }
}

@Composable
private fun SkeletonRow(cardWidth: androidx.compose.ui.unit.Dp) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        items(4) {
            SkeletonTasteMatchCard(modifier = Modifier.width(cardWidth))
        }
    }
}

// ── ViewModel ──

@HiltViewModel
class PopularUsersRailViewModel @Inject constructor(
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

    /** Public for test injection: reset the rail (e.g. after sign-out). */
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

            // Advance cursor before any awaits below so a concurrent loadMore
            // (already gated on isLoading) won't replay this page.
            afterDocId = users.lastOrNull()?.id ?: afterDocId

            val newMatches = matchesWithPreviews(users)
            val appended = newMatches.filter { seenIds.add(it.user.id) }
            if (appended.isNotEmpty()) {
                _matches.value = _matches.value + appended
            }
        } finally {
            _isLoading.value = false
        }
    }

    /** Fetches up to 4 recent posts per user in parallel and synthesizes the
     *  [MusicMatchData] previews that [TasteMatchCard] uses to render its
     *  2x2 album-art grid. */
    private suspend fun matchesWithPreviews(users: List<CymbalUser>): List<SuggestedUserMatch> {
        val viewerId = authRepository.currentUserId ?: return users.map { SuggestedUserMatch(it) }
        return coroutineScope {
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
    }
}
