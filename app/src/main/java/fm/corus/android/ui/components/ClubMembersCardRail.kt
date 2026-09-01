package fm.corus.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import fm.corus.android.ui.theme.CorusMotion
import kotlinx.coroutines.delay
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
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.horizontalRailCardWidth
import fm.corus.android.ui.util.deferToInnerHorizontalScroll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/**
 * Horizontally-scrolling rail of Corus Club members, ordered by initial
 * sign-up date (most recent first). Renders the same 2x2 album-art
 * [TasteMatchCard] as the Popular and Mutual Connections rails.
 *
 * Club members come from `users_v2` without track previews, so we enrich
 * them client-side from each user's recent posts. Cards still being enriched
 * render as [SkeletonTasteMatchCard].
 */
@Composable
fun ClubMembersCardRail(
    users: List<CymbalUser>,
    // Pass the followed-id set (not a lambda) so this rail recomposes when
    // the viewer follows/unfollows someone — a captured-viewModel lambda is
    // treated as stable and would let Compose skip the rail, leaving the
    // Follow buttons visually stuck on the old state.
    followedIds: Set<String>,
    onUserTap: (CymbalUser) -> Unit,
    onFollowTap: (CymbalUser) -> Unit,
    memberSinceLabel: (Date) -> String,
    modifier: Modifier = Modifier,
    preserveOrder: Boolean = false,
    subtitleForUser: ((CymbalUser) -> String)? = null,
    preferDisplayName: Boolean = false,
    viewModelKey: String = "clubMembers",
    viewModel: ClubMembersCardRailViewModel = hiltViewModel(key = viewModelKey),
) {
    val enriched by viewModel.enriched.collectAsState()
    val isActive = LocalContainingTabSelected.current

    LaunchedEffect(isActive, users.map { it.id }) {
        if (!isActive || users.isEmpty()) return@LaunchedEffect
        delay(CorusMotion.SEARCH_LIVE_LOAD_DELAY_MS)
        viewModel.enrichAll(users)
    }

    // Bias the rail toward unfollowed members so high-follow viewers still
    // see new sign-ups they don't yet follow at the front. Each subgroup
    // keeps the server's `clubMemberSince desc` order. Mirrors iOS
    // `HorizontalClubMembersRail.displayMatches`.
    val orderedUsers = if (preserveOrder) {
        users.take(VISIBLE_CAP)
    } else {
        val unfollowed = users.filter { it.id !in followedIds }
        val followed = users.filter { it.id in followedIds }
        (unfollowed + followed).take(VISIBLE_CAP)
    }

    val cardWidth = horizontalRailCardWidth()
    LazyRow(
        modifier = modifier.fillMaxWidth().deferToInnerHorizontalScroll(),
        contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        items(orderedUsers, key = { it.id }) { user ->
            val enrichedMatch = enriched[user.id]
            AnimatedContent(
                targetState = enrichedMatch != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "clubMemberCardEnrich",
            ) { ready ->
                if (ready && enrichedMatch != null) {
                    TasteMatchCard(
                        match = enrichedMatch,
                        isFollowing = user.id in followedIds,
                        onUserTap = { onUserTap(user) },
                        onFollowTap = { onFollowTap(user) },
                        subtitle = subtitleForUser?.invoke(user)
                            ?: user.clubMemberSince?.let(memberSinceLabel).orEmpty(),
                        // Display name (or member-since fallback) is one line —
                        // don't reserve a permanently-empty second line. Matches
                        // iOS (subtitleReservesTwoLines: false) and the Popular /
                        // Mutual Connections rails, which also pass subtitleLines = 1.
                        subtitleLines = 1,
                        preferDisplayName = preferDisplayName,
                        modifier = Modifier.width(cardWidth),
                    )
                } else {
                    SkeletonTasteMatchCard(modifier = Modifier.width(cardWidth))
                }
            }
        }
    }
}

/** Visible cap in the rail. The fetch pool is bigger so the
 *  unfollowed-first reorder still has unfollowed members to pull from when
 *  the viewer follows most of the active club. */
private const val VISIBLE_CAP = 12

@HiltViewModel
class ClubMembersCardRailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _enriched = MutableStateFlow<Map<String, SuggestedUserMatch>>(emptyMap())
    val enriched: StateFlow<Map<String, SuggestedUserMatch>> = _enriched.asStateFlow()

    private val inFlightIds = mutableSetOf<String>()

    /** Fetches recent posts for every user not yet enriched and synthesizes
     *  the [MusicMatchData] previews that drive [TasteMatchCard]'s 2x2 grid. */
    fun enrichAll(users: List<CymbalUser>) {
        val viewerId = authRepository.currentUserId ?: return
        val toFetch = users.filter { it.id !in _enriched.value && it.id !in inFlightIds }
        if (toFetch.isEmpty()) return
        toFetch.forEach { inFlightIds += it.id }

        viewModelScope.launch {
            val results = coroutineScope {
                toFetch.map { user ->
                    async {
                        // Bounded retry so a transient cold-start callable
                        // failure doesn't cache an empty grid until the app is
                        // relaunched — see fetchListWithRetry.
                        val posts = fetchListWithRetry {
                            postRepository.getProfilePosts(user.id, viewerId, limit = 12)
                        }

                        // Prefer the high-res field — the 2x2 grid tiles are big
                        // enough on phones that the thumbnail-sized URL renders blurry.
                        val trackPreviews = posts.filter { it.isTrack }.map {
                            SharedTrackPreview(
                                trackId = it.track.id,
                                trackName = it.track.name,
                                artistName = it.track.artistName,
                                albumArtURL = it.track.albumArtLargeURL ?: it.track.albumArtURL,
                                posterURL = null,
                                isMovie = false,
                            )
                        }
                        val moviePreviews = posts.filter { it.isMovie }.map {
                            SharedMoviePreview(
                                movieId = it.movieId.orEmpty(),
                                movieTitle = it.movieTitle.orEmpty(),
                                directorName = it.directorName.orEmpty(),
                                posterURL = it.posterLargeURL ?: it.posterURL,
                            )
                        }
                        SuggestedUserMatch(
                            user = user,
                            matchData = MusicMatchData(
                                sharedTrackPreviews = trackPreviews,
                                sharedMoviePreviews = moviePreviews,
                            ),
                            suggestionReason = null,
                        )
                    }
                }.awaitAll()
            }
            _enriched.value = _enriched.value + results.associateBy { it.user.id }
            results.forEach { inFlightIds -= it.user.id }
        }
    }
}
