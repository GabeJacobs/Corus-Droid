package fm.corus.android.ui.screens.destination

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.navigation.FilmDetailRoute
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * "Who shared {name}" — the paginated posts see-all for an artist or a
 * director. Full history (NOT deduped by user) with automatic load-more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationPostsScreen(
    kind: DestinationPostsViewModel.Kind,
    subjectId: String,
    subjectName: String? = null,
    viewModel: DestinationPostsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToFilm: (FilmDetailRoute) -> Unit = {},
) {
    val posts by viewModel.posts.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()

    LaunchedEffect(kind, subjectId) {
        viewModel.load(kind, subjectId, subjectName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    CorusHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.feed_cd_back),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = CorusSpacing.xxxl),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = CorusSpacing.lg)) {
                    Text(
                        text = if (!subjectName.isNullOrBlank()) {
                            stringResource(R.string.destination_who_shared_format, subjectName)
                        } else {
                            stringResource(
                                if (kind == DestinationPostsViewModel.Kind.ARTIST) {
                                    R.string.destination_who_shared_artist
                                } else {
                                    R.string.destination_who_shared_director
                                }
                            )
                        },
                        style = CorusFont.songTitleLarge,
                        color = CorusColors.Text,
                    )
                    if (uniquePosterCount > 0) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                        Text(
                            text = pluralStringResource(
                                R.plurals.destination_shared_by_people,
                                uniquePosterCount,
                                formatDestinationCount(uniquePosterCount),
                            ),
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Secondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }
            }

            if (isLoading) {
                items(8) { index ->
                    SkeletonUserRow()
                    if (index < 7) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }
                }
            } else if (loadError) {
                item {
                    Text(
                        text = stringResource(R.string.destination_posts_load_error),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.lg),
                    )
                }
            } else if (posts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.destination_no_posts_yet),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.lg),
                    )
                }
            } else {
                items(posts.size) { index ->
                    val post = posts[index]
                    DestinationPostRow(
                        post = post,
                        onUserTap = { onNavigateToUser(post.user.id) },
                        onPostTap = { onNavigateToPost(post.id) },
                        onFilmChipTap = if (post.isMovie && post.movieId != null) {
                            {
                                onNavigateToFilm(
                                    FilmDetailRoute(
                                        movieId = post.movieId ?: "",
                                        movieTitle = post.movieTitle,
                                        directorName = post.directorName,
                                        releaseYear = post.releaseYear,
                                        posterURL = post.posterURL,
                                        posterLargeURL = post.posterLargeURL,
                                        trailerURL = post.trailerURL,
                                    )
                                )
                            }
                        } else null,
                    )

                    // Auto-pagination: same trailing-item trigger as SongDetailScreen.
                    if (index == posts.lastIndex && hasMore && !isLoadingMore) {
                        LaunchedEffect(post.id) { viewModel.loadMore() }
                    }
                }

                if (isLoadingMore) {
                    item {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = CorusColors.Accent,
                                modifier = Modifier.padding(CorusSpacing.lg),
                            )
                        }
                    }
                }
            }
        }
    }
}
