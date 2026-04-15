package fm.corus.android.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tag
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagFeedScreen(
    hashtag: String,
    viewModel: HashtagFeedViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
) {
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(hashtag) {
        viewModel.loadHashtagPosts(hashtag)
    }

    // Prefetch when near end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= posts.size - 6 && hasMore && !isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadHashtagPosts(hashtag)
    }

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
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Hashtag header
            item(span = { GridItemSpan(3) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CorusSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "#$hashtag",
                        style = CorusFont.songTitleLarge,
                        color = CorusColors.Text,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Text(
                        text = "${formatCount(posts.size)} coruses",
                        style = CorusFont.artistNameLarge,
                        color = CorusColors.Secondary,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }
            }

            if (loadError != null && posts.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CorusSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Couldn't load posts",
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        OutlinedButton(
                            onClick = { viewModel.retry() },
                            border = BorderStroke(1.dp, CorusColors.Accent),
                        ) {
                            Text(
                                text = "Try Again",
                                color = CorusColors.Accent,
                            )
                        }
                    }
                }
            } else if (isLoading && posts.isEmpty()) {
                // Skeleton grid: 9 placeholders
                items(9) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(CorusSpacing.xxs)
                            .background(CorusColors.CardBackground),
                    )
                }
            } else if (posts.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tag,
                            contentDescription = null,
                            tint = CorusColors.Tertiary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        Text(
                            text = "No posts for this hashtag yet",
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                        )
                    }
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    AsyncImage(
                        model = post.displayImageURL,
                        contentDescription = post.displayTitle,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(CorusSpacing.xxs)
                            .clickable { onNavigateToPost(post.id) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}
