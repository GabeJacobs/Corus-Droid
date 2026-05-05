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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import fm.corus.android.R
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
    /** Open the scrollable hashtag feed (mirrors profile-grid → ProfileFeedScreen). */
    onNavigateToHashtagFeed: (postId: String) -> Unit = {},
) {
    val posts by viewModel.posts.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val hasLoadedFollowState by viewModel.hasLoadedFollowState.collectAsState()
    val isTogglingFollow by viewModel.isTogglingFollow.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(hashtag) {
        viewModel.loadHashtagPosts(hashtag)
        viewModel.loadFollowState(hashtag)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.hashtag_feed_cd_back), tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
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
                    if (isLoading && posts.isEmpty() && totalCount == 0) {
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(16.dp)
                                .shimmer()
                                .clip(RoundedCornerShape(4.dp))
                                .background(CorusColors.Skeleton),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.hashtag_feed_count_format, formatCount(totalCount)),
                            style = CorusFont.artistNameLarge,
                            color = CorusColors.Secondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    if (hasLoadedFollowState) {
                        HashtagFollowButton(
                            isFollowing = isFollowing,
                            isEnabled = !isTogglingFollow,
                            onClick = { viewModel.toggleFollow(hashtag) },
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(30.dp)
                                .shimmer()
                                .clip(RoundedCornerShape(CorusSpacing.pillCornerRadius))
                                .background(CorusColors.Skeleton),
                        )
                    }
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
                            text = stringResource(R.string.hashtag_feed_load_error),
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        OutlinedButton(
                            onClick = { viewModel.retry() },
                            border = BorderStroke(1.dp, CorusColors.Accent),
                        ) {
                            Text(
                                text = stringResource(R.string.hashtag_feed_try_again),
                                color = CorusColors.Accent,
                            )
                        }
                    }
                }
            } else if (isLoading && posts.isEmpty()) {
                // Skeleton grid: 9 placeholders with shimmer (matches iOS)
                items(9) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .shimmer()
                            .background(CorusColors.Skeleton),
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
                            text = stringResource(R.string.hashtag_feed_empty),
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                        )
                    }
                }
            } else {
                // distinctBy guards the LazyGrid against duplicate ids
                // (paginated server data can occasionally overlap) —
                // duplicates would crash SubcomposeLayout with
                // "Key … was already used".
                val gridPosts = posts.distinctBy { it.id }
                items(gridPosts, key = { it.id }) { post ->
                    AsyncImage(
                        model = post.displayImageLargeURL ?: post.displayImageURL,
                        contentDescription = post.displayTitle,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable {
                                // Mirror profile-grid behavior: seed
                                // ProfileFeedCache then navigate to the
                                // scrollable feed.
                                fm.corus.android.ui.screens.profile.ProfileFeedCache.posts = posts
                                fm.corus.android.ui.screens.profile.ProfileFeedCache.hasMore = hasMore
                                fm.corus.android.ui.screens.profile.ProfileFeedCache.profileUser = null
                                onNavigateToHashtagFeed(post.id)
                            },
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

@Composable
private fun HashtagFollowButton(
    isFollowing: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val label = if (isFollowing) {
        stringResource(R.string.hashtag_feed_following)
    } else {
        stringResource(R.string.hashtag_feed_follow)
    }
    Button(
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFollowing) CorusColors.CardBackground else CorusColors.Accent,
            contentColor = if (isFollowing) CorusColors.Secondary else Color.White,
        ),
        border = if (isFollowing) BorderStroke(1.dp, CorusColors.Divider) else null,
        contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
        modifier = Modifier.height(30.dp),
    ) {
        Text(text = label, style = CorusFont.buttonSmall)
    }
}
