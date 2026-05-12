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
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
    /** Open the followers/contributors lists when a stat is tapped. */
    onNavigateToHashtagFollowers: (tag: String) -> Unit = {},
    onNavigateToHashtagContributors: (tag: String) -> Unit = {},
) {
    val posts by viewModel.posts.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val hasLoadedFollowState by viewModel.hasLoadedFollowState.collectAsState()
    val isTogglingFollow by viewModel.isTogglingFollow.collectAsState()
    val followerCount by viewModel.followerCount.collectAsState()
    val contributorCount by viewModel.contributorCount.collectAsState()
    val recentCount by viewModel.recentCount.collectAsState()
    val hasLoadedAggregates by viewModel.hasLoadedAggregates.collectAsState()
    val hasLoadedPostsPage by viewModel.hasLoadedPostsPage.collectAsState()
    // Single readiness gate: hold the entire header (velocity, stats, facepile,
    // follow button) in its skeleton state until every source has arrived, so
    // they paint together instead of popping in piecewise. The post grid is
    // independent — those tiles can stream in as images load.
    val isHeaderReady = hasLoadedAggregates && hasLoadedPostsPage && hasLoadedFollowState
    val topContributors by viewModel.topContributors.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(hashtag) {
        viewModel.loadHashtagPosts(hashtag)
        viewModel.loadFollowState(hashtag)
        viewModel.loadAggregatesAndContributors(hashtag)
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
            // Hashtag header — title, velocity caption, 3-stat row, facepile,
            // follow button. Mirrors iOS `HashtagFeedView.hashtagHeader`.
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
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    // Reserve the velocity caption's vertical space pre-load
                    // so the grid below doesn't shift when aggregates arrive.
                    if (!isHeaderReady) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(12.dp)
                                .shimmer()
                                .clip(RoundedCornerShape(4.dp))
                                .background(CorusColors.Skeleton),
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                    } else if (recentCount > 0) {
                        val velocityRes = if (recentCount == 1) {
                            R.string.hashtag_velocity_this_week_one
                        } else {
                            R.string.hashtag_velocity_this_week
                        }
                        Text(
                            text = stringResource(velocityRes, recentCount.toString()),
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                    }
                    HashtagStatsRow(
                        postsValue = if (totalCount > 0) totalCount else 0,
                        followerCount = followerCount,
                        contributorCount = contributorCount,
                        isReady = isHeaderReady,
                        onFollowersTap = { onNavigateToHashtagFollowers(hashtag) },
                        onContributorsTap = { onNavigateToHashtagContributors(hashtag) },
                    )
                    if (!isHeaderReady) {
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        HashtagContributorFacepilePlaceholder()
                    } else if (topContributors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        HashtagContributorFacepile(
                            contributors = topContributors,
                            totalCount = maxOf(contributorCount, topContributors.size),
                            onTap = { onNavigateToHashtagContributors(hashtag) },
                        )
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    if (isHeaderReady) {
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
private fun HashtagStatsRow(
    postsValue: Int,
    followerCount: Int,
    contributorCount: Int,
    isReady: Boolean,
    onFollowersTap: () -> Unit,
    onContributorsTap: () -> Unit,
) {
    val postsLabel = stringResource(R.string.post_noun_plural)
    val followersLabel = stringResource(R.string.hashtag_followers)
    val contributorsLabel = stringResource(R.string.hashtag_contributors)
    Row(
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HashtagStatItem(
            value = formatCount(postsValue),
            label = postsLabel,
            isRedacted = !isReady,
            onTap = null,
        )
        HashtagStatItem(
            value = formatCount(followerCount),
            label = followersLabel,
            isRedacted = !isReady,
            onTap = onFollowersTap.takeIf { isReady },
        )
        HashtagStatItem(
            value = formatCount(contributorCount),
            label = contributorsLabel,
            isRedacted = !isReady,
            onTap = onContributorsTap.takeIf { isReady },
        )
    }
}

@Composable
private fun HashtagStatItem(
    value: String,
    label: String,
    isRedacted: Boolean,
    onTap: (() -> Unit)?,
) {
    val column: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            if (isRedacted) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(20.dp)
                        .shimmer()
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.Skeleton),
                )
            } else {
                Text(text = value, style = CorusFont.stat, color = CorusColors.Text)
            }
            Text(text = label, style = CorusFont.statLabel, color = CorusColors.Secondary)
        }
    }
    if (onTap != null) {
        Box(modifier = Modifier.clickable(onClick = onTap)) { column() }
    } else {
        column()
    }
}

@Composable
private fun HashtagContributorFacepile(
    contributors: List<fm.corus.android.data.model.HashtagContributor>,
    totalCount: Int,
    onTap: () -> Unit,
) {
    if (contributors.isEmpty()) return
    val visible = contributors.take(6)
    val avatarSize = 28.dp
    val overlap = 10.dp
    val step = avatarSize - overlap
    val stackWidth = avatarSize + step * (visible.size - 1).coerceAtLeast(0)
    val nameOf = { c: fm.corus.android.data.model.HashtagContributor ->
        c.username.ifEmpty { c.displayName.ifEmpty { "someone" } }
    }
    // Bold names + secondary connectors so the row reads as "these are the
    // contributors" rather than competing visually with the followers stat.
    val caption = buildAnnotatedString {
        withStyle(SpanStyle(color = CorusColors.Secondary)) {
            append("Contributors · ")
        }
        val nameStyle = SpanStyle(color = CorusColors.Text, fontWeight = FontWeight.SemiBold)
        val restStyle = SpanStyle(color = CorusColors.Secondary)
        when {
            visible.size == 1 -> withStyle(nameStyle) { append(nameOf(visible[0])) }
            totalCount <= 2 -> {
                withStyle(nameStyle) { append(nameOf(visible[0])) }
                withStyle(restStyle) { append(" and ") }
                withStyle(nameStyle) { append(nameOf(visible[1])) }
            }
            else -> {
                val others = (totalCount - 2).coerceAtLeast(0)
                withStyle(nameStyle) { append(nameOf(visible[0])) }
                withStyle(restStyle) { append(", ") }
                withStyle(nameStyle) { append(nameOf(visible[1])) }
                withStyle(restStyle) { append(" & $others others") }
            }
        }
    }
    Row(
        modifier = Modifier
            .clickable(onClick = onTap)
            .padding(horizontal = CorusSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        Box(modifier = Modifier.width(stackWidth).height(avatarSize)) {
            visible.forEachIndexed { i, c ->
                val xOffset = step * i
                Box(
                    modifier = Modifier
                        .offset(x = xOffset)
                        .size(avatarSize)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(CorusColors.CardBackground),
                ) {
                    if (!c.photoURL.isNullOrEmpty()) {
                        AsyncImage(
                            model = c.photoURL,
                            contentDescription = c.displayName.ifEmpty { c.username },
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        val initial = (c.displayName.ifEmpty { c.username }).firstOrNull()?.toString().orEmpty()
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = initial.uppercase(), style = CorusFont.bodyMedium, color = CorusColors.Tertiary)
                        }
                    }
                }
            }
        }
        Text(text = caption, style = CorusFont.body, maxLines = 2)
    }
}

/** Skeleton stand-in for the facepile while aggregates load. Reserves the
 *  same height as the real component so the post grid below doesn't shift
 *  when data arrives. */
@Composable
private fun HashtagContributorFacepilePlaceholder() {
    val avatarSize = 28.dp
    val overlap = 10.dp
    val step = avatarSize - overlap
    val visibleCount = 5
    val stackWidth = avatarSize + step * (visibleCount - 1).coerceAtLeast(0)
    Row(
        modifier = Modifier.padding(horizontal = CorusSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        Box(modifier = Modifier.width(stackWidth).height(avatarSize)) {
            for (i in 0 until visibleCount) {
                val xOffset = step * i
                Box(
                    modifier = Modifier
                        .offset(x = xOffset)
                        .size(avatarSize)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(CorusColors.Divider),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(12.dp)
                .shimmer()
                .clip(RoundedCornerShape(4.dp))
                .background(CorusColors.Skeleton),
        )
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
