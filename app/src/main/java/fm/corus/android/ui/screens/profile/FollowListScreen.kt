package fm.corus.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** Groups counts like Instagram's tab labels ("2,352 followers"). */
private fun formatCount(count: Int): String = NumberFormat.getInstance().format(count.toLong())

/**
 * Instagram-style tabbed follow list: Followers / Following, plus a Mutual tab
 * ("Followed by people you follow") that only appears when the signed-in viewer
 * is looking at someone else's profile and has at least one mutual. Each tab is
 * driven by its own [FollowListViewModel] instance so pagination and search
 * stay independent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    userId: String,
    title: String,
    followerCount: Int,
    followingCount: Int,
    initialShowFollowers: Boolean,
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    // Distinct VM instances per tab (scoped to this nav entry via keys).
    val followersVM: FollowListViewModel = hiltViewModel(key = "follow_list_followers")
    val followingVM: FollowListViewModel = hiltViewModel(key = "follow_list_following")
    val mutualVM: FollowListViewModel = hiltViewModel(key = "follow_list_mutual")

    // Mutual is viewer-specific: only on someone else's profile, and only once
    // we know there's at least one mutual.
    val mutualEligible = mutualVM.currentUserId != null && mutualVM.currentUserId != userId
    val mutualCount by mutualVM.mutualCount.collectAsState()
    val mutualCountCapped by mutualVM.mutualCountCapped.collectAsState()
    val showMutual = mutualEligible && mutualCount > 0

    // Eagerly resolve the mutual count (cheap + cached; usually pre-warmed by the
    // profile view) so the tab paints complete. The paginated mutual LIST is
    // loaded lazily by the pager page only when the Mutual tab is opened.
    LaunchedEffect(userId, mutualEligible) {
        if (mutualEligible) mutualVM.loadMutualCount(mutualVM.currentUserId ?: "", userId)
    }

    val mutualResolved by mutualVM.mutualResolved.collectAsState()
    // Hold the tab strip until we know whether the Mutual tab exists, so it's
    // present (leftmost, Instagram order) from the first paint instead of popping
    // in and shoving the others over after the list is already on screen.
    val tabsReady = !mutualEligible || mutualResolved

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (title.isNotEmpty()) "@$title" else "",
                        style = CorusFont.screenTitle,
                        color = CorusColors.Text,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(fm.corus.android.R.string.common_back),
                            tint = CorusColors.Text,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!tabsReady) {
                // Resolving whether a Mutual tab exists — keep the chrome (tab strip
                // + search placeholders) in place so the screen doesn't jump from
                // bare rows to full chrome; the real tabs snap in once resolved.
                FollowListLoadingSkeleton()
            } else {
                FollowListPager(
                    userId = userId,
                    showMutual = showMutual,
                    initialShowFollowers = initialShowFollowers,
                    followerCount = followerCount,
                    followingCount = followingCount,
                    mutualCount = mutualCount,
                    mutualCountCapped = mutualCountCapped,
                    followersVM = followersVM,
                    followingVM = followingVM,
                    mutualVM = mutualVM,
                    onNavigateToUser = onNavigateToUser,
                )
            }
        }
    }
}

/**
 * The TabRow + pager. Only mounted once we know whether the Mutual tab exists,
 * so the tab list — and thus the pager's initial page index — is final and
 * stable for this composable's whole lifetime (no shifting after async resolve).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowListPager(
    userId: String,
    showMutual: Boolean,
    initialShowFollowers: Boolean,
    followerCount: Int,
    followingCount: Int,
    mutualCount: Int,
    mutualCountCapped: Boolean,
    followersVM: FollowListViewModel,
    followingVM: FollowListViewModel,
    mutualVM: FollowListViewModel,
    onNavigateToUser: (String) -> Unit,
) {
    // Mutual leads (Instagram order), then Followers, Following.
    val tabs = if (showMutual) {
        listOf(FollowListMode.MUTUAL, FollowListMode.FOLLOWERS, FollowListMode.FOLLOWING)
    } else {
        listOf(FollowListMode.FOLLOWERS, FollowListMode.FOLLOWING)
    }
    // Open on the tapped tab (followers/following) regardless of Mutual's slot.
    val initialTab = if (initialShowFollowers) FollowListMode.FOLLOWERS else FollowListMode.FOLLOWING
    val initialPage = tabs.indexOf(initialTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.size - 1),
            containerColor = CorusColors.Background,
            contentColor = CorusColors.Text,
        ) {
            tabs.forEachIndexed { index, mode ->
                val label = when (mode) {
                    FollowListMode.FOLLOWERS ->
                        pluralStringResource(
                            fm.corus.android.R.plurals.follow_list_tab_followers,
                            followerCount,
                            formatCount(followerCount),
                        )
                    FollowListMode.FOLLOWING ->
                        stringResource(fm.corus.android.R.string.follow_list_tab_following, formatCount(followingCount))
                    FollowListMode.MUTUAL ->
                        stringResource(
                            fm.corus.android.R.string.follow_list_tab_mutual,
                            formatCount(mutualCount.coerceAtLeast(0)) + if (mutualCountCapped) "+" else "",
                        )
                }
                val selected = pagerState.currentPage == index
                Tab(
                    selected = selected,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            label,
                            style = CorusFont.buttonSmall,
                            color = if (selected) CorusColors.Text else CorusColors.Secondary,
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val mode = tabs[page]
            val vm = when (mode) {
                FollowListMode.FOLLOWERS -> followersVM
                FollowListMode.FOLLOWING -> followingVM
                FollowListMode.MUTUAL -> mutualVM
            }
            FollowListContent(
                userId = userId,
                mode = mode,
                viewModel = vm,
                onNavigateToUser = onNavigateToUser,
            )
        }
    }
}

/**
 * Loading state for the whole screen while the mutual count resolves: tab-strip
 * + search placeholders (so the chrome stays put) above the row skeletons.
 */
@Composable
private fun FollowListLoadingSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab-strip placeholder (3 shimmer pills + divider) ~matching the TabRow.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(width = 70.dp, height = 14.dp)
                        .background(CorusColors.CardBackground, RoundedCornerShape(7.dp)),
                )
            }
        }
        HorizontalDivider(color = CorusColors.Divider)
        // Search-field placeholder.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
                .height(48.dp)
                .background(CorusColors.CardBackground, RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
        )
        FollowListSkeletonList()
    }
}

/** Shimmer placeholder rows shown while the initial list (or tab set) resolves. */
@Composable
private fun FollowListSkeletonList() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(10) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(CorusSpacing.avatarMedium)
                        .background(CorusColors.CardBackground, RoundedCornerShape(50)),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.md))
                Column {
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 12.dp)
                            .background(CorusColors.CardBackground, RoundedCornerShape(4.dp)),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 70.dp, height = 10.dp)
                            .background(CorusColors.CardBackground, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

/**
 * One tab's content: the scoped search bar plus the browse/search list for a
 * single [FollowListMode]. Hosted inside [FollowListScreen]'s pager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowListContent(
    userId: String,
    mode: FollowListMode,
    viewModel: FollowListViewModel,
    onNavigateToUser: (String) -> Unit,
) {
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val followingStatus by viewModel.followingStatus.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val searching = searchQuery.isNotBlank()

    LaunchedEffect(userId, mode) {
        viewModel.loadFollowList(userId, mode)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 3 && hasMore && !isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !searching) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(searchQuery) {
        viewModel.search(searchQuery)
    }

    val displayedUsers = if (searching) searchResults else users
    val showSearchSkeleton = searching && isSearching && searchResults.isEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            placeholder = { Text(stringResource(fm.corus.android.R.string.follow_list_search_placeholder), style = CorusFont.body, color = CorusColors.Tertiary) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = stringResource(fm.corus.android.R.string.follow_list_search_placeholder), tint = CorusColors.Secondary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(fm.corus.android.R.string.follow_list_cd_clear), tint = CorusColors.Secondary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CorusColors.CardBackground,
                unfocusedContainerColor = CorusColors.CardBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = CorusColors.Accent,
            ),
            textStyle = CorusFont.body.copy(color = CorusColors.Text),
        )

        when {
            (!searching && isLoading && users.isEmpty()) || showSearchSkeleton -> {
                FollowListSkeletonList()
            }
            displayedUsers.isEmpty() && !isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (mode == FollowListMode.FOLLOWING) Icons.Outlined.PersonAdd else Icons.Outlined.Group,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    Text(
                        text = when {
                            searching -> stringResource(fm.corus.android.R.string.follow_list_no_results)
                            mode == FollowListMode.FOLLOWERS -> stringResource(fm.corus.android.R.string.follow_list_no_followers)
                            mode == FollowListMode.FOLLOWING -> stringResource(fm.corus.android.R.string.follow_list_not_following)
                            else -> stringResource(fm.corus.android.R.string.follow_list_no_mutuals)
                        },
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Secondary,
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(displayedUsers, key = { it.id }) { user ->
                        FollowUserRow(
                            user = user,
                            isFollowing = followingStatus[user.id] ?: false,
                            isCurrentUser = user.id == viewModel.currentUserId,
                            showFollowBack = mode == FollowListMode.FOLLOWING && viewModel.isFollowedByTarget(user.id),
                            showFollowsYou = mode == FollowListMode.FOLLOWERS && (followingStatus[user.id] ?: false),
                            onUserTap = { onNavigateToUser(user.id) },
                            onFollowTap = { viewModel.toggleFollow(user.id) },
                        )
                    }

                    if (!searching && isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(CorusSpacing.lg),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = CorusColors.Accent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUserRow(
    user: CymbalUser,
    isFollowing: Boolean,
    isCurrentUser: Boolean,
    showFollowBack: Boolean = false,
    showFollowsYou: Boolean = false,
    onUserTap: () -> Unit,
    onFollowTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUserTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = (CorusSpacing.avatarMedium + 8.dp))

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            UsernameWithFlair(
                username = user.username,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = user.flairStyle,
                isBot = user.isBot,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showFollowsYou) {
                    Text(
                        text = " · " + stringResource(fm.corus.android.R.string.follow_list_follows_you),
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                        maxLines = 1,
                    )
                }
            }
        }

        // Follow button (hidden for self)
        if (!isCurrentUser) {
            Spacer(modifier = Modifier.width(CorusSpacing.sm))

            val buttonText = when {
                isFollowing -> stringResource(fm.corus.android.R.string.follow_list_button_following)
                showFollowBack -> stringResource(fm.corus.android.R.string.follow_list_button_follow_back)
                else -> stringResource(fm.corus.android.R.string.follow_list_button_follow)
            }

            Button(
                onClick = onFollowTap,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) CorusColors.Divider else CorusColors.Accent,
                    contentColor = if (isFollowing) CorusColors.Secondary else Color.White,
                ),
                contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                modifier = Modifier.height(32.dp),
            ) {
                Text(buttonText, style = CorusFont.buttonSmall)
            }
        }
    }
}
