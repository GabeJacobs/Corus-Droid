package fm.corus.android.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonTasteMatchCard
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedUsersListScreen(
    matches: List<SuggestedUserMatch>,
    title: String? = null,
    useRowLayout: Boolean = false,
    source: String = "tasteMatches",
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    // Taste-matches All/Unfollowed filter. Only wired for the tasteMatches
    // source; other sources leave these at their defaults so the toggle is
    // hidden and the followed-all empty state never shows.
    showFilterToggle: Boolean = false,
    filterUnfollowed: Boolean = false,
    onSetFilterUnfollowed: (Boolean) -> Unit = {},
    // True while auto-paging to surface unfollowed matches — show a skeleton
    // instead of the followed-all empty state so it doesn't flash mid-fill.
    isFilling: Boolean = false,
    // Pass the followed-id set (not a lambda) so this screen recomposes when
    // the viewer follows/unfollows someone — a captured-viewModel lambda is
    // stable and would cause the Follow buttons to visually freeze.
    followedIds: Set<String> = emptySet(),
    onFollow: (CymbalUser) -> Unit = {},
    /** Fired before [onNavigateToUser] so the analytics event captures *which*
     *  see-all section the tap came from. The default no-op makes this a
     *  pure UI screen — analytics wiring lives at the navigation-graph layer. */
    onUserTapped: (String) -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    /** Visible-range hook used by clubMembers to lazily enrich the 2x2
     *  album-art previews. Other sources ignore this. */
    onVisibleRangeChange: (Int, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    val resolvedTitle = title ?: stringResource(fm.corus.android.R.string.suggested_users_default_title)
    val context = LocalContext.current
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(resolvedTitle, style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    CorusHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(fm.corus.android.R.string.common_back),
                    )
                },
                actions = {
                    if (showFilterToggle) {
                        // The M3 TopAppBar actions slot ends 4.dp from the screen
                        // edge, and the 16.dp icon centers inside its 24.dp
                        // IconButton (4.dp inset), leaving the icon ~8.dp from the
                        // edge. The card grid pads 16.dp (CorusSpacing.lg), so add
                        // 8.dp (CorusSpacing.sm) to line the icon's edge up with
                        // the right column instead of overhanging it.
                        UnfollowedUsersFilterMenu(
                            filterUnfollowed = filterUnfollowed,
                            onSetFilterUnfollowed = onSetFilterUnfollowed,
                            contentDescription = stringResource(fm.corus.android.R.string.search_cd_filter_taste_matches),
                            modifier = Modifier.padding(end = CorusSpacing.sm),
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
            )
        },
    ) { padding ->
      PullToRefreshBox(
          isRefreshing = isRefreshing,
          onRefresh = onRefresh,
          modifier = Modifier.fillMaxSize().padding(padding),
      ) {
        if (isLoading) {
            if (useRowLayout) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(12) { SkeletonUserRow() }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = CorusSpacing.lg, end = CorusSpacing.lg, bottom = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    repeat(12) { item { SkeletonTasteMatchCard() } }
                }
            }
        } else if (isFilling) {
            // Auto-paging to surface unfollowed matches: show a skeleton grid
            // rather than flashing the followed-all empty state for a frame.
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = CorusSpacing.lg, end = CorusSpacing.lg, bottom = CorusSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                repeat(12) { item { SkeletonTasteMatchCard() } }
            }
        } else if (matches.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (filterUnfollowed) {
                    // Filtered to unfollowed, but you follow all of your matches.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                    ) {
                        Text(
                            // No em dashes in user-facing copy (repo rule).
                            text = stringResource(fm.corus.android.R.string.suggested_users_followed_all_taste_matches),
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Secondary,
                        )
                        Text(
                            text = stringResource(fm.corus.android.R.string.suggested_users_show_all),
                            style = CorusFont.captionMedium,
                            color = CorusColors.Accent,
                            modifier = Modifier.clickable { onSetFilterUnfollowed(false) },
                        )
                    }
                } else {
                    Text(stringResource(fm.corus.android.R.string.suggested_users_empty), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                }
            }
        } else if (useRowLayout) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(matches, key = { _, m -> m.id }) { index, match ->
                    SuggestedUserRow(
                        user = match.user,
                        subtitle = subtitleForRow(context, match, source),
                        isFollowed = match.user.id in followedIds,
                        onTap = {
                            onUserTapped(match.user.id)
                            onNavigateToUser(match.user.id)
                        },
                        onFollow = { onFollow(match.user) },
                    )
                    if (index == matches.lastIndex && hasMore && !isLoadingMore) {
                        LaunchedEffect(index) { onLoadMore() }
                    }
                }
                if (isLoadingMore) {
                    items(3) { SkeletonUserRow() }
                }
            }
        } else {
            val gridState = rememberLazyGridState()
            // For clubMembers we lazily enrich preview images for the visible
            // window; report the range to the ViewModel whenever it changes.
            if (source == "clubMembers") {
                val firstVisible by remember { derivedStateOf { gridState.firstVisibleItemIndex } }
                val lastVisible by remember {
                    derivedStateOf {
                        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            ?: gridState.firstVisibleItemIndex
                    }
                }
                LaunchedEffect(firstVisible, lastVisible, matches.size) {
                    onVisibleRangeChange(firstVisible, lastVisible)
                }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = CorusSpacing.lg, end = CorusSpacing.lg, bottom = CorusSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItemsIndexed(matches, key = { _, m -> m.id }) { index, match ->
                    // For clubMembers, matches arrive without album-art previews
                    // and get enriched as they scroll into view; show a skeleton
                    // tile until then so card heights stay stable.
                    val showSkeleton = source == "clubMembers" && match.matchData == null
                    AnimatedContent(
                        targetState = showSkeleton,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "clubMemberCardEnrich",
                    ) { skeleton ->
                        if (skeleton) {
                            SkeletonTasteMatchCard()
                        } else {
                            TasteMatchCard(
                                match = match,
                                isFollowing = match.user.id in followedIds,
                                onUserTap = {
                                    onUserTapped(match.user.id)
                                    onNavigateToUser(match.user.id)
                                },
                                onFollowTap = { onFollow(match.user) },
                                subtitle = subtitleForRow(context, match, source),
                                subtitleLines = subtitleLinesForSource(source),
                                preferDisplayName = source == "artistsOnCorus",
                            )
                        }
                    }
                    if (index == matches.lastIndex && hasMore && !isLoadingMore) {
                        LaunchedEffect(index) { onLoadMore() }
                    }
                }
                if (isLoadingMore) {
                    // One clean row in the 2-column grid — 3 left a lopsided
                    // dangling card in a second row.
                    repeat(2) { item { SkeletonTasteMatchCard() } }
                }
            }
        }
      }
    }
}

/** Lines reserved for [TasteMatchCard]'s subtitle in the grid. Popular
 *  ("X followers"), New ("joined …"), and Club Members (display name)
 *  produce fixed single-line strings, so the card's default of 2 would leave a
 *  blank second line; taste matches list shared artist/director names and wrap,
 *  so they keep 2 to hold a uniform card height. Mirrors the branch split in
 *  [subtitleForRow] and matches the rails, which pass subtitleLines = 1. */
internal fun subtitleLinesForSource(source: String): Int =
    if (source == "popular" || source == "new" || source == "clubMembers" || source == "artistsOnCorus") 1 else 2

internal fun subtitleForRow(context: android.content.Context, match: SuggestedUserMatch, source: String): String? {
    return when (source) {
        "popular" -> {
            val count = match.user.followerCount
            context.resources.getQuantityString(fm.corus.android.R.plurals.search_followers_count, count, count)
        }
        "new" -> match.user.createdAt?.let { context.getString(fm.corus.android.R.string.suggested_users_joined_format, DateUtils.relativeTime(context, it)) }
        "clubMembers" -> match.user.displayName.trim().takeIf { it.isNotEmpty() }
        "artistsOnCorus" -> {
            val count = match.user.followerCount
            context.resources.getQuantityString(fm.corus.android.R.plurals.search_followers_count, count, count)
        }
        else -> formatMutualFollowersText(context, match.suggestionReason?.mutualNames)
    }
}
