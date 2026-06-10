package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonFilmDetailHeader
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.FirstPosterBadge
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmDetailScreen(
    movieId: String,
    initialMovieTitle: String? = null,
    initialDirectorName: String? = null,
    initialReleaseYear: String? = null,
    initialPosterURL: String? = null,
    initialPosterLargeURL: String? = null,
    initialTrailerURL: String? = null,
    viewModel: FilmDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToCompose: (String) -> Unit = {},
) {
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val movieHeader by viewModel.movieHeader.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(movieId) {
        if (initialMovieTitle != null) {
            viewModel.setInitialMovieInfo(
                MovieHeaderInfo(
                    movieTitle = initialMovieTitle,
                    directorName = initialDirectorName,
                    releaseYear = initialReleaseYear,
                    posterURL = initialPosterURL,
                    posterLargeURL = initialPosterLargeURL,
                    trailerURL = initialTrailerURL,
                )
            )
        }
        viewModel.loadMoviePosts(movieId)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = CorusSpacing.xxxl + CorusSpacing.xxxl),
        ) {
            // Film header
            item {
                val header = movieHeader
                if (header == null && isLoading) {
                    SkeletonFilmDetailHeader()
                    HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
                    return@item
                }

                Spacer(modifier = Modifier.height(CorusSpacing.xl))

                if (header != null) {
                    // Movie poster
                    AsyncImage(
                        model = header.posterLargeURL ?: header.posterURL,
                        contentDescription = header.movieTitle,
                        modifier = Modifier
                            .width(220.dp)
                            .aspectRatio(2f / 3f)
                            .shadow(8.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )

                    Spacer(modifier = Modifier.height(CorusSpacing.md))

                    // Title + year
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = header.movieTitle ?: "",
                            style = CorusFont.songTitleLarge,
                            color = CorusColors.Text,
                            textAlign = TextAlign.Center,
                        )
                        if (!header.releaseYear.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.film_detail_year_format, header.releaseYear),
                                style = CorusFont.artistName,
                                color = CorusColors.Tertiary,
                            )
                        }
                    }

                    // Director
                    if (!header.directorName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                        Text(
                            text = header.directorName,
                            style = CorusFont.artistName,
                            color = CorusColors.Secondary,
                        )
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.md))

                    // Action buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        ) {
                            // Post Film capsule
                            Button(
                                onClick = { onNavigateToCompose(movieId) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CorusColors.Accent,
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                                Text(stringResource(R.string.film_detail_post_film), style = CorusFont.buttonSmall)
                            }

                            // Watch Trailer capsule
                            if (!header.trailerURL.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        viewModel.nowPlayingManager.pause()
                                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(header.trailerURL))) } catch (_: Exception) { }
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CorusColors.Accent,
                                        contentColor = Color.White,
                                    ),
                                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                                    Text(stringResource(R.string.film_detail_watch_trailer), style = CorusFont.buttonSmall)
                                }
                            }
                        }

                        // Where to Watch outline capsule
                        if (!header.movieTitle.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val url = "https://www.justwatch.com/us/search?q=${Uri.encode(header.movieTitle)}"
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                                },
                                shape = RoundedCornerShape(50),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(CorusColors.Accent),
                                ),
                                contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                            ) {
                                Icon(
                                    Icons.Outlined.Tv,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = CorusColors.Accent,
                                )
                                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                                Text(stringResource(R.string.film_detail_where_to_watch), style = CorusFont.buttonSmall, color = CorusColors.Accent)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }

                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            }

            // Posted by section
            if (isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.film_detail_posted_by),
                        style = CorusFont.sectionHeader,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .padding(top = CorusSpacing.lg, bottom = CorusSpacing.md),
                    )
                }
                items(6) { index ->
                    SkeletonUserRow()
                    if (index < 5) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }
                }
            } else if (loadError != null) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.film_detail_load_error), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        TextButton(onClick = { viewModel.loadMoviePosts(movieId) }) {
                            Text(stringResource(R.string.film_detail_try_again), style = CorusFont.buttonSmall, color = CorusColors.Accent)
                        }
                    }
                }
            } else if (posts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.film_detail_empty), style = CorusFont.body, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        Button(
                            onClick = { onNavigateToCompose(movieId) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CorusColors.Accent,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        ) {
                            Text(stringResource(R.string.film_detail_be_the_first), style = CorusFont.buttonSmall)
                        }
                    }
                }
            } else {
                item {
                    val count = uniquePosterCount ?: posts.map { it.user.id }.toSet().size
                    Text(
                        text = pluralStringResource(R.plurals.film_detail_posted_by_count, count, formatFilmUserCount(count)),
                        style = CorusFont.sectionHeader,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .padding(top = CorusSpacing.lg, bottom = CorusSpacing.md),
                    )
                }

                items(posts, key = { it.id }) { post ->
                    FilmPostedByRow(
                        post = post,
                        onUserTap = { onNavigateToUser(post.user.id) },
                    )
                    if (post.id != posts.lastOrNull()?.id) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }

                    if (post.id == posts.lastOrNull()?.id && hasMore && !isLoadingMore) {
                        LaunchedEffect(post.id) { viewModel.loadMore() }
                    }
                }

                if (isLoadingMore) {
                    item {
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

@Composable
internal fun FilmPostedByRow(
    post: CymbalPost,
    onUserTap: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUserTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = post.user.avatarURL, displayName = post.user.displayName, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = post.user.displayName,
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (post.isFirstPoster) {
                    Spacer(modifier = Modifier.width(6.dp))
                    FirstPosterBadge()
                }
            }
            UsernameWithFlair(
                username = post.user.username,
                isVerified = post.user.isVerified,
                isClubMember = post.user.isClubMember,
                flairStyle = post.user.flairStyle,
                isBot = post.user.isBot,
                showAtPrefix = true,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
            )
        }
        Text(
            text = DateUtils.relativeTime(LocalContext.current, post.timestamp),
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
        )
    }
}

private fun formatFilmUserCount(count: Int): String {
    return when {
        count < 1000 -> "$count"
        count < 1_000_000 -> String.format("%.2fK", count / 1000.0)
        else -> String.format("%.2fM", count / 1_000_000.0)
    }
}
