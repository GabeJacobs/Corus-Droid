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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Horizontally-scrolling rail of mutual-connection users, rendered with the
 * same [TasteMatchCard] (2x2 album-art) UI as the Popular rail. Mirrors iOS
 * `MutualConnectionsCardGrid`.
 *
 * Mutuals come from the suggestion path without track previews, so we
 * enrich them client-side from each user's recent posts. Cards still being
 * enriched render as [SkeletonTasteMatchCard].
 */
@Composable
fun MutualConnectionsCardRail(
    matches: List<SuggestedUserMatch>,
    isFollowed: (String) -> Boolean,
    onUserTap: (CymbalUser) -> Unit,
    onFollowTap: (CymbalUser) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MutualConnectionsCardRailViewModel = hiltViewModel(),
) {
    val enriched by viewModel.enriched.collectAsState()

    LaunchedEffect(matches.map { it.user.id }) {
        viewModel.enrichAll(matches)
    }

    val cardWidth = horizontalRailCardWidth()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        items(matches, key = { it.user.id }) { match ->
            val enrichedMatch = enriched[match.user.id]
            AnimatedContent(
                targetState = enrichedMatch != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "mutualCardEnrich",
            ) { ready ->
                if (ready && enrichedMatch != null) {
                    TasteMatchCard(
                        match = enrichedMatch,
                        isFollowing = isFollowed(match.user.id),
                        onUserTap = { onUserTap(match.user) },
                        onFollowTap = { onFollowTap(match.user) },
                        subtitle = mutualFollowersSubtitle(match.suggestionReason?.mutualNames.orEmpty()),
                        modifier = Modifier.width(cardWidth),
                    )
                } else {
                    SkeletonTasteMatchCard(modifier = Modifier.width(cardWidth))
                }
            }
        }
    }
}

/** Compact "via @x, @y +N" form sized to fit in the ~170dp card subtitle.
 *  Mirrors iOS MutualConnectionsCardGrid.mutualFollowersSubtitle. */
private fun mutualFollowersSubtitle(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> "via @${names[0]}"
    2 -> "via @${names[0]}, @${names[1]}"
    else -> "via @${names[0]}, @${names[1]} +${names.size - 2}"
}

@HiltViewModel
class MutualConnectionsCardRailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _enriched = MutableStateFlow<Map<String, SuggestedUserMatch>>(emptyMap())
    val enriched: StateFlow<Map<String, SuggestedUserMatch>> = _enriched.asStateFlow()

    private val inFlightIds = mutableSetOf<String>()

    /** Fetches recent posts for every match not yet enriched and synthesizes
     *  the [MusicMatchData] previews [TasteMatchCard]'s 2x2 grid renders. */
    fun enrichAll(matches: List<SuggestedUserMatch>) {
        val viewerId = authRepository.currentUserId ?: return
        val toFetch = matches
            .map { it.user }
            .filter { it.id !in _enriched.value && it.id !in inFlightIds }
        if (toFetch.isEmpty()) return
        toFetch.forEach { inFlightIds += it.id }

        viewModelScope.launch {
            val results = coroutineScope {
                toFetch.map { user ->
                    async {
                        val posts = runCatching {
                            postRepository.getProfilePosts(user.id, viewerId, limit = 12)
                        }.getOrDefault(emptyList())

                        val trackPreviews = posts.filter { it.isTrack }.map {
                            SharedTrackPreview(
                                trackId = it.track.id,
                                trackName = it.track.name,
                                artistName = it.track.artistName,
                                albumArtURL = it.track.albumArtURL,
                                posterURL = null,
                                isMovie = false,
                            )
                        }
                        val moviePreviews = posts.filter { it.isMovie }.map {
                            SharedMoviePreview(
                                movieId = it.movieId.orEmpty(),
                                movieTitle = it.movieTitle.orEmpty(),
                                directorName = it.directorName.orEmpty(),
                                posterURL = it.posterURL,
                            )
                        }
                        // Preserve the original suggestionReason so the
                        // mutual-followers subtitle survives enrichment.
                        val original = matches.first { it.user.id == user.id }
                        SuggestedUserMatch(
                            user = user,
                            matchData = MusicMatchData(
                                sharedTrackPreviews = trackPreviews,
                                sharedMoviePreviews = moviePreviews,
                            ),
                            suggestionReason = original.suggestionReason,
                        )
                    }
                }.awaitAll()
            }
            _enriched.value = _enriched.value + results.associateBy { it.user.id }
            results.forEach { inFlightIds -= it.user.id }
        }
    }
}
