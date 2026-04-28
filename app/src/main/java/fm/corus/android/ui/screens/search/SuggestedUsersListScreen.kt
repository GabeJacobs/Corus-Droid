package fm.corus.android.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
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
    isFollowed: (String) -> Boolean = { false },
    onFollow: (CymbalUser) -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
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
                    contentPadding = PaddingValues(top = CorusSpacing.md),
                ) {
                    items(12) { SkeletonUserRow() }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(12) { SkeletonTasteMatchCard() }
                }
            }
        } else if (matches.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(fm.corus.android.R.string.suggested_users_empty), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
            }
        } else if (useRowLayout) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = CorusSpacing.md),
            ) {
                itemsIndexed(matches, key = { _, m -> m.id }) { index, match ->
                    SuggestedUserRow(
                        user = match.user,
                        subtitle = subtitleForRow(context, match, source),
                        isFollowed = isFollowed(match.user.id),
                        onTap = { onNavigateToUser(match.user.id) },
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(CorusSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItemsIndexed(matches, key = { _, m -> m.id }) { _, match ->
                    TasteMatchCard(
                        match = match,
                        isFollowing = isFollowed(match.user.id),
                        onUserTap = { onNavigateToUser(match.user.id) },
                        onFollowTap = { onFollow(match.user) },
                    )
                }
            }
        }
      }
    }
}

private fun subtitleForRow(context: android.content.Context, match: SuggestedUserMatch, source: String): String? {
    return when (source) {
        "popular" -> {
            val count = match.user.followerCount
            context.resources.getQuantityString(fm.corus.android.R.plurals.search_followers_count, count, count)
        }
        "new" -> match.user.createdAt?.let { context.getString(fm.corus.android.R.string.suggested_users_joined_format, DateUtils.relativeTime(context, it)) }
        else -> formatMutualFollowersText(context, match.suggestionReason?.mutualNames)
    }
}
