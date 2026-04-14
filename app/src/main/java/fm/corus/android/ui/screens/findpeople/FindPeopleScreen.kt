package fm.corus.android.ui.screens.findpeople

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.ui.components.SkeletonFilmRow
import fm.corus.android.ui.components.SkeletonTrendingSongRow
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

enum class SearchTab(val label: String) {
    USERS("Users"),
    SONGS("Songs"),
    FILMS("Films"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindPeopleScreen(
    viewModel: FindPeopleViewModel = hiltViewModel(),
    scrollToTopTrigger: Int = 0,
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (String, String?) -> Unit = { _, _ -> },
    onNavigateToFilm: (String) -> Unit = {},
    onNavigateToBotList: (String?) -> Unit = {},
    onNavigateToSuggestedUsers: () -> Unit = {},
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
    val recentSearches by viewModel.recentSearches.collectAsState()
    val contactMatches by viewModel.contactMatches.collectAsState()
    val isSyncingContacts by viewModel.isSyncingContacts.collectAsState()
    val contactsSyncStatus by viewModel.contactsSyncStatus.collectAsState()
    val popularUsers by viewModel.popularUsers.collectAsState()
    val isPopularLoading by viewModel.isPopularLoading.collectAsState()

    var activeTab by remember { mutableStateOf(SearchTab.USERS) }
    val hasSearchQuery = searchQuery.isNotBlank()
    var isSearchFocused by remember { mutableStateOf(false) }

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

    val mutualConnectionUsers = remember(suggestedMatches, followingIds, localFollowedIds) {
        val allFollowed = followingIds + localFollowedIds
        suggestedMatches
            .filter { !allFollowed.contains(it.user.id) }
            .filter { it.matchData?.hasSimilarityData != true && (it.user.artistsInCommonCount ?: 0) == 0 }
            .filter { it.user.cymbalCount > 0 }
            .filter { it.suggestionReason?.mutualNames?.isNotEmpty() == true }
    }

    Column(modifier = Modifier.fillMaxSize().background(CorusColors.Background)) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text("Search", style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        // Search bar
        SearchBarSection(
            query = searchQuery,
            onQueryChange = { viewModel.onSearchQueryChange(it) },
            onClear = {
                viewModel.onSearchQueryChange("")
                viewModel.clearSearch()
                isSearchFocused = false
            },
            onFocusChanged = { isSearchFocused = it },
            placeholder = when (activeTab) {
                SearchTab.SONGS -> "Search for a song"
                SearchTab.FILMS -> "Search for a film"
                SearchTab.USERS -> "Search by username"
            },
        )

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        // Tab bar
        FindPeopleTabBar(
            selectedTab = activeTab,
            onTabSelected = { activeTab = it },
        )

        // Content
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
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
                            recentSearches = recentSearches,
                            isSearchFocused = isSearchFocused,
                            musicMatchUsers = musicMatchUsers,
                            mutualConnectionUsers = mutualConnectionUsers,
                            curatedMusicBots = curatedMusicBots,
                            curatedFilmBots = curatedFilmBots,
                            contactMatches = contactMatches,
                            contactsSyncStatus = contactsSyncStatus,
                            isSyncingContacts = isSyncingContacts,
                            popularUsers = popularUsers,
                            isPopularLoading = isPopularLoading,
                            isSuggestedLoading = isSuggestedLoading,
                            isBotsLoading = isBotsLoading,
                            viewModel = viewModel,
                            onNavigateToUser = onNavigateToUser,
                            onNavigateToBotList = onNavigateToBotList,
                            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
                            onNavigateToContactFriends = onNavigateToContactFriends,
                            onRecentSearchTap = { query ->
                                viewModel.populateSearchFromRecent(query)
                            },
                            onClearRecentSearches = { viewModel.clearRecentSearches() },
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
    }
}

@Composable
private fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    placeholder: String,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

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
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = CorusColors.Secondary)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = CorusColors.Secondary, modifier = Modifier.size(18.dp))
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
private fun FindPeopleTabBar(
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
                    Text(text = tab.label, style = CorusFont.bodyMedium, color = textColor)
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
private fun SuggestedUsersContent(
    listState: LazyListState = rememberLazyListState(),
    recentSearches: List<String>,
    isSearchFocused: Boolean = false,
    musicMatchUsers: List<SuggestedUserMatch>,
    mutualConnectionUsers: List<SuggestedUserMatch>,
    curatedMusicBots: List<SuggestedUserMatch>,
    curatedFilmBots: List<SuggestedUserMatch>,
    contactMatches: List<CymbalUser>,
    contactsSyncStatus: String,
    isSyncingContacts: Boolean,
    popularUsers: List<CymbalUser>,
    isPopularLoading: Boolean,
    isSuggestedLoading: Boolean,
    isBotsLoading: Boolean,
    viewModel: FindPeopleViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToBotList: (String?) -> Unit,
    onNavigateToSuggestedUsers: () -> Unit,
    onNavigateToContactFriends: () -> Unit,
    onRecentSearchTap: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
) {
    val context = LocalContext.current

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.syncContacts(context.contentResolver)
        }
    }

    if (isSuggestedLoading && isBotsLoading) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(6) {
                SkeletonUserRow()
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = CorusSpacing.lg),
    ) {
        // ── Recent Searches (only visible when search field is focused, matching iOS) ──
        if (isSearchFocused && recentSearches.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("RECENT", style = CorusFont.sectionHeader, color = CorusColors.Secondary)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearRecentSearches) {
                        Text("Clear", style = CorusFont.captionMedium, color = CorusColors.Accent)
                    }
                }
            }
            items(recentSearches, key = { "recent-$it" }) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRecentSearchTap(query) }
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = CorusColors.Tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.md))
                    Text(
                        text = query,
                        style = CorusFont.body,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(CorusSpacing.md))
            }
        }

        // ── Find Friends from Contacts ──
        if (contactsSyncStatus == "notAsked") {
            item {
                FindFriendsFromContactsCard(
                    isSyncing = isSyncingContacts,
                    onTap = {
                        contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                )
                Spacer(modifier = Modifier.height(CorusSpacing.lg))
            }
        }

        // ── Friends on Corus (contact matches) ──
        if (contactMatches.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "contacts",
                    title = "FRIENDS ON CORUS",
                    showSeeAll = contactMatches.size > 3,
                    onSeeAll = onNavigateToContactFriends,
                )
            }
            items(contactMatches.take(3), key = { "contact-${it.id}" }) { user ->
                SuggestedUserRow(
                    user = user,
                    subtitle = "From your contacts",
                    isFollowed = viewModel.isFollowed(user.id),
                    onTap = { onNavigateToUser(user.id) },
                    onFollow = { viewModel.toggleFollow(user) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.lg)) }
        }

        // ── Taste Matches section ──
        if (musicMatchUsers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "sparkles",
                    title = "TASTE MATCHES",
                    showSeeAll = musicMatchUsers.size > 4,
                    onSeeAll = onNavigateToSuggestedUsers,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    musicMatchUsers.take(2).forEach { match ->
                        TasteMatchCard(
                            match = match,
                            isFollowing = viewModel.isFollowed(match.user.id),
                            onUserTap = { onNavigateToUser(match.user.id) },
                            onFollowTap = { viewModel.toggleFollow(match.user) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (musicMatchUsers.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(CorusSpacing.lg))
            }
        }

        // ── Mutual Connections section ──
        if (mutualConnectionUsers.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "people",
                    title = "MUTUAL CONNECTIONS",
                    showSeeAll = mutualConnectionUsers.size > 2,
                    onSeeAll = onNavigateToSuggestedUsers,
                )
            }
            items(mutualConnectionUsers.take(2)) { match ->
                SuggestedUserRow(
                    user = match.user,
                    subtitle = formatMutualFollowersText(match.suggestionReason?.mutualNames),
                    isFollowed = viewModel.isFollowed(match.user.id),
                    onTap = { onNavigateToUser(match.user.id) },
                    onFollow = { viewModel.toggleFollow(match.user) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.lg)) }
        }

        // ── Popular on Corus ──
        if (popularUsers.isNotEmpty()) {
            item {
                SectionHeader(icon = "trending", title = "POPULAR ON CORUS")
            }
            items(popularUsers.take(3), key = { "popular-${it.id}" }) { user ->
                SuggestedUserRow(
                    user = user,
                    subtitle = "${user.followerCount} followers",
                    isFollowed = viewModel.isFollowed(user.id),
                    onTap = { onNavigateToUser(user.id) },
                    onFollow = { viewModel.toggleFollow(user) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.lg)) }
        } else if (isPopularLoading) {
            item {
                SectionHeader(icon = "trending", title = "POPULAR ON CORUS")
            }
            items(3) { SkeletonUserRow() }
            item { Spacer(modifier = Modifier.height(CorusSpacing.lg)) }
        }

        // ── Curated Music Bots ──
        if (curatedMusicBots.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "bot",
                    title = "CURATED MUSIC BOTS",
                    showSeeAll = curatedMusicBots.size > 4,
                    onSeeAll = { onNavigateToBotList("music") },
                )
            }
            item {
                BotGrid(bots = curatedMusicBots.take(4), viewModel = viewModel, onNavigateToUser = onNavigateToUser)
                Spacer(modifier = Modifier.height(CorusSpacing.lg))
            }
        }

        // ── Curated Film Bots ──
        if (curatedFilmBots.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "bot",
                    title = "CURATED FILM BOTS",
                    showSeeAll = curatedFilmBots.size > 4,
                    onSeeAll = { onNavigateToBotList("film") },
                )
            }
            item {
                BotGrid(bots = curatedFilmBots.take(4), viewModel = viewModel, onNavigateToUser = onNavigateToUser)
                Spacer(modifier = Modifier.height(CorusSpacing.lg))
            }
        }

        // ── Invite friends ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CorusSpacing.xxxl, horizontal = CorusSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("know someone with good taste?", style = CorusFont.songTitleLarge, color = CorusColors.Text)
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                Button(
                    onClick = { /* share intent */ },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.xl, vertical = CorusSpacing.sm),
                ) {
                    Text("invite friends", style = CorusFont.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun FindFriendsFromContactsCard(
    isSyncing: Boolean,
    onTap: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg)
            .clickable(enabled = !isSyncing, onClick = onTap),
        shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
        colors = CardDefaults.cardColors(containerColor = CorusColors.CardBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CorusSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Find Friends from Contacts",
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                )
                Spacer(modifier = Modifier.height(CorusSpacing.xs))
                Text(
                    "Discover who you know on Corus",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
            }
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = CorusColors.Accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Button(
                    onClick = onTap,
                    shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CorusColors.Accent,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Sync", style = CorusFont.buttonSmall)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: String,
    title: String,
    showSeeAll: Boolean = false,
    onSeeAll: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconVector = when (icon) {
            "sparkles" -> Icons.Filled.AutoAwesome
            "people" -> Icons.Filled.People
            "trending" -> Icons.Filled.AutoAwesome
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
        Spacer(modifier = Modifier.weight(1f))
        if (showSeeAll) {
            TextButton(onClick = onSeeAll) {
                Text("See All", style = CorusFont.captionMedium, color = CorusColors.Accent)
            }
        }
    }
}

@Composable
private fun UserSearchResults(
    listState: LazyListState = rememberLazyListState(),
    results: List<CymbalUser>,
    isSearching: Boolean,
    viewModel: FindPeopleViewModel,
    onNavigateToUser: (String) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        if (isSearching && results.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(CorusSpacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CorusColors.Accent)
                }
            }
        } else if (results.isEmpty()) {
            item {
                Text(
                    "No users found",
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
        UserAvatarView(avatarURL = user.avatarURL, size = 44.dp)
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
            // @username + verified badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${user.username}",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(CorusSpacing.xxs))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Verified",
                        tint = CorusColors.Verified,
                        modifier = Modifier.size(12.dp),
                    )
                }
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
            Text(if (isFollowed) "Following" else "Follow", style = CorusFont.buttonSmall)
        }
    }
}

@Composable
private fun TrendingSongsContent(
    listState: LazyListState = rememberLazyListState(),
    songs: List<TrendingSong>,
    isLoading: Boolean,
    onSongTap: (String, String?) -> Unit,
) {
    if (isLoading) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { SectionHeader(icon = "music", title = "TRENDING THIS MONTH") }
            items(5) { SkeletonTrendingSongRow() }
        }
        return
    }
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(80.dp))
                Text("\uD83C\uDFB5", style = CorusFont.songTitleLarge, modifier = Modifier.padding(bottom = CorusSpacing.sm))
                Text("Nothing trending yet", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                Text("Post some songs to get things going", style = CorusFont.body, color = CorusColors.Tertiary)
            }
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { SectionHeader(icon = "music", title = "TRENDING THIS MONTH") }
            itemsIndexed(songs) { index, song ->
                TrendingSongRow(song = song, onClick = { onSongTap(song.track.id, song.track.albumArtURL) })
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
    onFilmTap: (String) -> Unit,
) {
    if (isLoading) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { SectionHeader(icon = "film", title = "TRENDING THIS MONTH") }
            items(5) { SkeletonFilmRow() }
        }
        return
    }
    if (movies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(80.dp))
                Text("\uD83C\uDFAC", style = CorusFont.songTitleLarge, modifier = Modifier.padding(bottom = CorusSpacing.sm))
                Text("No trending films yet", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                Text("Post some films to get things going", style = CorusFont.body, color = CorusColors.Tertiary)
            }
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl)) {
            item { SectionHeader(icon = "film", title = "TRENDING THIS MONTH") }
            itemsIndexed(movies) { index, movie ->
                TrendingFilmRow(movie = movie, onClick = { onFilmTap(movie.movieId) })
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
        AsyncImage(
            model = movie.posterURL,
            contentDescription = null,
            modifier = Modifier
                .width(33.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(4.dp)),
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
    onSongTap: (String, String?) -> Unit,
) {
    if (isSearching && tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CorusColors.Accent)
        }
    } else if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No songs found", style = CorusFont.body, color = CorusColors.Secondary)
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            itemsIndexed(tracks) { index, track ->
                SongSearchRow(track = track, onClick = { onSongTap(track.id, track.albumArtURL) })
                if (index < tracks.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.albumArtURL,
            contentDescription = track.name,
            modifier = Modifier.size(CorusSpacing.albumArtSearch).clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.name, style = CorusFont.songTitle, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, style = CorusFont.artistName, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Film Search Results ──

@Composable
private fun FilmSearchResultsList(
    listState: LazyListState = rememberLazyListState(),
    movies: List<CymbalMovie>,
    isSearching: Boolean,
    onFilmTap: (String) -> Unit,
) {
    if (isSearching && movies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CorusColors.Accent)
        }
    } else if (movies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No films found", style = CorusFont.body, color = CorusColors.Secondary)
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            itemsIndexed(movies) { index, movie ->
                FilmSearchRow(movie = movie, onClick = { onFilmTap(movie.id) })
                if (index < movies.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun FilmSearchRow(movie: CymbalMovie, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = movie.posterURL,
            contentDescription = movie.title,
            modifier = Modifier
                .size(width = CorusSpacing.albumArtSearch, height = 72.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(movie.title, style = CorusFont.songTitle, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs)) {
                if (movie.directorName.isNotBlank()) {
                    Text(movie.directorName, style = CorusFont.artistName, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (movie.year.isNotBlank()) {
                    Text("(${movie.year})", style = CorusFont.artistName, color = CorusColors.Tertiary)
                }
            }
        }
    }
}

// ── Helpers ──

private fun formatMutualFollowersText(names: List<String>?): String? {
    if (names.isNullOrEmpty()) return null
    return when (names.size) {
        1 -> "Followed by @${names[0]}"
        2 -> "Followed by @${names[0]} and @${names[1]}"
        else -> {
            val others = names.size - 2
            "Followed by @${names[0]}, @${names[1]}, and $others ${if (others == 1) "other" else "others"}"
        }
    }
}

// ── Bot Grid (2-column layout matching iOS) ──

@Composable
private fun BotGrid(
    bots: List<SuggestedUserMatch>,
    viewModel: FindPeopleViewModel,
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
