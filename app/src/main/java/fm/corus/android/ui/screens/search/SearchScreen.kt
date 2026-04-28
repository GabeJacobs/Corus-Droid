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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.ui.navigation.FilmDetailRoute
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.FilmSearchResultRow
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.components.SkeletonFilmRow
import fm.corus.android.ui.components.SkeletonSearchSongRow
import fm.corus.android.ui.components.SkeletonSearchUserRow
import fm.corus.android.ui.components.SkeletonTrendingFilmRow
import fm.corus.android.ui.components.SkeletonTrendingSongRow
import fm.corus.android.ui.components.SkeletonTasteMatchCard
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils

enum class SearchTab(val labelRes: Int) {
    USERS(fm.corus.android.R.string.search_tab_users),
    SONGS(fm.corus.android.R.string.search_tab_songs),
    FILMS(fm.corus.android.R.string.search_tab_films),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (FilmDetailRoute) -> Unit = {},
    onNavigateToBotList: (String?) -> Unit = {},
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit = { _, _, _ -> },
    onNavigateToContactFriends: () -> Unit = {},
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
    val suggestedMatches by viewModel.suggestedMatches.collectAsState()
    val isSuggestedLoading by viewModel.isSuggestedLoading.collectAsState()
    val curatedMusicBots by viewModel.curatedMusicBots.collectAsState()
    val curatedFilmBots by viewModel.curatedFilmBots.collectAsState()
    val isBotsLoading by viewModel.isBotsLoading.collectAsState()
    val recentSearchUsers by viewModel.recentSearchUsers.collectAsState()
    val contactMatches by viewModel.contactMatches.collectAsState()
    val isSyncingContacts by viewModel.isSyncingContacts.collectAsState()
    val contactsSyncStatus by viewModel.contactsSyncStatus.collectAsState()
    val showNoContactMatches by viewModel.showNoContactMatches.collectAsState()
    val popularUsers by viewModel.popularUsers.collectAsState()
    val isPopularLoading by viewModel.isPopularLoading.collectAsState()
    val newUsers by viewModel.newUsers.collectAsState()

    val activeTabIndex by viewModel.activeTab.collectAsState()
    val activeTab = SearchTab.entries[activeTabIndex]
    val hasSearchQuery = searchQuery.isNotBlank()
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

    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            when (activeTab) {
                SearchTab.USERS -> usersListState.animateScrollToItem(0)
                SearchTab.SONGS -> songsListState.animateScrollToItem(0)
                SearchTab.FILMS -> filmsListState.animateScrollToItem(0)
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
            .filter { it.matchData?.hasSimilarityData == true || (it.user.artistsInCommonCount ?: 0) > 0 }
            .sortedByDescending { it.matchData?.similarityScore ?: 0.0 }
    }

    var filterUnfollowedMatches by rememberSaveable { mutableStateOf(false) }
    val allFollowedIds = remember(followingIds, localFollowedIds) { followingIds + localFollowedIds }
    val filteredMusicMatchUsers = remember(musicMatchUsers, filterUnfollowedMatches, allFollowedIds) {
        filteredMusicMatchUsers(filterUnfollowedMatches, musicMatchUsers, allFollowedIds)
    }
    val showUnfollowedMatchesToggle = remember(musicMatchUsers, allFollowedIds) {
        showUnfollowedMatchesToggle(musicMatchUsers, allFollowedIds)
    }

    val mutualConnectionUsers = remember(suggestedMatches, allFollowedIds) {
        suggestedMatches
            .filter { !allFollowedIds.contains(it.user.id) }
            .filter { it.matchData?.hasSimilarityData != true && (it.user.artistsInCommonCount ?: 0) == 0 }
            .filter { it.user.cymbalCount > 0 }
            .filter { it.suggestionReason?.mutualNames?.isNotEmpty() == true }
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
                when (activeTab) {
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
                                mutualConnectionUsers = mutualConnectionUsers,
                                curatedMusicBots = curatedMusicBots,
                                curatedFilmBots = curatedFilmBots,
                                contactMatches = contactMatches,
                                contactsSyncStatus = contactsSyncStatus,
                                isSyncingContacts = isSyncingContacts,
                                showNoContactMatches = showNoContactMatches,
                                popularUsers = popularUsers,
                                isPopularLoading = isPopularLoading,
                                newUsers = newUsers,
                                isSuggestedLoading = isSuggestedLoading,
                                isBotsLoading = isBotsLoading,
                                viewModel = viewModel,
                                onNavigateToUser = onNavigateToUser,
                                onNavigateToBotList = onNavigateToBotList,
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
                                onSongTap = onNavigateToSong,
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
                                onFilmTap = onNavigateToFilm,
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
    mutualConnectionUsers: List<SuggestedUserMatch>,
    curatedMusicBots: List<SuggestedUserMatch>,
    curatedFilmBots: List<SuggestedUserMatch>,
    contactMatches: List<CymbalUser>,
    contactsSyncStatus: String,
    isSyncingContacts: Boolean,
    showNoContactMatches: Boolean,
    popularUsers: List<CymbalUser>,
    isPopularLoading: Boolean,
    newUsers: List<CymbalUser>,
    isSuggestedLoading: Boolean,
    isBotsLoading: Boolean,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToBotList: (String?) -> Unit,
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit,
    onNavigateToContactFriends: () -> Unit,
) {
    val context = LocalContext.current
    val tasteMatchesTitle = stringResource(fm.corus.android.R.string.search_taste_matches_title)
    val mutualConnectionsTitle = stringResource(fm.corus.android.R.string.search_mutual_connections_title)
    val popularOnCorusTitle = stringResource(fm.corus.android.R.string.search_popular_title)
    val newOnCorusTitle = stringResource(fm.corus.android.R.string.search_new_title)
    val fromContactsSubtitle = stringResource(fm.corus.android.R.string.search_subtitle_from_contacts)
    val joinedFormat = fm.corus.android.R.string.suggested_users_joined_format

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
                    onSeeAll = onNavigateToContactFriends,
                )
            }
            items(contactMatches.take(3), key = { "contact-${it.id}" }) { user ->
                SuggestedUserRow(
                    user = user,
                    subtitle = stringResource(fm.corus.android.R.string.search_subtitle_from_contacts),
                    isFollowed = viewModel.isFollowed(user.id),
                    onTap = { onNavigateToUser(user.id) },
                    onFollow = { viewModel.toggleFollow(user) },
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
        if (isSuggestedLoading && musicMatchUsers.isEmpty()) {
            item {
                SectionHeader(icon = "sparkles", title = stringResource(fm.corus.android.R.string.search_section_taste_matches))
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    SkeletonTasteMatchCard(modifier = Modifier.weight(1f))
                    SkeletonTasteMatchCard(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }
        }
        if (musicMatchUsers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "sparkles",
                    title = stringResource(fm.corus.android.R.string.search_section_taste_matches),
                    showSeeAll = filteredMusicMatchUsers.size > 2,
                    onSeeAll = { onNavigateToSuggestedUsers(tasteMatchesTitle, false, "tasteMatches") },
                    trailingAction = if (showUnfollowedMatchesToggle) {
                        {
                            TasteMatchFilterMenu(
                                filterUnfollowedMatches = filterUnfollowedMatches,
                                onSetFilterUnfollowed = onSetFilterUnfollowed,
                            )
                        }
                    } else null,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    filteredMusicMatchUsers.take(2).forEach { match ->
                        TasteMatchCard(
                            match = match,
                            isFollowing = viewModel.isFollowed(match.user.id),
                            onUserTap = { onNavigateToUser(match.user.id) },
                            onFollowTap = { viewModel.toggleFollow(match.user) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (filteredMusicMatchUsers.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }
        }

        // ── Mutual Connections section ──
        if (mutualConnectionUsers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "people",
                    title = stringResource(fm.corus.android.R.string.search_section_mutual_connections),
                    showSeeAll = mutualConnectionUsers.size > 2,
                    onSeeAll = { onNavigateToSuggestedUsers(mutualConnectionsTitle, true, "mutualConnections") },
                )
            }
            items(mutualConnectionUsers.take(2)) { match ->
                SuggestedUserRow(
                    user = match.user,
                    subtitle = formatMutualFollowersText(context, match.suggestionReason?.mutualNames),
                    isFollowed = viewModel.isFollowed(match.user.id),
                    onTap = { onNavigateToUser(match.user.id) },
                    onFollow = { viewModel.toggleFollow(match.user) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        }

        // ── Popular on Corus ──
        if (popularUsers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "trending",
                    title = stringResource(fm.corus.android.R.string.search_section_popular),
                    showSeeAll = popularUsers.size > 2,
                    onSeeAll = { onNavigateToSuggestedUsers(popularOnCorusTitle, true, "popular") },
                )
            }
            items(popularUsers.take(2), key = { "popular-${it.id}" }) { user ->
                SuggestedUserRow(
                    user = user,
                    subtitle = context.resources.getQuantityString(fm.corus.android.R.plurals.search_followers_count, user.followerCount, user.followerCount),
                    isFollowed = viewModel.isFollowed(user.id),
                    onTap = { onNavigateToUser(user.id) },
                    onFollow = { viewModel.toggleFollow(user) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        } else if (isPopularLoading) {
            item {
                SectionHeader(icon = "trending", title = stringResource(fm.corus.android.R.string.search_section_popular))
            }
            items(2) { SkeletonUserRow() }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        }

        // ── New on Corus ──
        val seenNewIds = buildSet {
            mutualConnectionUsers.forEach { add(it.user.id) }
            popularUsers.take(2).forEach { add(it.id) }
        }
        val displayNewUsers = newUsers.filter {
            !seenNewIds.contains(it.id) && !viewModel.isFollowed(it.id)
        }
        if (displayNewUsers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "new",
                    title = stringResource(fm.corus.android.R.string.search_section_new),
                    showSeeAll = displayNewUsers.size > 2,
                    onSeeAll = { onNavigateToSuggestedUsers(newOnCorusTitle, true, "new") },
                )
            }
            items(displayNewUsers.take(2), key = { "new-${it.id}" }) { user ->
                SuggestedUserRow(
                    user = user,
                    subtitle = user.createdAt?.let { context.getString(joinedFormat, DateUtils.relativeTime(context, it)) },
                    isFollowed = viewModel.isFollowed(user.id),
                    onTap = { onNavigateToUser(user.id) },
                    onFollow = { viewModel.toggleFollow(user) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        }

        // ── Curated Music Bots ──
        if (curatedMusicBots.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "bot",
                    title = stringResource(fm.corus.android.R.string.search_section_curated_music_bots),
                    showSeeAll = curatedMusicBots.size > 4,
                    onSeeAll = { onNavigateToBotList("music") },
                )
            }
            item {
                BotGrid(bots = curatedMusicBots.take(4), viewModel = viewModel, onNavigateToUser = onNavigateToUser)
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }
        }

        // ── Curated Film Bots ──
        if (curatedFilmBots.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "bot",
                    title = stringResource(fm.corus.android.R.string.search_section_curated_film_bots),
                    showSeeAll = curatedFilmBots.size > 4,
                    onSeeAll = { onNavigateToBotList("film") },
                )
            }
            item {
                BotGrid(bots = curatedFilmBots.take(4), viewModel = viewModel, onNavigateToUser = onNavigateToUser)
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }
        }

        // ── Invite friends ──
        if (!isSuggestedLoading && !isBotsLoading && !isPopularLoading) {
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
    val cardShape = RoundedCornerShape(CorusSpacing.cornerRadiusLarge)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .clip(cardShape)
            .background(CorusColors.CardBackground)
            .border(0.5.dp, CorusColors.Divider, cardShape)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Contacts,
            contentDescription = null,
            tint = CorusColors.Accent,
            modifier = Modifier.size(36.dp),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        Text(
            stringResource(fm.corus.android.R.string.search_contacts_card_title),
            style = CorusFont.songTitleLarge,
            color = CorusColors.Text,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.xs))
        Text(
            stringResource(fm.corus.android.R.string.search_contacts_card_subtitle),
            style = CorusFont.body,
            color = CorusColors.Secondary,
        )

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        Button(
            onClick = onTap,
            enabled = !isSyncing,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = CorusColors.Accent,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(fm.corus.android.R.string.search_contacts_card_sync), style = CorusFont.buttonSmall)
            }
        }

        TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(horizontal = CorusSpacing.md, vertical = CorusSpacing.xs),
        ) {
            Text(stringResource(fm.corus.android.R.string.search_contacts_card_not_now), style = CorusFont.caption, color = CorusColors.Tertiary)
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

@Composable
private fun SectionHeader(
    icon: String,
    title: String,
    showSeeAll: Boolean = false,
    onSeeAll: () -> Unit = {},
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CorusSpacing.lg, end = CorusSpacing.lg, top = CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconVector = when (icon) {
            "sparkles" -> Icons.Filled.AutoAwesome
            "people" -> Icons.Filled.People
            "trending" -> Icons.Filled.AutoAwesome
            "new" -> Icons.Filled.PersonAdd
            "contacts" -> Icons.Filled.Contacts
            "bot" -> Icons.Filled.SmartToy
            else -> null
        }
        if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = CorusColors.Accent,
                modifier = Modifier.size(16.dp),
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
            TextButton(onClick = onSeeAll) {
                Text(stringResource(fm.corus.android.R.string.search_see_all), style = CorusFont.captionMedium, color = CorusColors.Accent)
            }
        }
    }
}

@Composable
private fun TasteMatchFilterMenu(
    filterUnfollowedMatches: Boolean,
    onSetFilterUnfollowed: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = if (filterUnfollowedMatches) {
                    Icons.Filled.FilterAlt
                } else {
                    Icons.Outlined.FilterAlt
                },
                contentDescription = stringResource(fm.corus.android.R.string.search_cd_filter_taste_matches),
                tint = if (filterUnfollowedMatches) CorusColors.Accent else CorusColors.Tertiary,
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
                leadingIcon = if (!filterUnfollowedMatches) {
                    { Icon(Icons.Filled.Check, contentDescription = null) }
                } else null,
            )
            DropdownMenuItem(
                text = { Text(stringResource(fm.corus.android.R.string.search_filter_unfollowed)) },
                onClick = {
                    onSetFilterUnfollowed(true)
                    expanded = false
                },
                leadingIcon = if (filterUnfollowedMatches) {
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
            // Display name (bold)
            Text(
                text = user.displayName.ifBlank { user.username },
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // @username + flair badge
            UsernameWithFlair(
                username = user.username,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = user.flairStyle,
                isBot = user.isBot,
                showAtPrefix = true,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
            )
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
    onSongTap: (CymbalTrack) -> Unit,
) {
    if (isLoading) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { SectionHeader(icon = "music", title = stringResource(fm.corus.android.R.string.search_section_trending_this_month)) }
            items(5) { SkeletonTrendingSongRow() }
        }
        return
    }
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(80.dp))
                Text("\uD83C\uDFB5", style = CorusFont.songTitleLarge, modifier = Modifier.padding(bottom = CorusSpacing.sm))
                Text(stringResource(fm.corus.android.R.string.search_nothing_trending), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                Text(stringResource(fm.corus.android.R.string.search_post_some_songs), style = CorusFont.body, color = CorusColors.Tertiary)
            }
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { SectionHeader(icon = "music", title = stringResource(fm.corus.android.R.string.search_section_trending_this_month)) }
            itemsIndexed(songs) { index, song ->
                TrendingSongRow(song = song, onClick = { onSongTap(song.track) })
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
        AsyncImage(
            model = song.track.albumArtURL,
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
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
    onFilmTap: (FilmDetailRoute) -> Unit,
) {
    if (isLoading) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { SectionHeader(icon = "film", title = stringResource(fm.corus.android.R.string.search_section_trending_this_month)) }
            items(10) { SkeletonTrendingFilmRow() }
        }
        return
    }
    if (movies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(80.dp))
                Text("\uD83C\uDFAC", style = CorusFont.songTitleLarge, modifier = Modifier.padding(bottom = CorusSpacing.sm))
                Text(stringResource(fm.corus.android.R.string.search_no_trending_films), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                Text(stringResource(fm.corus.android.R.string.search_post_some_films), style = CorusFont.body, color = CorusColors.Tertiary)
            }
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { SectionHeader(icon = "film", title = stringResource(fm.corus.android.R.string.search_section_trending_this_month)) }
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

// ── Bot Grid (2-column layout matching iOS) ──

@Composable
private fun BotGrid(
    bots: List<SuggestedUserMatch>,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
) {
    val rows = bots.chunked(2)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CorusSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        rows.forEach { rowBots ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md)) {
                rowBots.forEach { match ->
                    TasteMatchCard(
                        match = match,
                        isFollowing = viewModel.isFollowed(match.user.id),
                        onUserTap = { onNavigateToUser(match.user.id) },
                        onFollowTap = { viewModel.toggleFollow(match.user) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowBots.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
