package fm.corus.android.ui.screens.search

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalHashtag
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.ui.navigation.FilmDetailRoute
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.TrendingHashtag
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.data.model.TrendingWindow
import fm.corus.android.domain.HapticManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.service.SearchSection
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.ClubMembersCardRail
import fm.corus.android.ui.components.FilmSearchResultRow
import fm.corus.android.ui.components.HorizontalPopularUsersRail
import fm.corus.android.ui.components.MutualConnectionsCardRail
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.components.SkeletonFilmRow
import fm.corus.android.ui.components.SkeletonSearchSongRow
import fm.corus.android.ui.components.SkeletonSearchUserRow
import fm.corus.android.ui.components.SkeletonTrendingFilmRow
import fm.corus.android.ui.components.SkeletonTrendingSongRow
import fm.corus.android.ui.components.VennDiagramIcon
import fm.corus.android.ui.components.SkeletonTasteMatchCard
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.horizontalRailCardWidth
import fm.corus.android.ui.util.DateUtils
import androidx.compose.foundation.lazy.LazyRow

enum class SearchTab(val labelRes: Int) {
    USERS(fm.corus.android.R.string.search_tab_users),
    SONGS(fm.corus.android.R.string.search_tab_songs),
    FILMS(fm.corus.android.R.string.search_tab_films),
    HASHTAGS(fm.corus.android.R.string.search_tab_hashtags),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (FilmDetailRoute) -> Unit = {},
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit = { _, _, _ -> },
    onNavigateToContactFriends: () -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val userResults by viewModel.userSearchResults.collectAsState()
    val songSearchResults by viewModel.songSearchResults.collectAsState()
    val filmSearchResults by viewModel.filmSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val trendingSongs by viewModel.trendingSongs.collectAsState()
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val isTrendingLoading by viewModel.isTrendingLoading.collectAsState()
    val isTrendingMoviesLoading by viewModel.isTrendingMoviesLoading.collectAsState()
    val trendingSongsWindow by viewModel.trendingSongsWindow.collectAsState()
    val trendingFilmsWindow by viewModel.trendingFilmsWindow.collectAsState()
    val suggestedMatches by viewModel.suggestedMatches.collectAsState()
    val isSuggestedLoading by viewModel.isSuggestedLoading.collectAsState()
    val isTasteMatchPolling by viewModel.isTasteMatchPolling.collectAsState()
    val tasteMatchLoadFailed by viewModel.tasteMatchLoadFailed.collectAsState()
    val recentSearchUsers by viewModel.recentSearchUsers.collectAsState()
    val contactMatches by viewModel.contactMatches.collectAsState()
    val isSyncingContacts by viewModel.isSyncingContacts.collectAsState()
    val contactsSyncStatus by viewModel.contactsSyncStatus.collectAsState()
    val showNoContactMatches by viewModel.showNoContactMatches.collectAsState()
    val newUsers by viewModel.newUsers.collectAsState()
    val clubMembers by viewModel.clubMembers.collectAsState()
    val hashtagSearchResults by viewModel.hashtagSearchResults.collectAsState()
    val trendingHashtags by viewModel.trendingHashtags.collectAsState()
    val isTrendingHashtagsLoading by viewModel.isTrendingHashtagsLoading.collectAsState()
    val trendingHashtagsWindow by viewModel.trendingHashtagsWindow.collectAsState()
    val followedHashtagNames by viewModel.followedHashtagNames.collectAsState()

    val activeTabIndex by viewModel.activeTab.collectAsState()
    val activeTab = SearchTab.entries[activeTabIndex]
    val hasSearchQuery = searchQuery.isNotBlank()
    val searchHasError by viewModel.searchHasError.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val currentTabIsEmpty = when (activeTab) {
        SearchTab.USERS -> userResults.isEmpty()
        SearchTab.SONGS -> songSearchResults.isEmpty()
        SearchTab.FILMS -> filmSearchResults.isEmpty()
        SearchTab.HASHTAGS -> hashtagSearchResults.isEmpty()
    }
    val showSearchOfflineRetry = hasSearchQuery && !isSearching && searchHasError && currentTabIsEmpty
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val showRecentOverlay = isSearchFocused && !hasSearchQuery && activeTab == SearchTab.USERS

    // Dismiss the recent-searches overlay on back press
    BackHandler(enabled = showRecentOverlay) {
        focusManager.clearFocus()
        isSearchFocused = false
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollDismissConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    keyboardController?.hide()
                }
                return Offset.Zero
            }
        }
    }

    // List states for scroll-to-top
    val usersListState = rememberLazyListState()
    val songsListState = rememberLazyListState()
    val filmsListState = rememberLazyListState()
    val hashtagsListState = rememberLazyListState()

    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            when (activeTab) {
                SearchTab.USERS -> usersListState.animateScrollToItem(0)
                SearchTab.SONGS -> songsListState.animateScrollToItem(0)
                SearchTab.FILMS -> filmsListState.animateScrollToItem(0)
                SearchTab.HASHTAGS -> hashtagsListState.animateScrollToItem(0)
            }
            lastScrollTrigger = scrollToTopTrigger
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadInitialData()
    }

    // Trigger search when query or tab changes
    LaunchedEffect(searchQuery, activeTab) {
        viewModel.search(searchQuery, activeTab.ordinal)
    }

    val followingIds by viewModel.followingIds.collectAsState()
    val localFollowedIds by viewModel.localFollowedIds.collectAsState()

    // Derived suggestion categories (matching iOS logic)
    val musicMatchUsers = remember(suggestedMatches) {
        suggestedMatches
            .filter { it.isTasteMatch }
            .sortedByDescending { it.matchData?.similarityScore ?: 0.0 }
    }

    var filterUnfollowedMatches by rememberSaveable { mutableStateOf(true) }
    val allFollowedIds = remember(followingIds, localFollowedIds) { followingIds + localFollowedIds }
    val filteredMusicMatchUsers = remember(musicMatchUsers, filterUnfollowedMatches, allFollowedIds) {
        filteredUnfollowedUsers(filterUnfollowedMatches, musicMatchUsers, allFollowedIds)
    }
    val showUnfollowedMatchesToggle = remember(musicMatchUsers, allFollowedIds) {
        shouldShowUnfollowedFilter(musicMatchUsers, allFollowedIds)
    }

    // Popular-on-Corus filter — mirrors taste matches but uses a *snapshot* of
    // followed ids so following someone via the rail doesn't make their card
    // disappear immediately. Snapshot is refreshed only when followingIds first
    // populates or when the user re-toggles the filter.
    var filterUnfollowedPopular by rememberSaveable { mutableStateOf(true) }
    var popularFilterSnapshot by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hasPrimedPopularSnapshot by remember { mutableStateOf(false) }
    LaunchedEffect(followingIds) {
        if (!hasPrimedPopularSnapshot && followingIds.isNotEmpty()) {
            popularFilterSnapshot = followingIds
            hasPrimedPopularSnapshot = true
        }
    }
    val onSetFilterUnfollowedPopular: (Boolean) -> Unit = { enabled ->
        filterUnfollowedPopular = enabled
        popularFilterSnapshot = followingIds
        hasPrimedPopularSnapshot = true
    }
    val popularRailFilterFollowedIds = if (filterUnfollowedPopular) popularFilterSnapshot else emptySet()
    val showUnfollowedPopularToggle = allFollowedIds.isNotEmpty()

    // Mutual-connection users sorted by mutual-count DESC. The underlying
    // suggestion list mixes in music-similarity-ranked users, so the rail
    // needs this explicit re-sort even though the backend mutualConnections
    // doc itself is already sorted that way.
    val mutualConnectionUsers = remember(suggestedMatches, allFollowedIds) {
        suggestedMatches
            .filter { !allFollowedIds.contains(it.user.id) }
            .filter { !it.isTasteMatch }
            .filter { it.user.cymbalCount > 0 }
            .filter { it.suggestionReason?.mutualNames?.isNotEmpty() == true }
            .sortedByDescending { it.suggestionReason?.mutualCount ?: 0 }
    }

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background).nestedScroll(scrollDismissConnection)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(fm.corus.android.R.string.search_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        // Search bar
        SearchBarSection(
            query = searchQuery,
            showClearButton = searchQuery.isNotBlank() || isSearchFocused,
            onQueryChange = { viewModel.onSearchQueryChange(it) },
            onClear = {
                viewModel.onSearchQueryChange("")
                viewModel.clearSearch()
                focusManager.clearFocus()
                isSearchFocused = false
            },
            onFocusChanged = { isSearchFocused = it },
            placeholder = when (activeTab) {
                SearchTab.SONGS -> stringResource(fm.corus.android.R.string.search_placeholder_songs)
                SearchTab.FILMS -> stringResource(fm.corus.android.R.string.search_placeholder_films)
                SearchTab.USERS -> stringResource(fm.corus.android.R.string.search_placeholder_users)
                SearchTab.HASHTAGS -> stringResource(fm.corus.android.R.string.search_placeholder_hashtags)
            },
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xs))

        // Tab bar
        SearchTabBar(
            selectedTab = activeTab,
            onTabSelected = { viewModel.setActiveTab(it.ordinal) },
        )

        // Content area – a Box so the recent-searches overlay can sit on top
        Box(modifier = Modifier.fillMaxSize()) {
            val searchHaptics = LocalHapticManager.current
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    // Mirrors iOS SearchView.refreshable haptic.
                    searchHaptics.impact(HapticManager.ImpactStyle.LIGHT)
                    viewModel.refresh()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showSearchOfflineRetry) {
                    // When the device is online but search errored, say so —
                    // pointing the user at their internet is misleading in that
                    // case. Default offline copy is kept for actual offline.
                    fm.corus.android.ui.components.OfflineRetryState(
                        modifier = Modifier.fillMaxSize(),
                        onRetry = { viewModel.retrySearch() },
                        icon = if (isConnected) Icons.Filled.WarningAmber else Icons.Filled.WifiOff,
                        title = if (isConnected) {
                            stringResource(fm.corus.android.R.string.search_service_unavailable_title)
                        } else {
                            stringResource(fm.corus.android.R.string.feed_offline_title)
                        },
                        subtitle = if (isConnected) {
                            stringResource(fm.corus.android.R.string.search_service_unavailable_subtitle)
                        } else {
                            stringResource(fm.corus.android.R.string.feed_offline_subtitle)
                        },
                    )
                } else when (activeTab) {
                    SearchTab.USERS -> {
                        if (hasSearchQuery) {
                            UserSearchResults(
                                listState = usersListState,
                                results = userResults,
                                isSearching = isSearching,
                                viewModel = viewModel,
                                onNavigateToUser = { userId ->
                                    val user = userResults.find { it.id == userId }
                                    if (user != null) viewModel.onUserSelected(user)
                                    onNavigateToUser(userId)
                                },
                            )
                        } else {
                            SuggestedUsersContent(
                                listState = usersListState,
                                musicMatchUsers = musicMatchUsers,
                                filteredMusicMatchUsers = filteredMusicMatchUsers,
                                showUnfollowedMatchesToggle = showUnfollowedMatchesToggle,
                                filterUnfollowedMatches = filterUnfollowedMatches,
                                onSetFilterUnfollowed = { filterUnfollowedMatches = it },
                                popularRailFilterFollowedIds = popularRailFilterFollowedIds,
                                showUnfollowedPopularToggle = showUnfollowedPopularToggle,
                                filterUnfollowedPopular = filterUnfollowedPopular,
                                onSetFilterUnfollowedPopular = onSetFilterUnfollowedPopular,
                                mutualConnectionUsers = mutualConnectionUsers,
                                contactMatches = contactMatches,
                                contactsSyncStatus = contactsSyncStatus,
                                isSyncingContacts = isSyncingContacts,
                                showNoContactMatches = showNoContactMatches,
                                newUsers = newUsers,
                                clubMembers = clubMembers,
                                allFollowedIds = allFollowedIds,
                                isSuggestedLoading = isSuggestedLoading,
                                isTasteMatchPolling = isTasteMatchPolling,
                                tasteMatchLoadFailed = tasteMatchLoadFailed,
                                viewModel = viewModel,
                                onNavigateToUser = onNavigateToUser,
                                onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
                                onNavigateToContactFriends = onNavigateToContactFriends,
                            )
                        }
                    }
                    SearchTab.SONGS -> {
                        if (hasSearchQuery) {
                            SongSearchResultsList(
                                listState = songsListState,
                                tracks = songSearchResults,
                                isSearching = isSearching,
                                onSongTap = onNavigateToSong,
                            )
                        } else {
                            TrendingSongsContent(
                                listState = songsListState,
                                songs = trendingSongs,
                                isLoading = isTrendingLoading,
                                window = trendingSongsWindow,
                                onWindowChange = { viewModel.setTrendingSongsWindow(it) },
                                onSongTap = onNavigateToSong,
                                nowPlaying = viewModel.nowPlayingManager,
                            )
                        }
                    }
                    SearchTab.FILMS -> {
                        if (hasSearchQuery) {
                            FilmSearchResultsList(
                                listState = filmsListState,
                                movies = filmSearchResults,
                                isSearching = isSearching,
                                onFilmTap = onNavigateToFilm,
                            )
                        } else {
                            TrendingFilmsContent(
                                listState = filmsListState,
                                movies = trendingMovies,
                                isLoading = isTrendingMoviesLoading,
                                window = trendingFilmsWindow,
                                onWindowChange = { viewModel.setTrendingFilmsWindow(it) },
                                onFilmTap = onNavigateToFilm,
                            )
                        }
                    }
                    SearchTab.HASHTAGS -> {
                        if (hasSearchQuery) {
                            HashtagSearchResultsList(
                                listState = hashtagsListState,
                                hashtags = hashtagSearchResults,
                                isSearching = isSearching,
                                followedHashtagNames = followedHashtagNames,
                                onHashtagTap = { tag -> onNavigateToHashtag(tag.name) },
                                onToggleFollow = { tag -> viewModel.toggleHashtagFollow(tag) },
                            )
                        } else {
                            TrendingHashtagsContent(
                                listState = hashtagsListState,
                                hashtags = trendingHashtags,
                                isLoading = isTrendingHashtagsLoading,
                                followedHashtagNames = followedHashtagNames,
                                window = trendingHashtagsWindow,
                                onWindowChange = { viewModel.setTrendingHashtagsWindow(it) },
                                onHashtagTap = { tag -> onNavigateToHashtag(tag.name) },
                                onToggleFollow = { tag -> viewModel.toggleHashtagFollowByName(tag.name) },
                            )
                        }
                    }
                }
            }

            // Full-screen recent-searches overlay (matches iOS behaviour)
            if (showRecentOverlay) {
                RecentSearchesOverlay(
                    recentUsers = recentSearchUsers,
                    onUserTap = { user ->
                        viewModel.onUserSelected(user)
                        onNavigateToUser(user.id)
                    },
                    onRemoveUser = { userId -> viewModel.removeRecentSearch(userId) },
                    onClearAll = { viewModel.clearRecentSearches() },
                )
            }
        }
    }
}

@Composable
private fun SearchBarSection(
    query: String,
    showClearButton: Boolean = query.isNotBlank(),
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    placeholder: String,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        placeholder = {
            Text(placeholder, style = CorusFont.body, color = CorusColors.Tertiary)
        },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = stringResource(fm.corus.android.R.string.search_cd_search), tint = CorusColors.Secondary)
        },
        trailingIcon = {
            if (showClearButton) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(fm.corus.android.R.string.search_cd_clear), tint = CorusColors.Secondary, modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
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
}

@Composable
private fun SearchTabBar(
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit,
) {
    val density = LocalDensity.current
    var tabRowWidth by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { tabRowWidth = it.size.width },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SearchTab.entries.forEach { tab ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = CorusSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    val textColor by animateColorAsState(
                        targetValue = if (selectedTab == tab) CorusColors.Text else CorusColors.Tertiary,
                        animationSpec = tween(200),
                        label = "tabTextColor",
                    )
                    Text(text = stringResource(tab.labelRes), style = CorusFont.bodyMedium, color = textColor)
                }
            }
        }

        val tabWidth = with(density) { (tabRowWidth / SearchTab.entries.size).toDp() }
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedTab.ordinal,
            animationSpec = tween(200),
            label = "indicatorOffset",
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .height(2.dp)
                    .background(CorusColors.Accent),
            )
        }

        HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
    }
}

@Composable
private fun RecentSearchesOverlay(
    recentUsers: List<CymbalUser>,
    onUserTap: (CymbalUser) -> Unit,
    onRemoveUser: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CorusColors.Background),
    ) {
        if (recentUsers.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(top = CorusSpacing.sm, bottom = CorusSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(fm.corus.android.R.string.search_recent), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(fm.corus.android.R.string.search_clear_all),
                    style = CorusFont.captionMedium,
                    color = CorusColors.Accent,
                    modifier = Modifier.clickable { onClearAll() },
                )
            }
            recentUsers.forEach { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUserTap(user) }
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatarView(
                        avatarURL = user.avatarURL,
                        avatarThumbURL = user.avatarThumbURL,
                        displayName = user.displayName,
                        size = 40.dp,
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        UsernameWithFlair(
                            username = user.username,
                            isVerified = user.isVerified,
                            isClubMember = user.isClubMember,
                            flairStyle = user.flairStyle,
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Text,
                        )
                        if (user.displayName.isNotBlank()) {
                            Text(
                                text = user.displayName,
                                style = CorusFont.caption,
                                color = CorusColors.Secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { onRemoveUser(user.id) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(fm.corus.android.R.string.search_cd_remove),
                            tint = CorusColors.Tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedUsersContent(
    listState: LazyListState = rememberLazyListState(),
    musicMatchUsers: List<SuggestedUserMatch>,
    filteredMusicMatchUsers: List<SuggestedUserMatch>,
    showUnfollowedMatchesToggle: Boolean,
    filterUnfollowedMatches: Boolean,
    onSetFilterUnfollowed: (Boolean) -> Unit,
    popularRailFilterFollowedIds: Set<String>,
    showUnfollowedPopularToggle: Boolean,
    filterUnfollowedPopular: Boolean,
    onSetFilterUnfollowedPopular: (Boolean) -> Unit,
    mutualConnectionUsers: List<SuggestedUserMatch>,
    contactMatches: List<CymbalUser>,
    contactsSyncStatus: String,
    isSyncingContacts: Boolean,
    showNoContactMatches: Boolean,
    newUsers: List<CymbalUser>,
    clubMembers: List<CymbalUser>,
    allFollowedIds: Set<String>,
    isSuggestedLoading: Boolean,
    isTasteMatchPolling: Boolean,
    tasteMatchLoadFailed: Boolean,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit,
    onNavigateToContactFriends: () -> Unit,
) {
    val context = LocalContext.current
    val tasteMatchesTitle = stringResource(fm.corus.android.R.string.search_taste_matches_title)
    val mutualConnectionsTitle = stringResource(fm.corus.android.R.string.search_mutual_connections_title)
    val popularOnCorusTitle = stringResource(fm.corus.android.R.string.search_popular_title)
    val newOnCorusTitle = stringResource(fm.corus.android.R.string.search_new_title)
    val clubMembersTitle = stringResource(fm.corus.android.R.string.search_club_members_title)
    val fromContactsSubtitle = stringResource(fm.corus.android.R.string.search_subtitle_from_contacts)
    val joinedFormat = fm.corus.android.R.string.suggested_users_joined_format
    val memberSinceFormat = fm.corus.android.R.string.suggested_users_member_since_format
    val railExcludeIds = remember(viewModel.currentUserId) {
        viewModel.currentUserId?.let { setOf(it) } ?: emptySet()
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.syncContacts(context.contentResolver)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = CorusSpacing.xxs),
    ) {
        // ── Find Friends from Contacts ──
        item {
            AnimatedVisibility(
                visible = contactsSyncStatus == "notAsked",
                exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
            ) {
                Column(modifier = Modifier.padding(top = CorusSpacing.md)) {
                    FindFriendsFromContactsCard(
                        isSyncing = isSyncingContacts,
                        onTap = {
                            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        },
                        onDismiss = {
                            viewModel.dismissContactsSync()
                        },
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                }
            }
        }

        // ── Friends on Corus (contact matches) ──
        if (isSyncingContacts && contactsSyncStatus == "synced") {
            item {
                SectionHeader(icon = "contacts", title = stringResource(fm.corus.android.R.string.search_section_friends_on_corus))
            }
            items(3) {
                SkeletonUserRow()
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        } else if (contactMatches.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "contacts",
                    title = stringResource(fm.corus.android.R.string.search_section_friends_on_corus),
                    showSeeAll = contactMatches.size > 3,
                    onSeeAll = {
                        viewModel.logSearchSectionSeeAllTapped(SearchSection.FriendsOnCorus)
                        onNavigateToContactFriends()
                    },
                )
            }
            items(contactMatches.take(3), key = { "contact-${it.id}" }) { user ->
                SuggestedUserRow(
                    user = user,
                    subtitle = stringResource(fm.corus.android.R.string.search_subtitle_from_contacts),
                    isFollowed = viewModel.isFollowed(user.id),
                    onTap = {
                        viewModel.logSearchSectionUserTapped(SearchSection.FriendsOnCorus, user.id)
                        onNavigateToUser(user.id)
                    },
                    onFollow = { viewModel.toggleFollow(user, SearchSection.FriendsOnCorus) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        } else if (showNoContactMatches) {
            item {
                NoContactMatchesCard()
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        }

        // ── Taste Matches section ──
        // Always present the section: real cards when we have matches, a skeleton
        // while we're still loading/polling, and a short explainer whenever a user
        // has no taste matches yet (regardless of post count) so the slot reads as
        // "coming soon" rather than missing. (For brand-new users with no posts the
        // ViewModel skips the cold-start poll, so they reach the explainer fast.)
        if (musicMatchUsers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "sparkles",
                    title = stringResource(fm.corus.android.R.string.search_section_taste_matches),
                    showSeeAll = filteredMusicMatchUsers.size > 2,
                    onSeeAll = {
                        viewModel.logSearchSectionSeeAllTapped(SearchSection.TasteMatches)
                        onNavigateToSuggestedUsers(tasteMatchesTitle, false, "tasteMatches")
                    },
                    trailingAction = if (showUnfollowedMatchesToggle) {
                        {
                            UnfollowedUsersFilterMenu(
                                filterUnfollowed = filterUnfollowedMatches,
                                onSetFilterUnfollowed = onSetFilterUnfollowed,
                                contentDescription = stringResource(fm.corus.android.R.string.search_cd_filter_taste_matches),
                            )
                        }
                    } else null,
                )
            }
            item {
                // Horizontal rail — peek of next card on the right edge so the
                // user can see it scrolls. Matches iOS SearchView.tasteMatchesRail.
                val cardWidth = horizontalRailCardWidth()
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    items(filteredMusicMatchUsers, key = { it.user.id }) { match ->
                        TasteMatchCard(
                            match = match,
                            isFollowing = viewModel.isFollowed(match.user.id),
                            onUserTap = {
                                // Keep music_match_tapped (carries similarity_score, unique to this section)
                                viewModel.logMusicMatchTapped(match.user.id, match.matchData?.similarityScore ?: 0.0)
                                // Also fire the unified event so cross-section comparisons work.
                                viewModel.logSearchSectionUserTapped(SearchSection.TasteMatches, match.user.id)
                                onNavigateToUser(match.user.id)
                            },
                            onFollowTap = { viewModel.toggleFollow(match.user, SearchSection.TasteMatches) },
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }
        } else if (isSuggestedLoading || isTasteMatchPolling) {
            item {
                SectionHeader(icon = "sparkles", title = stringResource(fm.corus.android.R.string.search_section_taste_matches))
            }
            item {
                val cardWidth = horizontalRailCardWidth()
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    items(4) { SkeletonTasteMatchCard(modifier = Modifier.width(cardWidth)) }
                }
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }
        } else if (tasteMatchLoadFailed) {
            // Failed/timed-out load with nothing to show: hide the section entirely
            // rather than implying the user has no taste matches. It reappears on the
            // next successful load (or the cold-start poll recovering).
        } else {
            item {
                SectionHeader(icon = "sparkles", title = stringResource(fm.corus.android.R.string.search_section_taste_matches))
            }
            item {
                TasteMatchesEmptyCard()
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }
        }

        // ── Popular on Corus — paginated horizontal rail of real users ──
        item {
            val popularFilterCd = stringResource(fm.corus.android.R.string.search_cd_filter_popular_users)
            HorizontalPopularUsersRail(
                excludeIds = railExcludeIds,
                followedIds = allFollowedIds,
                onUserTap = { user ->
                    viewModel.logSearchSectionUserTapped(SearchSection.Popular, user.id)
                    onNavigateToUser(user.id)
                },
                onFollowTap = { user -> viewModel.toggleFollow(user, SearchSection.Popular) },
                onSeeAll = {
                    viewModel.logSearchSectionSeeAllTapped(SearchSection.Popular)
                    onNavigateToSuggestedUsers(popularOnCorusTitle, false, "popular")
                },
                filterFollowedIds = popularRailFilterFollowedIds,
                trailingAction = if (showUnfollowedPopularToggle) {
                    {
                        UnfollowedUsersFilterMenu(
                            filterUnfollowed = filterUnfollowedPopular,
                            onSetFilterUnfollowed = onSetFilterUnfollowedPopular,
                            contentDescription = popularFilterCd,
                        )
                    }
                } else null,
            )
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
        }

        // ── Corus Club Members ──
        // Horizontal rail of TasteMatchCards ordered by initial sign-up
        // (most recent first). Mirrors web; placement matches iOS.
        if (clubMembers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "club",
                    title = stringResource(fm.corus.android.R.string.search_section_club_members),
                    showSeeAll = true,
                    onSeeAll = {
                        viewModel.logSearchSectionSeeAllTapped(SearchSection.ClubMembers)
                        onNavigateToSuggestedUsers(clubMembersTitle, false, "clubMembers")
                    },
                )
            }
            item {
                ClubMembersCardRail(
                    users = clubMembers,
                    followedIds = allFollowedIds,
                    onUserTap = { user ->
                        viewModel.logSearchSectionUserTapped(SearchSection.ClubMembers, user.id)
                        onNavigateToUser(user.id)
                    },
                    onFollowTap = { user -> viewModel.toggleFollow(user, SearchSection.ClubMembers) },
                    memberSinceLabel = { date ->
                        context.getString(memberSinceFormat, DateUtils.relativeTime(context, date))
                    },
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }
        }

        // ── Mutual Connections section ──
        // Horizontal rail of TasteMatchCards (2x2 album-art) — matches iOS
        // SearchView.mutualConnectionsSection / MutualConnectionsCardGrid.
        if (mutualConnectionUsers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "people",
                    title = stringResource(fm.corus.android.R.string.search_section_mutual_connections),
                    showSeeAll = mutualConnectionUsers.size > 2,
                    onSeeAll = {
                        viewModel.logSearchSectionSeeAllTapped(SearchSection.MutualConnections)
                        onNavigateToSuggestedUsers(mutualConnectionsTitle, true, "mutualConnections")
                    },
                )
            }
            item {
                MutualConnectionsCardRail(
                    matches = mutualConnectionUsers,
                    followedIds = allFollowedIds,
                    onUserTap = { user ->
                        viewModel.logSearchSectionUserTapped(SearchSection.MutualConnections, user.id)
                        onNavigateToUser(user.id)
                    },
                    onFollowTap = { user -> viewModel.toggleFollow(user, SearchSection.MutualConnections) },
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }
        }

        // ── New on Corus ──
        val seenNewIds = buildSet { mutualConnectionUsers.forEach { add(it.user.id) } }
        val displayNewUsers = newUsers.filter {
            !seenNewIds.contains(it.id) && !viewModel.isFollowed(it.id)
        }
        if (displayNewUsers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "new",
                    title = stringResource(fm.corus.android.R.string.search_section_new),
                    showSeeAll = displayNewUsers.size > 3,
                    onSeeAll = {
                        viewModel.logSearchSectionSeeAllTapped(SearchSection.NewOnCorus)
                        onNavigateToSuggestedUsers(newOnCorusTitle, true, "new")
                    },
                )
            }
            items(displayNewUsers.take(3), key = { "new-${it.id}" }) { user ->
                SuggestedUserRow(
                    user = user,
                    subtitle = user.createdAt?.let { context.getString(joinedFormat, DateUtils.relativeTime(context, it)) },
                    isFollowed = viewModel.isFollowed(user.id),
                    onTap = {
                        viewModel.logSearchSectionUserTapped(SearchSection.NewOnCorus, user.id)
                        onNavigateToUser(user.id)
                    },
                    onFollow = { viewModel.toggleFollow(user, SearchSection.NewOnCorus) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        }

        // ── Invite friends ──
        if (!isSuggestedLoading) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CorusSpacing.xxxl, horizontal = CorusSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(fm.corus.android.R.string.search_invite_title), style = CorusFont.songTitleLarge, color = CorusColors.Text)
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                Button(
                    onClick = { /* share intent */ },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.xl, vertical = CorusSpacing.sm),
                ) {
                    Text(stringResource(fm.corus.android.R.string.search_invite_button), style = CorusFont.bodyMedium)
                }
            }
        }
        }
    }
}

@Composable
private fun FindFriendsFromContactsCard(
    isSyncing: Boolean,
    onTap: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    val cardShape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .clip(cardShape)
            .background(CorusColors.CardBackground)
            .border(0.5.dp, CorusColors.Divider, cardShape)
            .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Balances the dismiss icon on the right so the icon + title stay
            // optically centered in the card.
            Spacer(modifier = Modifier.size(18.dp))

            Text(
                stringResource(fm.corus.android.R.string.search_contacts_card_title),
                style = CorusFont.songTitle,
                color = CorusColors.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(fm.corus.android.R.string.search_contacts_card_not_now),
                tint = CorusColors.Tertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.xs))

        Text(
            stringResource(fm.corus.android.R.string.search_contacts_card_subtitle),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        Button(
            onClick = onTap,
            enabled = !isSyncing,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = CorusColors.Accent,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            modifier = Modifier.heightIn(min = 0.dp),
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(fm.corus.android.R.string.search_contacts_card_sync), style = CorusFont.buttonSmall)
            }
        }
    }
}

@Composable
private fun NoContactMatchesCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CorusSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(fm.corus.android.R.string.search_no_contact_matches_title),
            style = CorusFont.body,
            color = CorusColors.Secondary,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.xxs))
        Text(
            stringResource(fm.corus.android.R.string.search_no_contact_matches_subtitle),
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
        )
    }
}

/**
 * Empty state for the Taste Matches rail shown to brand-new users who haven't
 * posted yet (no taste data to match on). Explains what the section will become
 * so it reads as "coming soon" rather than a broken/empty rail.
 */
@Composable
private fun TasteMatchesEmptyCard() {
    val shape = RoundedCornerShape(CorusSpacing.cornerRadiusLarge)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .clip(shape)
            .background(CorusColors.CardBackground)
            .border(0.5.dp, CorusColors.Divider, shape)
            .padding(CorusSpacing.md),
    ) {
        Text(
            stringResource(fm.corus.android.R.string.search_taste_matches_empty),
            style = CorusFont.body,
            color = CorusColors.Secondary,
        )
    }
}

@Composable
internal fun SectionHeader(
    icon: String,
    title: String,
    showSeeAll: Boolean = false,
    onSeeAll: () -> Unit = {},
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconVector = when (icon) {
            "people" -> Icons.Filled.People
            "trending" -> Icons.Filled.AutoAwesome
            "new" -> Icons.Filled.PersonAdd
            "contacts" -> Icons.Filled.Contacts
            "bot" -> Icons.Filled.SmartToy
            "hashtag" -> Icons.Filled.Tag
            "fire" -> Icons.Filled.LocalFireDepartment
            else -> null
        }
        if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = CorusColors.Accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
        } else if (icon == "sparkles") {
            // Taste Matches: the custom two-circle Venn with the lens-shaped
            // overlap shaded in (matches the taste-match sheet hero on iOS).
            VennDiagramIcon(
                size = 24.dp,
                color = CorusColors.Accent,
                shadedIntersection = true,
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
        } else if (icon == "club") {
            // Match web/iOS: brand the section with the Corus logo mark — same
            // asset used for the club-member flair badge.
            Icon(
                painter = androidx.compose.ui.res.painterResource(
                    fm.corus.android.R.drawable.logo_no_background
                ),
                contentDescription = null,
                tint = CorusColors.Accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
        }
        Text(title, style = CorusFont.sectionHeader, color = CorusColors.Secondary)
        if (trailingAction != null) {
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            trailingAction()
        }
        Spacer(modifier = Modifier.weight(1f))
        if (showSeeAll) {
            Text(
                stringResource(fm.corus.android.R.string.search_see_all),
                style = CorusFont.captionMedium,
                color = CorusColors.Accent,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        }
    }
}

@Composable
internal fun UnfollowedUsersFilterMenu(
    filterUnfollowed: Boolean,
    onSetFilterUnfollowed: (Boolean) -> Unit,
    contentDescription: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = if (filterUnfollowed) {
                    Icons.Filled.FilterAlt
                } else {
                    Icons.Outlined.FilterAlt
                },
                contentDescription = contentDescription,
                tint = if (filterUnfollowed) CorusColors.Accent else CorusColors.Tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(fm.corus.android.R.string.search_filter_all)) },
                onClick = {
                    onSetFilterUnfollowed(false)
                    expanded = false
                },
                leadingIcon = if (!filterUnfollowed) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
            )
            DropdownMenuItem(
                text = { Text(stringResource(fm.corus.android.R.string.search_filter_unfollowed)) },
                onClick = {
                    onSetFilterUnfollowed(true)
                    expanded = false
                },
                leadingIcon = if (filterUnfollowed) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
            )
        }
    }
}

@Composable
private fun UserSearchResults(
    listState: LazyListState = rememberLazyListState(),
    results: List<CymbalUser>,
    isSearching: Boolean,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        if (isSearching && results.isEmpty()) {
            items(6) { SkeletonSearchUserRow() }
        } else if (results.isEmpty()) {
            item {
                Text(
                    stringResource(fm.corus.android.R.string.search_no_users_found),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CorusSpacing.xxl),
                )
            }
        } else {
            items(results, key = { it.id }) { user ->
                SuggestedUserRow(
                    user = user,
                    isFollowed = viewModel.isFollowed(user.id),
                    onTap = { onNavigateToUser(user.id) },
                    onFollow = { viewModel.toggleFollow(user) },
                )
            }
        }
    }
}

@Composable
fun SuggestedUserRow(
    user: CymbalUser,
    subtitle: String? = null,
    isFollowed: Boolean = false,
    onTap: () -> Unit = {},
    onFollow: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = 44.dp)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            // Lead with the @username (with flair) to match search/follow lists;
            // display name sits muted below.
            UsernameWithFlair(
                username = user.username,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = user.flairStyle,
                isBot = user.isBot,
                showAtPrefix = true,
                style = CorusFont.username,
                color = CorusColors.Text,
            )
            if (user.displayName.isNotBlank()) {
                Text(
                    text = user.displayName,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Flavor text (subtitle like "Followed by @user1, @user2")
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Button(
            onClick = onFollow,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowed) CorusColors.CardBackground else CorusColors.Accent,
                contentColor = if (isFollowed) CorusColors.Secondary else Color.White,
            ),
            border = if (isFollowed) androidx.compose.foundation.BorderStroke(1.dp, CorusColors.Divider) else null,
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
            modifier = Modifier.height(30.dp),
        ) {
            Text(if (isFollowed) stringResource(fm.corus.android.R.string.search_button_following) else stringResource(fm.corus.android.R.string.search_button_follow), style = CorusFont.buttonSmall)
        }
    }
}

@Composable
private fun TrendingSongsContent(
    listState: LazyListState = rememberLazyListState(),
    songs: List<TrendingSong>,
    isLoading: Boolean,
    window: TrendingWindow,
    onWindowChange: (TrendingWindow) -> Unit,
    onSongTap: (CymbalTrack) -> Unit,
    nowPlaying: fm.corus.android.domain.NowPlayingManager,
) {
    val header: @Composable () -> Unit = {
        TrendingHeader(iconName = "music", window = window, onWindowChange = onWindowChange)
    }
    if (isLoading) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { header() }
            items(5) { SkeletonTrendingSongRow() }
        }
        return
    }
    if (songs.isEmpty()) {
        // Keep the header with its picker visible so the user can pick a
        // different window when one is empty (e.g. nothing trending this week).
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { header() }
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83C\uDFB5", style = CorusFont.songTitleLarge, modifier = Modifier.padding(bottom = CorusSpacing.sm))
                        Text(stringResource(fm.corus.android.R.string.search_nothing_trending), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Text(stringResource(fm.corus.android.R.string.search_post_some_songs), style = CorusFont.body, color = CorusColors.Tertiary)
                    }
                }
            }
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { header() }
            itemsIndexed(songs) { index, song ->
                TrendingSongRow(song = song, nowPlaying = nowPlaying, onClick = { onSongTap(song.track) })
                if (index < songs.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun TrendingSongRow(
    song: TrendingSong,
    nowPlaying: fm.corus.android.domain.NowPlayingManager,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${song.rank}",
            style = CorusFont.bodyMedium,
            color = CorusColors.Tertiary,
            modifier = Modifier.width(24.dp),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        fm.corus.android.ui.components.SongPreviewArtwork(
            track = song.track,
            nowPlaying = nowPlaying,
            size = 44.dp,
            cornerRadius = 4.dp,
            contentDescription = song.track.name,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.track.name, style = CorusFont.bodyMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.track.artistName, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${song.cymbalCount}", style = CorusFont.caption, color = CorusColors.Tertiary)
    }
}

@Composable
private fun TrendingFilmsContent(
    listState: LazyListState = rememberLazyListState(),
    movies: List<TrendingMovie>,
    isLoading: Boolean,
    window: TrendingWindow,
    onWindowChange: (TrendingWindow) -> Unit,
    onFilmTap: (FilmDetailRoute) -> Unit,
) {
    val header: @Composable () -> Unit = {
        TrendingHeader(iconName = "film", window = window, onWindowChange = onWindowChange)
    }
    if (isLoading) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { header() }
            items(10) { SkeletonTrendingFilmRow() }
        }
        return
    }
    if (movies.isEmpty()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { header() }
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83C\uDFAC", style = CorusFont.songTitleLarge, modifier = Modifier.padding(bottom = CorusSpacing.sm))
                        Text(stringResource(fm.corus.android.R.string.search_no_trending_films), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Text(stringResource(fm.corus.android.R.string.search_post_some_films), style = CorusFont.body, color = CorusColors.Tertiary)
                    }
                }
            }
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { header() }
            itemsIndexed(movies) { index, movie ->
                TrendingFilmRow(movie = movie, onClick = {
                    onFilmTap(FilmDetailRoute(
                        movieId = movie.movieId,
                        movieTitle = movie.movieTitle,
                        directorName = movie.directorName.ifBlank { null },
                        releaseYear = movie.releaseYear.ifBlank { null },
                        posterURL = movie.posterURL,
                        posterLargeURL = movie.posterLargeURL,
                        trailerURL = movie.trailerURL,
                    ))
                })
                if (index < movies.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    }
}

/** Section header for trending content. The whole phrase
 *  ("TRENDING THIS WEEK ▾") is one tappable Row anchoring a DropdownMenu, so
 *  the picker can never visually disjoin from the surrounding text — they
 *  re-render as one unit even during pull-to-refresh / state changes. Mirrors
 *  the iOS `trendingSectionHeader`. */
@Composable
private fun TrendingHeader(
    iconName: String,
    window: TrendingWindow,
    onWindowChange: (TrendingWindow) -> Unit,
    /** Optional noun to inject between "TRENDING" and "THIS" — e.g. "hashtags"
     *  renders "TRENDING HASHTAGS THIS WEEK ▾". Omit for songs/films (header
     *  reads just "TRENDING THIS WEEK ▾"); the surrounding tab already says
     *  Songs/Films. */
    noun: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val iconVector = when (iconName) {
        "music" -> Icons.Filled.MusicNote
        "film" -> Icons.Filled.Movie
        "hashtag" -> Icons.Filled.Tag
        else -> null
    }
    val windowLabelRes = when (window) {
        TrendingWindow.WEEK -> fm.corus.android.R.string.search_trending_window_week
        TrendingWindow.MONTH -> fm.corus.android.R.string.search_trending_window_month
        TrendingWindow.YEAR -> fm.corus.android.R.string.search_trending_window_year
    }
    val prefixText = if (!noun.isNullOrEmpty()) {
        stringResource(fm.corus.android.R.string.search_section_trending) +
            " " + noun.uppercase() + " " +
            stringResource(fm.corus.android.R.string.search_section_trending_this_suffix) + " "
    } else {
        stringResource(fm.corus.android.R.string.search_section_trending_this) + " "
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CorusSpacing.lg, end = CorusSpacing.lg, top = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = CorusColors.Accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = prefixText,
                    style = CorusFont.sectionHeader,
                    color = CorusColors.Secondary,
                )
                Text(
                    text = stringResource(windowLabelRes),
                    style = CorusFont.sectionHeader,
                    color = CorusColors.Text,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(fm.corus.android.R.string.search_trending_window_aria),
                    tint = CorusColors.Secondary,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                TrendingWindow.values().forEach { option ->
                    val optionLabelRes = when (option) {
                        TrendingWindow.WEEK -> fm.corus.android.R.string.search_trending_window_week
                        TrendingWindow.MONTH -> fm.corus.android.R.string.search_trending_window_month
                        TrendingWindow.YEAR -> fm.corus.android.R.string.search_trending_window_year
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(optionLabelRes)) },
                        onClick = {
                            expanded = false
                            if (option != window) onWindowChange(option)
                        },
                        trailingIcon = if (option == window) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = CorusColors.Accent, modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TrendingFilmRow(
    movie: TrendingMovie,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${movie.rank}",
            style = CorusFont.bodyMedium,
            color = CorusColors.Tertiary,
            modifier = Modifier.width(24.dp),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        ShimmerAsyncImage(
            model = movie.posterURL,
            contentDescription = null,
            modifier = Modifier
                .width(33.dp)
                .height(44.dp),
            shape = RoundedCornerShape(4.dp),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(movie.movieTitle, style = CorusFont.bodyMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(movie.directorName, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${movie.cymbalCount}", style = CorusFont.caption, color = CorusColors.Tertiary)
    }
}

// ── Song Search Results ──

@Composable
private fun SongSearchResultsList(
    listState: LazyListState = rememberLazyListState(),
    tracks: List<CymbalTrack>,
    isSearching: Boolean,
    onSongTap: (CymbalTrack) -> Unit,
) {
    if (isSearching && tracks.isEmpty()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            items(8) { index ->
                SkeletonSearchSongRow()
                if (index < 7) {
                    HorizontalDivider(modifier = Modifier.padding(start = 84.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    } else if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(fm.corus.android.R.string.search_no_songs_found), style = CorusFont.body, color = CorusColors.Secondary)
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            itemsIndexed(tracks) { index, track ->
                SongSearchRow(track = track, onClick = { onSongTap(track) })
                if (index < tracks.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 84.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun SongSearchRow(track: CymbalTrack, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(CorusSpacing.albumArtThumbnail)) {
            AsyncImage(
                model = track.albumArtURL,
                contentDescription = track.name,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                contentScale = ContentScale.Crop,
            )
            if (track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                fm.corus.android.ui.components.SoundCloudBadgeOverlay(
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Text(track.name, style = CorusFont.bodyMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (track.durationMs > 0) {
            Text(
                track.formattedDuration,
                style = CorusFont.caption.copy(fontFeatureSettings = "tnum"),
                color = CorusColors.Tertiary,
            )
        }
    }
}

// ── Film Search Results ──

@Composable
private fun FilmSearchResultsList(
    listState: LazyListState = rememberLazyListState(),
    movies: List<CymbalMovie>,
    isSearching: Boolean,
    onFilmTap: (FilmDetailRoute) -> Unit,
) {
    if (isSearching && movies.isEmpty()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            items(8) { index ->
                SkeletonFilmRow()
                if (index < 7) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    } else if (movies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(fm.corus.android.R.string.search_no_films_found), style = CorusFont.body, color = CorusColors.Secondary)
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            itemsIndexed(movies) { index, movie ->
                FilmSearchResultRow(movie = movie, onClick = {
                    onFilmTap(FilmDetailRoute(
                        movieId = movie.id,
                        movieTitle = movie.title,
                        directorName = movie.directorName.ifBlank { null },
                        releaseYear = movie.year.ifBlank { null },
                        posterURL = movie.posterURL,
                        posterLargeURL = movie.posterLargeURL,
                        trailerURL = movie.trailerURL,
                    ))
                })
                if (index < movies.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    }
}

// ── Helpers ──

internal fun formatMutualFollowersText(context: android.content.Context, names: List<String>?): String? {
    if (names.isNullOrEmpty()) return null
    return when (names.size) {
        1 -> context.getString(fm.corus.android.R.string.search_followed_by_one_format, names[0])
        2 -> context.getString(fm.corus.android.R.string.search_followed_by_two_format, names[0], names[1])
        else -> {
            val others = names.size - 2
            val othersText = context.resources.getQuantityString(fm.corus.android.R.plurals.search_others_count, others, others)
            context.getString(fm.corus.android.R.string.search_followed_by_many_format, names[0], names[1], othersText)
        }
    }
}

// ── Hashtag Search ──

@Composable
private fun TrendingHashtagsContent(
    listState: LazyListState = rememberLazyListState(),
    hashtags: List<TrendingHashtag>,
    isLoading: Boolean,
    followedHashtagNames: Set<String>,
    window: TrendingWindow,
    onWindowChange: (TrendingWindow) -> Unit,
    onHashtagTap: (TrendingHashtag) -> Unit,
    onToggleFollow: (TrendingHashtag) -> Unit,
) {
    val postNoun = stringResource(fm.corus.android.R.string.post_noun)
    val postNounPlural = stringResource(fm.corus.android.R.string.post_noun_plural)
    val followerWord = stringResource(fm.corus.android.R.string.hashtag_followers)
    val followerSingular = followerWord.removeSuffix("s")
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl),
    ) {
        item {
            TrendingHeader(
                iconName = "hashtag",
                window = window,
                onWindowChange = onWindowChange,
                noun = "hashtags",
            )
        }
        if (isLoading) {
            items(8) { SkeletonSearchSongRow() }
        } else if (hashtags.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "#",
                            style = CorusFont.songTitleLarge,
                            color = CorusColors.Tertiary,
                            modifier = Modifier.padding(bottom = CorusSpacing.sm),
                        )
                        Text(
                            stringResource(fm.corus.android.R.string.search_no_trending_hashtags),
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Secondary,
                        )
                    }
                }
            }
        } else {
            itemsIndexed(hashtags) { index, tag ->
                val noun = if (tag.cymbalCount == 1) postNoun else postNounPlural
                val subtitle = if (tag.followerCount > 0) {
                    val fNoun = if (tag.followerCount == 1) followerSingular else followerWord
                    "${tag.cymbalCount} $noun · ${tag.followerCount} $fNoun"
                } else {
                    "${tag.cymbalCount} $noun"
                }
                HashtagRow(
                    name = tag.name,
                    fallbackCount = tag.cymbalCount,
                    subtitleOverride = subtitle,
                    isFollowing = followedHashtagNames.contains(tag.name.lowercase()),
                    onClick = { onHashtagTap(tag) },
                    onToggleFollow = { onToggleFollow(tag) },
                )
                if (index < hashtags.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 88.dp),
                        color = CorusColors.Divider,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HashtagSearchResultsList(
    listState: LazyListState = rememberLazyListState(),
    hashtags: List<CymbalHashtag>,
    isSearching: Boolean,
    followedHashtagNames: Set<String>,
    onHashtagTap: (CymbalHashtag) -> Unit,
    onToggleFollow: (CymbalHashtag) -> Unit,
) {
    if (isSearching && hashtags.isEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
        ) {
            items(8) { index ->
                SkeletonSearchSongRow()
                if (index < 7) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 88.dp),
                        color = CorusColors.Divider,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    } else if (hashtags.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(fm.corus.android.R.string.search_no_hashtags_found),
                style = CorusFont.body,
                color = CorusColors.Secondary,
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
        ) {
            itemsIndexed(hashtags) { index, tag ->
                HashtagRow(
                    name = tag.name,
                    fallbackCount = tag.cymbalCount,
                    isFollowing = followedHashtagNames.contains(tag.name.lowercase()),
                    onClick = { onHashtagTap(tag) },
                    onToggleFollow = { onToggleFollow(tag) },
                )
                if (index < hashtags.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 88.dp),
                        color = CorusColors.Divider,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HashtagRow(
    name: String,
    fallbackCount: Int,
    isFollowing: Boolean,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
    /** Trending rows pass a precomposed subtitle ("N coruses · M followers").
     *  Search-result rows omit it and the row falls back to the live count
     *  fetched via `fetchHashtagPreview`. */
    subtitleOverride: String? = null,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    // Seed from the ViewModel's preview cache so a tab-switch round-trip
    // doesn't flash the shimmer skeleton on already-loaded hashtags.
    var preview by remember(name) {
        mutableStateOf(viewModel.cachedHashtagPreview(name))
    }
    LaunchedEffect(name) {
        if (preview == null) {
            preview = runCatching {
                viewModel.fetchHashtagPreview(name)
            }.getOrNull() ?: fm.corus.android.data.remote.FirestoreDataSource.HashtagPreview(
                coverArt = emptyList(),
                totalCount = fallbackCount,
            )
        }
    }
    // Hide phantom hashtags (orphan docs, drift, deleted-post cleanup). For
    // trending rows the parent already gates on cymbalCount > 0 so we only
    // hide search-result rows here.
    if (subtitleOverride == null && preview != null && (preview?.totalCount ?: 0) == 0) return

    val liveCount = preview?.totalCount
    val fallbackNoun = stringResource(
        if (liveCount == 1) fm.corus.android.R.string.post_noun
        else fm.corus.android.R.string.post_noun_plural
    )
    val subtitle: String? = subtitleOverride
        ?: liveCount?.let { "$it $fallbackNoun" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md)
            .heightIn(min = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HashtagAvatarMosaic(
            urls = preview?.coverArt ?: emptyList(),
            isLoaded = preview != null,
            size = 60.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Text(
                text = "#$name",
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
            }
        }
        HashtagFollowPill(
            isFollowing = isFollowing,
            onClick = onToggleFollow,
        )
    }
}

/** 2x2 mosaic of recent post album art for a hashtag, with graceful fallbacks:
 *  loading → 2x2 shimmer skeleton, empty → `#` glyph, partial → fills missing
 *  cells with a soft placeholder. Mirrors iOS `HashtagAvatarMosaic`. */
@Composable
private fun HashtagAvatarMosaic(
    urls: List<String>,
    isLoaded: Boolean,
    size: androidx.compose.ui.unit.Dp,
) {
    val cornerRadius = 8.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius)),
    ) {
        if (!isLoaded) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CorusColors.CardBackground))
                    Spacer(modifier = Modifier.width(1.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CorusColors.CardBackground))
                }
                Spacer(modifier = Modifier.height(1.dp))
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CorusColors.CardBackground))
                    Spacer(modifier = Modifier.width(1.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CorusColors.CardBackground))
                }
            }
        } else if (urls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CorusColors.CardBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Tag,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(size * 0.45f),
                )
            }
        } else {
            val cells: List<String?> = (0 until 4).map { i -> urls.getOrNull(i) }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    HashtagMosaicCell(cells[0], modifier = Modifier.weight(1f).fillMaxHeight())
                    Spacer(modifier = Modifier.width(1.dp))
                    HashtagMosaicCell(cells[1], modifier = Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(modifier = Modifier.height(1.dp))
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    HashtagMosaicCell(cells[2], modifier = Modifier.weight(1f).fillMaxHeight())
                    Spacer(modifier = Modifier.width(1.dp))
                    HashtagMosaicCell(cells[3], modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun HashtagMosaicCell(url: String?, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = modifier.background(CorusColors.CardBackground))
    }
}

@Composable
private fun HashtagFollowPill(
    isFollowing: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(
        if (isFollowing) fm.corus.android.R.string.hashtag_feed_following
        else fm.corus.android.R.string.hashtag_feed_follow
    )
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFollowing) CorusColors.CardBackground else CorusColors.Accent,
            contentColor = if (isFollowing) CorusColors.Secondary else Color.White,
        ),
        border = if (isFollowing) androidx.compose.foundation.BorderStroke(1.dp, CorusColors.Divider) else null,
        contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
        modifier = Modifier.height(30.dp),
    ) {
        Text(text = label, style = CorusFont.buttonSmall)
    }
}
