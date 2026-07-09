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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
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
import fm.corus.android.ui.components.HorizontalTasteMatchesRail
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
// TasteMatchCard is now rendered inside HorizontalTasteMatchesRail, not here.
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
    onNavigateToArtist: (fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit = {},
    onNavigateToAlbum: (fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit = {},
    onNavigateToDirector: (fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit = {},
    onNavigateToTrending: (String) -> Unit = {},
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val userResults by viewModel.userSearchResults.collectAsState()
    val songSearchResults by viewModel.songSearchResults.collectAsState()
    val filmSearchResults by viewModel.filmSearchResults.collectAsState()
    val artistSearchResults by viewModel.artistSearchResults.collectAsState()
    val albumSearchResults by viewModel.albumSearchResults.collectAsState()
    val songsFirst by viewModel.songsFirst.collectAsState()
    val directorSearchResults by viewModel.directorSearchResults.collectAsState()
    // Read once per composition — gates the tab labels, placeholders, and the
    // artist/album/director rows. Flag off = the screen renders as before.
    val artistPagesEnabled = viewModel.artistPagesEnabled
    // Unified search: blended zero-state feed + filter chips instead of tabs.
    // Read once per composition, same as artistPagesEnabled.
    val unifiedSearchEnabled = viewModel.unifiedSearchEnabled
    val unifiedFilter by viewModel.unifiedFilter.collectAsState()
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
    val belowTasteMatchThreshold by viewModel.belowTasteMatchThreshold.collectAsState()
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
    val currentTabIsEmpty = if (unifiedSearchEnabled) {
        // Unified: "empty" = nothing rendered by the active filter.
        when (unifiedFilter) {
            UnifiedSearchFilter.USERS -> userResults.isEmpty()
            UnifiedSearchFilter.MUSIC -> songSearchResults.isEmpty() && artistSearchResults.isEmpty()
            UnifiedSearchFilter.FILM -> filmSearchResults.isEmpty()
            UnifiedSearchFilter.HASHTAGS -> hashtagSearchResults.isEmpty()
            UnifiedSearchFilter.ALL ->
                userResults.isEmpty() && songSearchResults.isEmpty() &&
                    artistSearchResults.isEmpty() && filmSearchResults.isEmpty() &&
                    hashtagSearchResults.isEmpty()
        }
    } else when (activeTab) {
        SearchTab.USERS -> userResults.isEmpty()
        SearchTab.SONGS -> songSearchResults.isEmpty()
        SearchTab.FILMS -> filmSearchResults.isEmpty()
        SearchTab.HASHTAGS -> hashtagSearchResults.isEmpty()
    }
    val showSearchOfflineRetry = hasSearchQuery && !isSearching && searchHasError && currentTabIsEmpty
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // Unified search has no Users tab; recents overlay on focus everywhere.
    val showRecentOverlay = isSearchFocused && !hasSearchQuery &&
        (unifiedSearchEnabled || activeTab == SearchTab.USERS)

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
    // Unified search lists (blended zero state + blended All results) are
    // separate lists, so scroll-to-top needs their own hoisted states.
    val unifiedZeroListState = rememberLazyListState()
    val unifiedAllListState = rememberLazyListState()

    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > lastScrollTrigger) {
            if (unifiedSearchEnabled) {
                when {
                    !hasSearchQuery -> unifiedZeroListState.animateScrollToItem(0)
                    unifiedFilter == UnifiedSearchFilter.ALL -> unifiedAllListState.animateScrollToItem(0)
                    unifiedFilter == UnifiedSearchFilter.USERS -> usersListState.animateScrollToItem(0)
                    unifiedFilter == UnifiedSearchFilter.MUSIC -> songsListState.animateScrollToItem(0)
                    unifiedFilter == UnifiedSearchFilter.FILM -> filmsListState.animateScrollToItem(0)
                    else -> hashtagsListState.animateScrollToItem(0)
                }
            } else when (activeTab) {
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

    // Default to "All" (followed + unfollowed): the paginated taste-matches rail
    // pages the full strength-ranked list, so the toggle is always available and
    // "Unfollowed" auto-pages until unfollowed matches surface. Matches iOS.
    var filterUnfollowedMatches by rememberSaveable { mutableStateOf(false) }
    val allFollowedIds = remember(followingIds, localFollowedIds) { followingIds + localFollowedIds }

    // Popular-on-Corus filter. When "Unfollowed" is on, the rail excludes
    // already-followed accounts at the *fetch* level (folded into excludeIds
    // below), the way iOS does — the backend over-fetches and skips them, so a
    // viewer who follows most popular accounts still gets a full page of
    // unfollowed ones instead of an empty/flashing rail.
    //
    // We subtract session-local follows so a card you just followed via the rail
    // isn't excluded and doesn't vanish under your finger. The set is *live* (not
    // a one-time snapshot): followingIds arrives in layers (DataStore → Firestore
    // → reconcile), and a frozen snapshot used to leak follows that landed after
    // the first emission back into the "Unfollowed" list. A change here refetches
    // the rail against the corrected exclusion.
    // Default to "All" (show everyone popular); the toggle narrows to unfollowed.
    // Matches iOS default flip.
    var filterUnfollowedPopular by rememberSaveable { mutableStateOf(false) }
    val onSetFilterUnfollowedPopular: (Boolean) -> Unit = { enabled ->
        filterUnfollowedPopular = enabled
    }
    val popularRailFilterFollowedIds =
        if (filterUnfollowedPopular) followingIds - localFollowedIds else emptySet()
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
            placeholder = if (unifiedSearchEnabled) {
                // No pre-picked vertical: one placeholder names them all.
                stringResource(fm.corus.android.R.string.search_placeholder_unified)
            } else when (activeTab) {
                // Artist pages on: songs/films placeholders widen to cover the
                // new artist/album/director rows ("Music" / "Film" tabs).
                SearchTab.SONGS -> stringResource(
                    if (artistPagesEnabled) fm.corus.android.R.string.search_placeholder_music
                    else fm.corus.android.R.string.search_placeholder_songs
                )
                SearchTab.FILMS -> stringResource(
                    if (artistPagesEnabled) fm.corus.android.R.string.search_placeholder_film
                    else fm.corus.android.R.string.search_placeholder_films
                )
                SearchTab.USERS -> stringResource(fm.corus.android.R.string.search_placeholder_users)
                SearchTab.HASHTAGS -> stringResource(fm.corus.android.R.string.search_placeholder_hashtags)
            },
        )

        Spacer(modifier = Modifier.height(CorusSpacing.xs))

        // Unified search has no pre-picked vertical: no tabs at all on the
        // zero state, filter chips only once a query is typed.
        if (unifiedSearchEnabled) {
            if (hasSearchQuery) {
                UnifiedFilterChipRow(
                    selected = unifiedFilter,
                    artistPagesEnabled = artistPagesEnabled,
                    onSelect = { viewModel.setUnifiedFilter(it) },
                )
            }
        } else {
            // Tab bar
            SearchTabBar(
                selectedTab = activeTab,
                onTabSelected = { viewModel.setActiveTab(it.ordinal) },
                artistPagesEnabled = artistPagesEnabled,
            )
        }

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
                } else if (unifiedSearchEnabled) {
                    if (hasSearchQuery) {
                        when (unifiedFilter) {
                            UnifiedSearchFilter.ALL -> UnifiedAllResults(
                                listState = unifiedAllListState,
                                userResults = userResults,
                                songResults = songSearchResults,
                                artistResults = artistSearchResults,
                                filmResults = filmSearchResults,
                                hashtagResults = hashtagSearchResults,
                                isSearching = isSearching,
                                artistPagesEnabled = artistPagesEnabled,
                                followedHashtagNames = followedHashtagNames,
                                viewModel = viewModel,
                                nowPlaying = viewModel.nowPlayingManager,
                                onSelectFilter = { viewModel.setUnifiedFilter(it) },
                                onNavigateToUser = { userId ->
                                    val user = userResults.find { it.id == userId }
                                    if (user != null) viewModel.onUserSelected(user)
                                    onNavigateToUser(userId)
                                },
                                onNavigateToSong = onNavigateToSong,
                                onNavigateToFilm = onNavigateToFilm,
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToHashtag = onNavigateToHashtag,
                            )
                            UnifiedSearchFilter.USERS -> UserSearchResults(
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
                            UnifiedSearchFilter.MUSIC -> SongSearchResultsList(
                                listState = songsListState,
                                tracks = songSearchResults,
                                isSearching = isSearching,
                                onSongTap = onNavigateToSong,
                                artists = artistSearchResults,
                                albums = albumSearchResults,
                                songsFirst = songsFirst,
                                onArtistTap = onNavigateToArtist,
                                onAlbumTap = onNavigateToAlbum,
                            )
                            UnifiedSearchFilter.FILM -> FilmSearchResultsList(
                                listState = filmsListState,
                                movies = filmSearchResults,
                                isSearching = isSearching,
                                onFilmTap = onNavigateToFilm,
                                directors = directorSearchResults,
                                onDirectorTap = onNavigateToDirector,
                            )
                            UnifiedSearchFilter.HASHTAGS -> HashtagSearchResultsList(
                                listState = hashtagsListState,
                                hashtags = hashtagSearchResults,
                                isSearching = isSearching,
                                followedHashtagNames = followedHashtagNames,
                                onHashtagTap = { tag -> onNavigateToHashtag(tag.name) },
                                onToggleFollow = { tag -> viewModel.toggleHashtagFollow(tag) },
                            )
                        }
                    } else {
                        UnifiedZeroStateContent(
                            listState = unifiedZeroListState,
                            musicMatchUsers = musicMatchUsers,
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
                            belowTasteMatchThreshold = belowTasteMatchThreshold,
                            trendingSongs = trendingSongs,
                            isTrendingLoading = isTrendingLoading,
                            trendingMovies = trendingMovies,
                            isTrendingMoviesLoading = isTrendingMoviesLoading,
                            trendingHashtags = trendingHashtags,
                            isTrendingHashtagsLoading = isTrendingHashtagsLoading,
                            followedHashtagNames = followedHashtagNames,
                            viewModel = viewModel,
                            onNavigateToUser = onNavigateToUser,
                            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
                            onNavigateToContactFriends = onNavigateToContactFriends,
                            onNavigateToSong = onNavigateToSong,
                            onNavigateToFilm = onNavigateToFilm,
                            onNavigateToHashtag = onNavigateToHashtag,
                            onNavigateToTrending = onNavigateToTrending,
                        )
                    }
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
                                belowTasteMatchThreshold = belowTasteMatchThreshold,
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
                                artists = artistSearchResults,
                                albums = albumSearchResults,
                                songsFirst = songsFirst,
                                onArtistTap = onNavigateToArtist,
                                onAlbumTap = onNavigateToAlbum,
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
                                directors = directorSearchResults,
                                onDirectorTap = onNavigateToDirector,
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
    /** Artist pages on: "Songs"→"Music" and "Films"→"Film" — the tabs now
     *  cover artists/albums and directors too. Label-only; the enum (and every
     *  ordinal-based dispatch) is untouched. */
    artistPagesEnabled: Boolean = false,
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
                    val labelRes = when {
                        artistPagesEnabled && tab == SearchTab.SONGS -> fm.corus.android.R.string.search_tab_music
                        artistPagesEnabled && tab == SearchTab.FILMS -> fm.corus.android.R.string.search_tab_film
                        else -> tab.labelRes
                    }
                    Text(text = stringResource(labelRes), style = CorusFont.bodyMedium, color = textColor)
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
    belowTasteMatchThreshold: Boolean,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit,
    onNavigateToContactFriends: () -> Unit,
) {
    val context = LocalContext.current
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

    // Assembled from the shared LazyListScope section extensions below so the
    // unified zero state (UnifiedZeroStateContent) can reuse the exact same
    // sections in web's order. THIS assembly preserves today's classic order.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = CorusSpacing.xxs),
    ) {
        contactsSections(
            contactsSyncStatus = contactsSyncStatus,
            isSyncingContacts = isSyncingContacts,
            contactMatches = contactMatches,
            showNoContactMatches = showNoContactMatches,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToContactFriends = onNavigateToContactFriends,
            onRequestContacts = {
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
        )

        tasteMatchesSections(
            musicMatchUsers = musicMatchUsers,
            isSuggestedLoading = isSuggestedLoading,
            isTasteMatchPolling = isTasteMatchPolling,
            tasteMatchLoadFailed = tasteMatchLoadFailed,
            belowTasteMatchThreshold = belowTasteMatchThreshold,
            allFollowedIds = allFollowedIds,
            filterUnfollowedMatches = filterUnfollowedMatches,
            onSetFilterUnfollowed = onSetFilterUnfollowed,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )

        popularSection(
            railExcludeIds = railExcludeIds,
            popularRailFilterFollowedIds = popularRailFilterFollowedIds,
            allFollowedIds = allFollowedIds,
            filterUnfollowedPopular = filterUnfollowedPopular,
            showUnfollowedPopularToggle = showUnfollowedPopularToggle,
            onSetFilterUnfollowedPopular = onSetFilterUnfollowedPopular,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )

        clubMembersSection(
            clubMembers = clubMembers,
            allFollowedIds = allFollowedIds,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )

        mutualConnectionsSection(
            mutualConnectionUsers = mutualConnectionUsers,
            allFollowedIds = allFollowedIds,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )

        newOnCorusSection(
            newUsers = newUsers,
            mutualConnectionUsers = mutualConnectionUsers,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )

        inviteFriendsSection(isSuggestedLoading = isSuggestedLoading)
    }
}

// ── Zero-state section extensions ──
// Each discovery section as a LazyListScope extension so BOTH assemblies can
// share them: SuggestedUsersContent (classic Users tab, today's order) and
// UnifiedZeroStateContent (unified_search_enabled, web's order with trending
// strips interleaved). Blocks moved verbatim from SuggestedUsersContent.

private fun LazyListScope.contactsSections(
    contactsSyncStatus: String,
    isSyncingContacts: Boolean,
    contactMatches: List<CymbalUser>,
    showNoContactMatches: Boolean,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToContactFriends: () -> Unit,
    onRequestContacts: () -> Unit,
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
                    onTap = onRequestContacts,
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
}

private fun LazyListScope.tasteMatchesSections(
    musicMatchUsers: List<SuggestedUserMatch>,
    isSuggestedLoading: Boolean,
    isTasteMatchPolling: Boolean,
    tasteMatchLoadFailed: Boolean,
    belowTasteMatchThreshold: Boolean,
    allFollowedIds: Set<String>,
    filterUnfollowedMatches: Boolean,
    onSetFilterUnfollowed: (Boolean) -> Unit,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit,
) {
    // ── Taste Matches section ──
    // Always present the section: real cards when we have matches, a skeleton
    // while we're still loading/polling, and a short explainer whenever a user
    // has no taste matches yet so the slot reads as "coming soon" rather than
    // missing. A viewer below the post threshold skips the skeleton entirely
    // (the ViewModel also skips their fetch and poll), so they reach the
    // explainer immediately instead of shimmering through a scan that can't
    // produce matches.
    if (musicMatchUsers.isNotEmpty()) {
        item {
            // Paginated rail backed by getTasteMatchesPage — pages the FULL
            // strength-ranked list (not the capped top-15 preview), so the
            // filter toggle is always available and "Unfollowed" can reach
            // matches ranked below the top-15. The rail owns its own data,
            // pagination, and filter; the parent only gates section
            // visibility on musicMatchUsers (from getSuggestedUsers), the
            // same way iOS keeps its parent gate.
            val tasteFilterCd = stringResource(fm.corus.android.R.string.search_cd_filter_taste_matches)
            val tasteMatchesTitle = stringResource(fm.corus.android.R.string.search_taste_matches_title)
            HorizontalTasteMatchesRail(
                followedIds = allFollowedIds,
                filterUnfollowed = filterUnfollowedMatches,
                onClearFilter = { onSetFilterUnfollowed(false) },
                onUserTap = { match ->
                    // Keep music_match_tapped (carries similarity_score, unique to this section)
                    viewModel.logMusicMatchTapped(match.user.id, match.matchData?.similarityScore ?: 0.0)
                    // Also fire the unified event so cross-section comparisons work.
                    viewModel.logSearchSectionUserTapped(SearchSection.TasteMatches, match.user.id)
                    onNavigateToUser(match.user.id)
                },
                onFollowTap = { user -> viewModel.toggleFollow(user, SearchSection.TasteMatches) },
                onSeeAll = {
                    viewModel.logSearchSectionSeeAllTapped(SearchSection.TasteMatches)
                    onNavigateToSuggestedUsers(tasteMatchesTitle, false, "tasteMatches")
                },
                // Always available when the viewer follows anyone (mirrors
                // iOS showFilterToggle: !currentUserFollowingIds.isEmpty),
                // not gated on a fragile "mix in the top-15" condition.
                trailingAction = if (allFollowedIds.isNotEmpty()) {
                    {
                        UnfollowedUsersFilterMenu(
                            filterUnfollowed = filterUnfollowedMatches,
                            onSetFilterUnfollowed = onSetFilterUnfollowed,
                            contentDescription = tasteFilterCd,
                        )
                    }
                } else null,
            )
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
        }
    } else if ((isSuggestedLoading || isTasteMatchPolling) && !belowTasteMatchThreshold) {
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
}

private fun LazyListScope.popularSection(
    railExcludeIds: Set<String>,
    popularRailFilterFollowedIds: Set<String>,
    allFollowedIds: Set<String>,
    filterUnfollowedPopular: Boolean,
    showUnfollowedPopularToggle: Boolean,
    onSetFilterUnfollowedPopular: (Boolean) -> Unit,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit,
) {
    // ── Popular on Corus — paginated horizontal rail of real users ──
    item {
        val popularFilterCd = stringResource(fm.corus.android.R.string.search_cd_filter_popular_users)
        val popularOnCorusTitle = stringResource(fm.corus.android.R.string.search_popular_title)
        HorizontalPopularUsersRail(
            // Fold the followed-id set into excludeIds so the query never
            // returns already-followed accounts under the "Unfollowed"
            // filter — same as iOS. Empty when the filter is off, so "All"
            // shows everyone.
            excludeIds = railExcludeIds + popularRailFilterFollowedIds,
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
            filterUnfollowed = filterUnfollowedPopular,
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
}

private fun LazyListScope.clubMembersSection(
    clubMembers: List<CymbalUser>,
    allFollowedIds: Set<String>,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit,
) {
    // ── Corus Club Members ──
    // Horizontal rail of TasteMatchCards ordered by initial sign-up
    // (most recent first). Mirrors web; placement matches iOS.
    if (clubMembers.isNotEmpty()) {
        item {
            val clubMembersTitle = stringResource(fm.corus.android.R.string.search_club_members_title)
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
            val context = LocalContext.current
            ClubMembersCardRail(
                users = clubMembers,
                followedIds = allFollowedIds,
                onUserTap = { user ->
                    viewModel.logSearchSectionUserTapped(SearchSection.ClubMembers, user.id)
                    onNavigateToUser(user.id)
                },
                onFollowTap = { user -> viewModel.toggleFollow(user, SearchSection.ClubMembers) },
                memberSinceLabel = { date ->
                    context.getString(fm.corus.android.R.string.suggested_users_member_since_format, DateUtils.relativeTime(context, date))
                },
            )
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
        }
    }
}

private fun LazyListScope.mutualConnectionsSection(
    mutualConnectionUsers: List<SuggestedUserMatch>,
    allFollowedIds: Set<String>,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit,
) {
    // ── Mutual Connections section ──
    // Horizontal rail of TasteMatchCards (2x2 album-art) — matches iOS
    // SearchView.mutualConnectionsSection / MutualConnectionsCardGrid.
    if (mutualConnectionUsers.isNotEmpty()) {
        item {
            val mutualConnectionsTitle = stringResource(fm.corus.android.R.string.search_mutual_connections_title)
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
}

private fun LazyListScope.newOnCorusSection(
    newUsers: List<CymbalUser>,
    mutualConnectionUsers: List<SuggestedUserMatch>,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit,
) {
    // ── New on Corus ──
    val seenNewIds = buildSet { mutualConnectionUsers.forEach { add(it.user.id) } }
    val displayNewUsers = newUsers.filter {
        !seenNewIds.contains(it.id) && !viewModel.isFollowed(it.id)
    }
    if (displayNewUsers.isNotEmpty()) {
        item {
            val newOnCorusTitle = stringResource(fm.corus.android.R.string.search_new_title)
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
            val context = LocalContext.current
            SuggestedUserRow(
                user = user,
                subtitle = user.createdAt?.let { context.getString(fm.corus.android.R.string.suggested_users_joined_format, DateUtils.relativeTime(context, it)) },
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
}

private fun LazyListScope.inviteFriendsSection(isSuggestedLoading: Boolean) {
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

// ── Unified search (unified_search_enabled) ──

/** Compact trending-songs strip on the blended zero state: header + top 4
 *  rows; See all pushes the full trending list (TrendingListScreen). Hidden
 *  when loaded-empty. */
private fun LazyListScope.compactTrendingSongsSection(
    songs: List<TrendingSong>,
    isLoading: Boolean,
    nowPlaying: fm.corus.android.domain.NowPlayingManager,
    viewModel: SearchViewModel,
    onSongTap: (CymbalTrack) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (!isLoading && songs.isEmpty()) return
    item {
        SectionHeader(
            icon = "music",
            title = stringResource(fm.corus.android.R.string.search_trending_songs_title).uppercase(),
            showSeeAll = true,
            onSeeAll = {
                viewModel.logSearchSectionSeeAllTapped(SearchSection.TrendingSongs)
                onSeeAll()
            },
        )
    }
    // Horizontal slider of art tiles (web's compact strip, but scrollable to
    // reach the full loaded list). No rank/count numbers here — the See-all
    // list keeps them.
    item {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            if (isLoading) {
                items(4) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CorusColors.CardBackground),
                    )
                }
            } else {
                items(songs, key = { "ts-${it.track.id}" }) { song ->
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clickable { onSongTap(song.track) },
                    ) {
                        // Large art first: the small thumb is sized for list
                        // rows and upscales blurry at tile size.
                        ShimmerAsyncImage(
                            model = song.track.albumArtLargeURL ?: song.track.albumArtURL,
                            contentDescription = song.track.name,
                            modifier = Modifier.size(120.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text(song.track.name, style = CorusFont.captionMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.track.artistName, style = CorusFont.caption, color = CorusColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
}

/** Compact trending-films strip: header + top 4 rows + See all. */
private fun LazyListScope.compactTrendingFilmsSection(
    movies: List<TrendingMovie>,
    isLoading: Boolean,
    viewModel: SearchViewModel,
    onFilmTap: (FilmDetailRoute) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (!isLoading && movies.isEmpty()) return
    item {
        SectionHeader(
            icon = "film",
            title = stringResource(fm.corus.android.R.string.search_trending_films_title).uppercase(),
            showSeeAll = true,
            onSeeAll = {
                viewModel.logSearchSectionSeeAllTapped(SearchSection.TrendingFilms)
                onSeeAll()
            },
        )
    }
    // Horizontal poster slider — same reasoning as the songs strip.
    item {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            if (isLoading) {
                items(5) {
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(135.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CorusColors.CardBackground),
                    )
                }
            } else {
                items(movies, key = { "tf-${it.movieId}" }) { movie ->
                    Column(
                        modifier = Modifier
                            .width(90.dp)
                            .clickable {
                                onFilmTap(FilmDetailRoute(
                                    movieId = movie.movieId,
                                    movieTitle = movie.movieTitle,
                                    directorName = movie.directorName.ifBlank { null },
                                    releaseYear = movie.releaseYear.ifBlank { null },
                                    posterURL = movie.posterURL,
                                    posterLargeURL = movie.posterLargeURL,
                                    trailerURL = movie.trailerURL,
                                ))
                            },
                    ) {
                        // Large poster first — same upscaling reasoning as
                        // the song tiles.
                        ShimmerAsyncImage(
                            model = movie.posterLargeURL ?: movie.posterURL,
                            contentDescription = movie.movieTitle,
                            modifier = Modifier
                                .width(90.dp)
                                .height(135.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text(movie.movieTitle, style = CorusFont.captionMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
}

/** Compact trending-hashtags strip: header + top 3 rows + See all. */
private fun LazyListScope.compactTrendingHashtagsSection(
    hashtags: List<TrendingHashtag>,
    isLoading: Boolean,
    followedHashtagNames: Set<String>,
    viewModel: SearchViewModel,
    onHashtagTap: (TrendingHashtag) -> Unit,
    onToggleFollow: (TrendingHashtag) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (!isLoading && hashtags.isEmpty()) return
    item {
        SectionHeader(
            icon = "hashtag",
            title = stringResource(fm.corus.android.R.string.search_trending_hashtags_title).uppercase(),
            showSeeAll = true,
            onSeeAll = {
                viewModel.logSearchSectionSeeAllTapped(SearchSection.TrendingHashtags)
                onSeeAll()
            },
        )
    }
    if (isLoading) {
        items(3) { SkeletonSearchSongRow() }
    } else {
        val visible = hashtags.take(3)
        itemsIndexed(visible, key = { _, tag -> "th-${tag.id}" }) { index, tag ->
            val postNoun = stringResource(fm.corus.android.R.string.post_noun)
            val postNounPlural = stringResource(fm.corus.android.R.string.post_noun_plural)
            val followerWord = stringResource(fm.corus.android.R.string.hashtag_followers)
            val noun = if (tag.cymbalCount == 1) postNoun else postNounPlural
            val subtitle = if (tag.followerCount > 0) {
                val fNoun = if (tag.followerCount == 1) followerWord.removeSuffix("s") else followerWord
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
            if (index < visible.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(start = 88.dp), color = CorusColors.Divider, thickness = 0.5.dp)
            }
        }
    }
    item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
}

/**
 * The unified blended zero state: every discovery section on one scroll in
 * web's order — contacts, taste matches, trending songs, trending films,
 * popular, mutual connections, club members, new on Corus, trending hashtags,
 * invite friends. People sections keep their existing rails; trending
 * verticals appear as compact strips whose See-all opens the full list.
 */
@Composable
private fun UnifiedZeroStateContent(
    listState: LazyListState,
    musicMatchUsers: List<SuggestedUserMatch>,
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
    belowTasteMatchThreshold: Boolean,
    trendingSongs: List<TrendingSong>,
    isTrendingLoading: Boolean,
    trendingMovies: List<TrendingMovie>,
    isTrendingMoviesLoading: Boolean,
    trendingHashtags: List<TrendingHashtag>,
    isTrendingHashtagsLoading: Boolean,
    followedHashtagNames: Set<String>,
    viewModel: SearchViewModel,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSuggestedUsers: (title: String, useRowLayout: Boolean, source: String) -> Unit,
    onNavigateToContactFriends: () -> Unit,
    onNavigateToSong: (CymbalTrack) -> Unit,
    onNavigateToFilm: (FilmDetailRoute) -> Unit,
    onNavigateToHashtag: (String) -> Unit,
    onNavigateToTrending: (String) -> Unit,
) {
    val context = LocalContext.current
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
        contactsSections(
            contactsSyncStatus = contactsSyncStatus,
            isSyncingContacts = isSyncingContacts,
            contactMatches = contactMatches,
            showNoContactMatches = showNoContactMatches,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToContactFriends = onNavigateToContactFriends,
            onRequestContacts = {
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
        )
        tasteMatchesSections(
            musicMatchUsers = musicMatchUsers,
            isSuggestedLoading = isSuggestedLoading,
            isTasteMatchPolling = isTasteMatchPolling,
            tasteMatchLoadFailed = tasteMatchLoadFailed,
            belowTasteMatchThreshold = belowTasteMatchThreshold,
            allFollowedIds = allFollowedIds,
            filterUnfollowedMatches = filterUnfollowedMatches,
            onSetFilterUnfollowed = onSetFilterUnfollowed,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )
        compactTrendingSongsSection(
            songs = trendingSongs,
            isLoading = isTrendingLoading,
            nowPlaying = viewModel.nowPlayingManager,
            viewModel = viewModel,
            onSongTap = onNavigateToSong,
            onSeeAll = { onNavigateToTrending("songs") },
        )
        compactTrendingFilmsSection(
            movies = trendingMovies,
            isLoading = isTrendingMoviesLoading,
            viewModel = viewModel,
            onFilmTap = onNavigateToFilm,
            onSeeAll = { onNavigateToTrending("films") },
        )
        popularSection(
            railExcludeIds = railExcludeIds,
            popularRailFilterFollowedIds = popularRailFilterFollowedIds,
            allFollowedIds = allFollowedIds,
            filterUnfollowedPopular = filterUnfollowedPopular,
            showUnfollowedPopularToggle = showUnfollowedPopularToggle,
            onSetFilterUnfollowedPopular = onSetFilterUnfollowedPopular,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )
        // Web order: mutual connections above club members (classic Android
        // order has them flipped; unified follows web).
        mutualConnectionsSection(
            mutualConnectionUsers = mutualConnectionUsers,
            allFollowedIds = allFollowedIds,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )
        clubMembersSection(
            clubMembers = clubMembers,
            allFollowedIds = allFollowedIds,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )
        compactTrendingHashtagsSection(
            hashtags = trendingHashtags,
            isLoading = isTrendingHashtagsLoading,
            followedHashtagNames = followedHashtagNames,
            viewModel = viewModel,
            onHashtagTap = { tag -> onNavigateToHashtag(tag.name) },
            onToggleFollow = { tag -> viewModel.toggleHashtagFollowByName(tag.name) },
            onSeeAll = { onNavigateToTrending("hashtags") },
        )
        newOnCorusSection(
            newUsers = newUsers,
            mutualConnectionUsers = mutualConnectionUsers,
            viewModel = viewModel,
            onNavigateToUser = onNavigateToUser,
            onNavigateToSuggestedUsers = onNavigateToSuggestedUsers,
        )
        inviteFriendsSection(isSuggestedLoading = isSuggestedLoading)
    }
}

/** Pill chip row shown once a query is typed. Active chip = accent blue,
 *  matching web. */
@Composable
private fun UnifiedFilterChipRow(
    selected: UnifiedSearchFilter,
    artistPagesEnabled: Boolean,
    onSelect: (UnifiedSearchFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
    ) {
        items(UnifiedSearchFilter.entries.toList(), key = { it.value }) { filter ->
            val isActive = filter == selected
            val label = when (filter) {
                UnifiedSearchFilter.ALL -> stringResource(fm.corus.android.R.string.search_filter_all_chip)
                UnifiedSearchFilter.USERS -> stringResource(fm.corus.android.R.string.search_tab_users)
                UnifiedSearchFilter.MUSIC -> stringResource(
                    if (artistPagesEnabled) fm.corus.android.R.string.search_tab_music
                    else fm.corus.android.R.string.search_tab_songs
                )
                UnifiedSearchFilter.FILM -> stringResource(
                    if (artistPagesEnabled) fm.corus.android.R.string.search_tab_film
                    else fm.corus.android.R.string.search_tab_films
                )
                UnifiedSearchFilter.HASHTAGS -> stringResource(fm.corus.android.R.string.search_tab_hashtags)
            }
            Button(
                onClick = { if (!isActive) onSelect(filter) },
                shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) CorusColors.Accent else CorusColors.Background,
                    contentColor = if (isActive) Color.White else CorusColors.Secondary,
                ),
                border = if (isActive) null else androidx.compose.foundation.BorderStroke(1.dp, CorusColors.Divider),
                contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
                modifier = Modifier.height(32.dp),
            ) {
                Text(label, style = CorusFont.buttonSmall)
            }
        }
    }
}

/**
 * The blended "All" results: Users(5) → Music(2 artists + 4 songs) →
 * Film(4) → Hashtags(3), fixed order per web. Sections with results render;
 * music/film also render (as skeletons) while the fan-out is in flight —
 * they're the slow verticals, so a skeleton beats a reflow when they land.
 * Section See-alls narrow the active chip to that vertical.
 */
@Composable
private fun UnifiedAllResults(
    listState: LazyListState,
    userResults: List<CymbalUser>,
    songResults: List<CymbalTrack>,
    artistResults: List<fm.corus.android.data.model.ArtistSummary>,
    filmResults: List<CymbalMovie>,
    hashtagResults: List<CymbalHashtag>,
    isSearching: Boolean,
    artistPagesEnabled: Boolean,
    followedHashtagNames: Set<String>,
    viewModel: SearchViewModel,
    nowPlaying: fm.corus.android.domain.NowPlayingManager,
    onSelectFilter: (UnifiedSearchFilter) -> Unit,
    onNavigateToUser: (String) -> Unit,
    onNavigateToSong: (CymbalTrack) -> Unit,
    onNavigateToFilm: (FilmDetailRoute) -> Unit,
    onNavigateToArtist: (fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit,
    onNavigateToHashtag: (String) -> Unit,
) {
    val noMatches = !isSearching &&
        userResults.isEmpty() && songResults.isEmpty() && artistResults.isEmpty() &&
        filmResults.isEmpty() && hashtagResults.isEmpty()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = CorusSpacing.sm),
    ) {
        // ── Users ──
        if (userResults.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "people",
                    title = stringResource(fm.corus.android.R.string.search_tab_users).uppercase(),
                    showSeeAll = true,
                    onSeeAll = { onSelectFilter(UnifiedSearchFilter.USERS) },
                )
            }
            items(userResults.take(5), key = { "u-${it.id}" }) { user ->
                SuggestedUserRow(
                    user = user,
                    isFollowed = viewModel.isFollowed(user.id),
                    onTap = { onNavigateToUser(user.id) },
                    onFollow = { viewModel.toggleFollow(user) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        }

        // ── Music ──
        val musicLoading = isSearching && songResults.isEmpty() && artistResults.isEmpty()
        if (musicLoading || songResults.isNotEmpty() || (artistPagesEnabled && artistResults.isNotEmpty())) {
            item {
                SectionHeader(
                    icon = "music",
                    title = stringResource(
                        if (artistPagesEnabled) fm.corus.android.R.string.search_tab_music
                        else fm.corus.android.R.string.search_tab_songs
                    ).uppercase(),
                    showSeeAll = true,
                    onSeeAll = { onSelectFilter(UnifiedSearchFilter.MUSIC) },
                )
            }
            if (musicLoading) {
                items(4) { SkeletonSearchSongRow() }
            } else {
                // Artist rows lead; albums are omitted in the blended view
                // (web parity) — the Music chip has the full list.
                if (artistPagesEnabled) {
                    items(artistResults.take(2), key = { "a-${it.id}" }) { artist ->
                        ArtistSearchRow(artist = artist, onClick = {
                            onNavigateToArtist(
                                fm.corus.android.ui.navigation.ArtistPageRoute(
                                    artistId = artist.id,
                                    name = artist.name,
                                    imageUrl = artist.imageUrl,
                                )
                            )
                        })
                    }
                }
                val visibleSongs = songResults.take(4)
                itemsIndexed(visibleSongs, key = { _, track -> "s-${track.id}" }) { index, track ->
                    SongSearchRow(track = track, onClick = { onNavigateToSong(track) })
                    if (index < visibleSongs.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        }

        // ── Film ──
        val filmLoading = isSearching && filmResults.isEmpty()
        if (filmLoading || filmResults.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "film",
                    title = stringResource(
                        if (artistPagesEnabled) fm.corus.android.R.string.search_tab_film
                        else fm.corus.android.R.string.search_tab_films
                    ).uppercase(),
                    showSeeAll = true,
                    onSeeAll = { onSelectFilter(UnifiedSearchFilter.FILM) },
                )
            }
            if (filmLoading) {
                items(4) { SkeletonTrendingFilmRow() }
            } else {
                val visibleMovies = filmResults.take(4)
                itemsIndexed(visibleMovies, key = { _, movie -> "f-${movie.id}" }) { index, movie ->
                    // Director rows are omitted in the blended view (web
                    // parity) — the Film chip has them.
                    FilmSearchResultRow(movie = movie, onClick = {
                        onNavigateToFilm(FilmDetailRoute(
                            movieId = movie.id,
                            movieTitle = movie.title,
                            directorName = movie.directorName.ifBlank { null },
                            releaseYear = movie.year.ifBlank { null },
                            posterURL = movie.posterURL,
                            posterLargeURL = movie.posterLargeURL,
                            trailerURL = movie.trailerURL,
                        ))
                    })
                    if (index < visibleMovies.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.sm)) }
        }

        // ── Hashtags ──
        if (hashtagResults.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = "hashtag",
                    title = stringResource(fm.corus.android.R.string.search_tab_hashtags).uppercase(),
                    showSeeAll = true,
                    onSeeAll = { onSelectFilter(UnifiedSearchFilter.HASHTAGS) },
                )
            }
            val visibleTags = hashtagResults.take(3)
            itemsIndexed(visibleTags, key = { _, tag -> "h-${tag.name}" }) { index, tag ->
                HashtagRow(
                    name = tag.name,
                    fallbackCount = tag.cymbalCount,
                    isFollowing = followedHashtagNames.contains(tag.name.lowercase()),
                    onClick = { onNavigateToHashtag(tag.name) },
                    onToggleFollow = { viewModel.toggleHashtagFollow(tag) },
                )
                if (index < visibleTags.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 88.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }

        // ── No matches (every vertical settled empty) ──
        if (noMatches) {
            item {
                Text(
                    stringResource(fm.corus.android.R.string.search_no_matches),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CorusSpacing.xxl),
                )
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
            "music" -> Icons.Filled.MusicNote
            "film" -> Icons.Filled.Movie
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
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
internal fun TrendingSongsContent(
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
internal fun TrendingFilmsContent(
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
                        movieReleaseDate = movie.movieReleaseDate,
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
    /** Artist/album rows (artist_pages_enabled) rendered above the songs.
     *  Always empty while the flag is off. */
    artists: List<fm.corus.android.data.model.ArtistSummary> = emptyList(),
    albums: List<fm.corus.android.data.model.AlbumSearchSummary> = emptyList(),
    /** True when the query is a song-title search: album rows then render BELOW
     *  the songs so a direct song hit leads instead of being buried by same-
     *  titled albums (Spotify's song-first ordering). False keeps albums on top
     *  for album-intent queries. */
    songsFirst: Boolean = false,
    onArtistTap: (fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit = {},
    onAlbumTap: (fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit = {},
) {
    val hasCatalogRows = artists.isNotEmpty() || albums.isNotEmpty()
    // Albums lead only for album-intent queries; on a song-title query they drop
    // below the songs (see the trailing album block).
    val albumsAboveSongs = albums.isNotEmpty() && !songsFirst
    if (isSearching && tracks.isEmpty() && !hasCatalogRows) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            items(8) { index ->
                SkeletonSearchSongRow()
                if (index < 7) {
                    HorizontalDivider(modifier = Modifier.padding(start = 84.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    } else if (tracks.isEmpty() && !hasCatalogRows) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(fm.corus.android.R.string.search_no_songs_found), style = CorusFont.body, color = CorusColors.Secondary)
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            // Cap artist rows at 2 on mobile — a narrow screen fills up fast, so
            // keep the top 2 (backend returns up to 3 in relevance order).
            items(artists.take(2), key = { "artist-${it.id}" }) { artist ->
                ArtistSearchRow(
                    artist = artist,
                    onClick = {
                        onArtistTap(
                            fm.corus.android.ui.navigation.ArtistPageRoute(
                                artistId = artist.id,
                                name = artist.name,
                                imageUrl = artist.imageUrl,
                            )
                        )
                    },
                )
            }
            if (albumsAboveSongs) {
                items(albums, key = { "album-${it.id}" }) { album ->
                    AlbumSearchRow(
                        album = album,
                        onClick = {
                            onAlbumTap(
                                fm.corus.android.ui.navigation.AlbumPageRoute(
                                    albumId = album.id,
                                    title = album.title,
                                    artist = album.artistName,
                                    coverUrl = album.coverUrl,
                                    year = album.year,
                                )
                            )
                        },
                    )
                }
            }
            // Divider between the leading catalog rows and the songs. When
            // songsFirst moved albums down, only artists lead — so gate on
            // whatever actually renders above the songs.
            if ((artists.isNotEmpty() || albumsAboveSongs) && tracks.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = CorusSpacing.xs),
                        color = CorusColors.Divider,
                        thickness = 0.5.dp,
                    )
                }
            }
            itemsIndexed(tracks) { index, track ->
                SongSearchRow(track = track, onClick = { onSongTap(track) })
                if (index < tracks.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 84.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
            // Song-title query: same-titled album rows render after the songs so
            // the direct hit leads but the album stays reachable.
            if (songsFirst && albums.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = CorusSpacing.xs),
                        color = CorusColors.Divider,
                        thickness = 0.5.dp,
                    )
                }
                items(albums, key = { "album-below-${it.id}" }) { album ->
                    AlbumSearchRow(
                        album = album,
                        onClick = {
                            onAlbumTap(
                                fm.corus.android.ui.navigation.AlbumPageRoute(
                                    albumId = album.id,
                                    title = album.title,
                                    artist = album.artistName,
                                    coverUrl = album.coverUrl,
                                    year = album.year,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

// ── Artist / Album / Director search rows (artist_pages_enabled) ──

@Composable
private fun ArtistSearchRow(
    artist: fm.corus.android.data.model.ArtistSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch)
                .clip(CircleShape)
                .background(CorusColors.CardBackground),
        ) {
            if (artist.imageUrl != null) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = CorusColors.Tertiary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Text(artist.name, style = CorusFont.bodyMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(fm.corus.android.R.string.destination_artist_label),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AlbumSearchRow(
    album: fm.corus.android.data.model.AlbumSearchSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                .background(CorusColors.CardBackground),
        ) {
            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Text(album.title, style = CorusFont.bodyMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(fm.corus.android.R.string.destination_album_artist_format, album.artistName),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DirectorSearchRow(
    director: fm.corus.android.data.model.ArtistSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch)
                .clip(CircleShape)
                .background(CorusColors.CardBackground),
        ) {
            if (director.imageUrl != null) {
                AsyncImage(
                    model = director.imageUrl,
                    contentDescription = director.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = CorusColors.Tertiary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
        ) {
            Text(director.name, style = CorusFont.bodyMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(fm.corus.android.R.string.destination_director_label),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(16.dp),
        )
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
    /** Director rows (artist_pages_enabled) rendered above the films. Always
     *  empty while the flag is off. */
    directors: List<fm.corus.android.data.model.ArtistSummary> = emptyList(),
    onDirectorTap: (fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit = {},
) {
    if (isSearching && movies.isEmpty() && directors.isEmpty()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            items(8) { index ->
                SkeletonFilmRow()
                if (index < 7) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }
        }
    } else if (movies.isEmpty() && directors.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(fm.corus.android.R.string.search_no_films_found), style = CorusFont.body, color = CorusColors.Secondary)
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = CorusSpacing.sm)) {
            items(directors, key = { "director-${it.id}" }) { director ->
                DirectorSearchRow(
                    director = director,
                    onClick = {
                        onDirectorTap(
                            fm.corus.android.ui.navigation.DirectorPageRoute(
                                directorId = director.id,
                                name = director.name,
                                imageUrl = director.imageUrl,
                            )
                        )
                    },
                )
            }
            if (directors.isNotEmpty() && movies.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = CorusSpacing.xs),
                        color = CorusColors.Divider,
                        thickness = 0.5.dp,
                    )
                }
            }
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
internal fun TrendingHashtagsContent(
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
