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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmDetailScreen(
    movieId: String,
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
    val context = LocalContext.current

    LaunchedEffect(movieId) {
        viewModel.loadMoviePosts(movieId)
    }

    val movieInfo = posts.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Film header
            item {
                Spacer(modifier = Modifier.height(CorusSpacing.xl))

                if (movieInfo != null) {
                    // Movie poster
                    AsyncImage(
                        model = movieInfo.posterLargeURL ?: movieInfo.posterURL,
                        contentDescription = movieInfo.movieTitle,
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
                            text = movieInfo.movieTitle ?: "",
                            style = CorusFont.songTitleLarge,
                            color = CorusColors.Text,
                            textAlign = TextAlign.Center,
                        )
                        if (!movieInfo.releaseYear.isNullOrBlank()) {
                            Text(
                                text = "(${movieInfo.releaseYear})",
                                style = CorusFont.artistNameLarge,
                                color = CorusColors.Tertiary,
                            )
                        }
                    }

                    // Director
                    if (!movieInfo.directorName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                        Text(
                            text = movieInfo.directorName,
                            style = CorusFont.artistNameLarge,
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
                            // Trailer capsule
                            if (!movieInfo.trailerURL.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(movieInfo.trailerURL))) } catch (_: Exception) { }
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CorusColors.Text,
                                        contentColor = Color.White,
                                    ),
                                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                                ) {
                                    Text("Trailer", style = CorusFont.buttonSmall)
                                }
                            }

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
                                Text("Post Film", style = CorusFont.buttonSmall)
                            }
                        }

                        // Where to Watch outline capsule
                        if (!movieInfo.movieTitle.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val url = "https://www.justwatch.com/us/search?q=${Uri.encode(movieInfo.movieTitle)}"
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                                },
                                shape = RoundedCornerShape(50),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(CorusColors.Accent),
                                ),
                                contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                            ) {
                                Text("Where to Watch", style = CorusFont.buttonSmall, color = CorusColors.Accent)
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
                        text = "Posted by",
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
                        Text("Couldn't load posts", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        TextButton(onClick = { viewModel.loadMoviePosts(movieId) }) {
                            Text("Try Again", style = CorusFont.buttonSmall, color = CorusColors.Accent)
                        }
                    }
                }
            } else if (posts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No one has posted this film yet", style = CorusFont.body, color = CorusColors.Secondary)
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
                            Text("Be the first!", style = CorusFont.buttonSmall)
                        }
                    }
                }
            } else {
                item {
                    val count = uniquePosterCount ?: posts.map { it.user.id }.toSet().size
                    Text(
                        text = "Posted by ${formatFilmUserCount(count)} user${if (count != 1) "s" else ""}",
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
private fun FilmPostedByRow(
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
        UserAvatarView(avatarURL = post.user.avatarURL, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.user.displayName,
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${post.user.username}",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
                if (post.user.isVerified) {
                    Spacer(modifier = Modifier.width(CorusSpacing.xs))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Verified",
                        tint = CorusColors.Verified,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (post.isFirstPoster) {
                    Spacer(modifier = Modifier.width(CorusSpacing.xs))
                    Text(
                        text = "FIRST",
                        style = CorusFont.captionMedium,
                        color = CorusColors.Accent,
                        modifier = Modifier
                            .background(CorusColors.Accent.copy(alpha = 0.1f), RoundedCornerShape(CorusSpacing.pillCornerRadius))
                            .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.xxs),
                    )
                }
            }
        }
        Text(
            text = DateUtils.relativeTime(post.timestamp),
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
