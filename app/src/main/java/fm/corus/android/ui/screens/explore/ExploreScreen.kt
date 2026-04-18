package fm.corus.android.ui.screens.explore

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import fm.corus.android.ui.components.FilmSearchResultRow
import fm.corus.android.ui.components.UsernameWithFlair
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import fm.corus.android.ui.components.SkeletonHashtagCard
import fm.corus.android.ui.components.SkeletonSectionHeader
import fm.corus.android.ui.components.SkeletonTasteMatchCard
import fm.corus.android.ui.components.SkeletonTrendingSongRow
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.TasteMatchCard
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

private enum class ExploreTab(val label: String) {
    Users("Users"),
    Songs("Songs"),
    Films("Films"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel = hiltViewModel(),
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (String) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
) {
    val songs by viewModel.trendingSongs.collectAsState()
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val suggestedUsers by viewModel.suggestedUsers.collectAsState()
    val curatedBots by viewModel.curatedBots.collectAsState()
    val musicMatches by viewModel.musicMatches.collectAsState()
    val mutualConnections by viewModel.mutualConnections.collectAsState()
    val popularUsers by viewModel.popularUsers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val userSearchResults by viewModel.userSearchResults.collectAsState()
    val songSearchResults by viewModel.songSearchResults.collectAsState()
    val filmSearchResults by viewModel.filmSearchResults.collectAsState()
    val followedIds by viewModel.followedIds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val nowPlayingState by viewModel.nowPlayingState.collectAsState()
    val previewLoadingTrackId by viewModel.previewLoadingTrackId.collectAsState()

    var selectedTab by remember { mutableStateOf(ExploreTab.Users) }
    var searchQuery by remember { mutableStateOf("") }
    val hasSearchQuery = searchQuery.isNotBlank()

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

    LaunchedEffect(Unit) {
        viewModel.loadTrending()
    }

    // Trigger search when query or tab changes
    LaunchedEffect(searchQuery, selectedTab) {
        viewModel.search(searchQuery, selectedTab.ordinal)
    }

    Column(modifier = Modifier.fillMaxSize().nestedScroll(scrollDismissConnection)) {
        // Header: centered "Search" title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text("Search", style = CorusFont.screenTitle, color = CorusColors.Text)
        }

        // Search bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onClear = {
                searchQuery = ""
                viewModel.clearSearch()
            },
            placeholder = when (selectedTab) {
                ExploreTab.Users -> "Search users"
                ExploreTab.Songs -> "Search songs"
                ExploreTab.Films -> "Search films"
            },
        )

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        // Tab bar: Users / Songs / Films
        ExploreTabBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )

        // Tab content
        if (isLoading && !hasSearchQuery) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CorusColors.Accent)
            }
        } else {
            when (selectedTab) {
                ExploreTab.Users -> {
                    if (hasSearchQuery) {
                        UserSearchResultsList(
                            users = userSearchResults,
                            isSearching = isSearching,
                            followedIds = followedIds,
                            onUserTap = onNavigateToUser,
                            onFollowTap = { userId, isFollowing ->
                                if (isFollowing) viewModel.unfollowUser(userId) else viewModel.followUser(userId)
                            },
                        )
                    } else {
                        UsersTabContent(
                            musicMatches = musicMatches,
                            mutualConnections = mutualConnections,
                            popularUsers = popularUsers,
                            curatedBots = curatedBots,
                            followedIds = followedIds,
                            onUserTap = onNavigateToUser,
                            onFollowTap = { userId, isFollowing ->
                                if (isFollowing) viewModel.unfollowUser(userId) else viewModel.followUser(userId)
                            },
                        )
                    }
                }
                ExploreTab.Songs -> {
                    if (hasSearchQuery) {
                        SongSearchResultsList(
                            tracks = songSearchResults,
                            isSearching = isSearching,
                            onSongTap = onNavigateToSong,
                            nowPlayingTrackId = if (nowPlayingState.isPlaying) nowPlayingState.trackId else null,
                            previewLoadingTrackId = previewLoadingTrackId,
                            onPreviewTap = { viewModel.togglePreview(it) },
                        )
                    } else {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refreshTrending() },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            SongsTabContent(
                                songs = songs,
                                onSongTap = onNavigateToSong,
                            )
                        }
                    }
                }
                ExploreTab.Films -> {
                    if (hasSearchQuery) {
                        FilmSearchResultsList(
                            movies = filmSearchResults,
                            isSearching = isSearching,
                            onFilmTap = onNavigateToFilm,
                        )
                    } else {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refreshTrending() },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            FilmsTabContent(
                                trendingMovies = trendingMovies,
                                onFilmTap = onNavigateToFilm,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Search Bar ──

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg),
        placeholder = {
            Text(placeholder, style = CorusFont.body, color = CorusColors.Tertiary)
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Search",
                tint = CorusColors.Secondary,
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = CorusColors.Secondary,
                        modifier = Modifier.size(18.dp),
                    )
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

// ── Tab Bar ──

@Composable
private fun ExploreTabBar(
    selectedTab: ExploreTab,
    onTabSelected: (ExploreTab) -> Unit,
) {
    val density = LocalDensity.current
    var tabRowWidth by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { tabRowWidth = it.size.width },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ExploreTab.entries.forEach { tab ->
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
                    Text(
                        text = tab.label,
                        style = CorusFont.bodyMedium,
                        color = textColor,
                    )
                }
            }
        }

        // Animated underline indicator
        val tabWidth = with(density) { (tabRowWidth / ExploreTab.entries.size).toDp() }
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

        Spacer(modifier = Modifier.height(CorusSpacing.md))
    }
}

// ── Search Results ──

@Composable
private fun UserSearchResultsList(
    users: List<CymbalUser>,
    isSearching: Boolean,
    followedIds: Set<String>,
    onUserTap: (String) -> Unit,
    onFollowTap: (String, Boolean) -> Unit,
) {
    if (isSearching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CorusColors.Accent)
        }
    } else if (users.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No users found", style = CorusFont.body, color = CorusColors.Secondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
        ) {
            items(users, key = { it.id }) { user ->
                UserSearchRow(
                    user = user,
                    isFollowing = followedIds.contains(user.id),
                    onTap = { onUserTap(user.id) },
                    onFollowTap = { onFollowTap(user.id, followedIds.contains(user.id)) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = CorusColors.Divider,
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
private fun UserSearchRow(
    user: CymbalUser,
    isFollowing: Boolean,
    onTap: () -> Unit,
    onFollowTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = user.avatarURL,
            contentDescription = user.username,
            modifier = Modifier
                .size(CorusSpacing.avatarMedium)
                .clip(CircleShape)
                .background(CorusColors.CardBackground),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            UsernameWithFlair(
                username = user.username,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = user.flairStyle,
                isBot = user.isBot,
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
        Button(
            onClick = onFollowTap,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) CorusColors.Divider else CorusColors.Accent,
                contentColor = if (isFollowing) CorusColors.Text else Color.White,
            ),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
        ) {
            Text(
                text = if (isFollowing) "Following" else "Follow",
                style = CorusFont.captionMedium,
            )
        }
    }
}

@Composable
private fun SongSearchResultsList(
    tracks: List<CymbalTrack>,
    isSearching: Boolean,
    onSongTap: (CymbalTrack) -> Unit,
    nowPlayingTrackId: String? = null,
    previewLoadingTrackId: String? = null,
    onPreviewTap: (CymbalTrack) -> Unit = {},
) {
    if (isSearching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CorusColors.Accent)
        }
    } else if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No songs found", style = CorusFont.body, color = CorusColors.Secondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
        ) {
            items(tracks, key = { it.id }) { track ->
                SongSearchRow(
                    track = track,
                    isPlaying = nowPlayingTrackId == track.id,
                    isLoading = previewLoadingTrackId == track.id,
                    onAlbumArtTap = { onPreviewTap(track) },
                    onClick = { onSongTap(track) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = CorusColors.Divider,
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@Composable
private fun SongSearchRow(
    track: CymbalTrack,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    onAlbumArtTap: () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                .clickable { onAlbumArtTap() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = track.albumArtURL,
                contentDescription = track.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (isPlaying || isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play preview",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = CorusFont.body,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationMs > 0) {
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Text(
                text = track.formattedDuration,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
            )
        }
    }
}

@Composable
private fun FilmSearchResultsList(
    movies: List<CymbalMovie>,
    isSearching: Boolean,
    onFilmTap: (String) -> Unit,
) {
    if (isSearching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CorusColors.Accent)
        }
    } else if (movies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No films found", style = CorusFont.body, color = CorusColors.Secondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
        ) {
            items(movies, key = { it.id }) { movie ->
                FilmSearchResultRow(movie = movie, onClick = { onFilmTap(movie.id) })
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = CorusColors.Divider,
                    thickness = 0.5.dp,
                )
            }
        }
    }
}


// ── Users Tab ──

@Composable
private fun UsersTabContent(
    musicMatches: List<SuggestedUserMatch>,
    mutualConnections: List<SuggestedUserMatch>,
    popularUsers: List<SuggestedUserMatch>,
    curatedBots: List<SuggestedUserMatch>,
    followedIds: Set<String>,
    onUserTap: (String) -> Unit = {},
    onFollowTap: (String, Boolean) -> Unit = { _, _ -> },
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = CorusSpacing.sm, bottom = CorusSpacing.xxxl),
    ) {
        // 1. Music Matches section (horizontal scroll of TasteMatchCards)
        if (musicMatches.isNotEmpty()) {
            item {
                SectionHeader(emoji = "\uD83C\uDFB5", title = "YOUR MUSIC MATCHES")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    items(musicMatches.take(4).size) { index ->
                        val match = musicMatches[index]
                        TasteMatchCard(
                            match = match,
                            isFollowing = followedIds.contains(match.user.id),
                            onUserTap = { onUserTap(match.user.id) },
                            onFollowTap = { onFollowTap(match.user.id, followedIds.contains(match.user.id)) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(CorusSpacing.md))
            }
        }

        // 2. Mutual Connections section (row-based)
        if (mutualConnections.isNotEmpty()) {
            item {
                SectionHeader(emoji = "\uD83E\uDD1D", title = "MUTUAL CONNECTIONS")
            }
            items(mutualConnections.take(3).size) { index ->
                val match = mutualConnections[index]
                SuggestedUserRow(
                    match = match,
                    isFollowing = followedIds.contains(match.user.id),
                    subtitle = match.suggestionReason?.mutualNames?.let { names ->
                        if (names.isNotEmpty()) "Followed by ${names.first()}${if (names.size > 1) " + ${names.size - 1} more" else ""}"
                        else null
                    },
                    onUserTap = { onUserTap(match.user.id) },
                    onFollowTap = { onFollowTap(match.user.id, followedIds.contains(match.user.id)) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.md)) }
        }

        // 3. Popular on Corus (show if no music matches)
        if (popularUsers.isNotEmpty() && musicMatches.isEmpty()) {
            item {
                SectionHeader(emoji = "\uD83D\uDD25", title = "POPULAR ON CORUS")
            }
            items(popularUsers.take(3).size) { index ->
                val match = popularUsers[index]
                SuggestedUserRow(
                    match = match,
                    isFollowing = followedIds.contains(match.user.id),
                    subtitle = "${match.user.followerCount} followers",
                    onUserTap = { onUserTap(match.user.id) },
                    onFollowTap = { onFollowTap(match.user.id, followedIds.contains(match.user.id)) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.md)) }
        }

        // 4. Curated Bots section
        if (curatedBots.isNotEmpty()) {
            item {
                SectionHeader(emoji = "\uD83E\uDD16", title = "CURATED BOTS")
            }
            item {
                SuggestedUsersGrid(
                    users = curatedBots,
                    isBots = true,
                    followedIds = followedIds,
                    onUserTap = onUserTap,
                    onFollowTap = onFollowTap,
                )
            }
        }

        // Empty state
        if (musicMatches.isEmpty() && mutualConnections.isEmpty() && popularUsers.isEmpty() && curatedBots.isEmpty()) {
            item { EmptyHint("No suggestions yet") }
        }
    }
}

@Composable
private fun SuggestedUserRow(
    match: SuggestedUserMatch,
    isFollowing: Boolean,
    subtitle: String? = null,
    onUserTap: () -> Unit = {},
    onFollowTap: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUserTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = match.user.avatarURL, displayName = match.user.displayName, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            UsernameWithFlair(
                username = match.user.username,
                isVerified = match.user.isVerified,
                isClubMember = match.user.isClubMember,
                flairStyle = match.user.flairStyle,
                isBot = match.user.isBot,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = match.user.displayName,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Button(
            onClick = onFollowTap,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) CorusColors.Divider else CorusColors.Accent,
                contentColor = if (isFollowing) CorusColors.Secondary else Color.White,
            ),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            modifier = Modifier.height(32.dp),
        ) {
            Text(if (isFollowing) "Following" else "Follow", style = CorusFont.buttonSmall)
        }
    }
}

@Composable
private fun SuggestedUsersGrid(
    users: List<SuggestedUserMatch>,
    isBots: Boolean,
    followedIds: Set<String>,
    onUserTap: (String) -> Unit = {},
    onFollowTap: (String, Boolean) -> Unit = { _, _ -> },
) {
    val rows = users.chunked(2)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
    ) {
        rows.forEach { rowUsers ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            ) {
                rowUsers.forEach { match ->
                    val isFollowing = followedIds.contains(match.user.id)
                    SuggestedUserCard(
                        match = match,
                        isBot = isBots,
                        isFollowing = isFollowing,
                        modifier = Modifier.weight(1f),
                        onTap = { onUserTap(match.user.id) },
                        onFollowTap = { onFollowTap(match.user.id, isFollowing) },
                    )
                }
                if (rowUsers.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SuggestedUserCard(
    match: SuggestedUserMatch,
    isBot: Boolean,
    isFollowing: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onFollowTap: () -> Unit = {},
) {
    val user = match.user
    val previews = match.matchData?.sharedTrackPreviews ?: emptyList()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .border(
                width = 1.dp,
                color = CorusColors.Divider,
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            )
            .clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 2x2 album art grid
        Column(modifier = Modifier.fillMaxWidth()) {
            for (row in 0..1) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..1) {
                        val index = row * 2 + col
                        val imageUrl = previews.getOrNull(index)?.displayImageURL
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(CorusColors.CardBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (imageUrl != null) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = CorusColors.Tertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (col == 0) {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                    }
                }
                if (row == 0) {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        // Avatar + username row
        Row(
            modifier = Modifier.padding(horizontal = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = user.avatarURL,
                contentDescription = user.username,
                modifier = Modifier
                    .size(CorusSpacing.avatarSmall)
                    .clip(CircleShape)
                    .background(CorusColors.CardBackground),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(CorusSpacing.xs))
            UsernameWithFlair(
                username = user.username,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = user.flairStyle,
                isBot = isBot,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isBot) {
                Spacer(modifier = Modifier.width(CorusSpacing.xs))
                Text(
                    text = "BOT",
                    style = CorusFont.captionMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            CorusColors.Verified,
                            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
                        )
                        .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.xxs),
                )
            }
        }

        // Subtitle for bots
        if (isBot && match.matchData != null) {
            val artistCount = match.matchData.sharedArtists
            if (artistCount > 0) {
                Text(
                    text = "$artistCount artist match${if (artistCount != 1) "es" else ""}",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        // Follow button
        Button(
            onClick = onFollowTap,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.sm),
            shape = RoundedCornerShape(CorusSpacing.cornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowing) CorusColors.Divider else CorusColors.Accent,
                contentColor = if (isFollowing) CorusColors.Text else Color.White,
            ),
            contentPadding = PaddingValues(vertical = CorusSpacing.sm),
        ) {
            Text(
                text = if (isFollowing) "Following" else "Follow",
                style = CorusFont.button,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))
    }
}

// ── Songs Tab ──

@Composable
private fun SongsTabContent(songs: List<TrendingSong>, onSongTap: (CymbalTrack) -> Unit = {}) {
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(80.dp))
                Text(
                    "\uD83C\uDFB5",
                    style = CorusFont.songTitleLarge,
                    modifier = Modifier.padding(bottom = CorusSpacing.sm),
                )
                Text("Nothing trending yet", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                Text("Post some songs to get things going", style = CorusFont.body, color = CorusColors.Tertiary)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl),
        ) {
            item {
                SectionHeader(emoji = "\uD83C\uDFB5", title = "TRENDING SONGS")
            }

            itemsIndexed(songs) { index, song ->
                TrendingSongRow(song = song, onClick = { onSongTap(song.track) })
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = CorusColors.Divider,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingSongRow(song: TrendingSong, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${song.rank}",
            style = CorusFont.stat,
            color = CorusColors.Secondary,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        AsyncImage(
            model = song.track.albumArtURL,
            contentDescription = song.track.name,
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.track.name,
                style = CorusFont.songTitle,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.track.artistName,
                style = CorusFont.artistName,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${song.cymbalCount}",
            style = CorusFont.captionMedium,
            color = CorusColors.Secondary,
        )
    }
}

// ── Films Tab ──

@Composable
private fun FilmsTabContent(trendingMovies: List<TrendingMovie>, onFilmTap: (String) -> Unit = {}) {
    if (trendingMovies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(80.dp))
                Text(
                    "\uD83C\uDFAC",
                    style = CorusFont.songTitleLarge,
                    modifier = Modifier.padding(bottom = CorusSpacing.sm),
                )
                Text("No trending films yet", style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                Text("Post some films to get things going", style = CorusFont.body, color = CorusColors.Tertiary)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.xxxl),
        ) {
            item {
                SectionHeader(emoji = "\uD83C\uDFAC", title = "TRENDING FILMS")
            }

            itemsIndexed(trendingMovies) { index, movie ->
                TrendingMovieRow(movie = movie, onClick = { onFilmTap(movie.movieId) })
                if (index < trendingMovies.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = CorusColors.Divider,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingMovieRow(movie: TrendingMovie, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .heightIn(min = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${movie.rank}",
            style = CorusFont.stat,
            color = CorusColors.Secondary,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        AsyncImage(
            model = movie.posterURL,
            contentDescription = movie.movieTitle,
            modifier = Modifier
                .size(width = CorusSpacing.albumArtSearch, height = 72.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.movieTitle,
                style = CorusFont.songTitle,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs)) {
                Text(
                    text = movie.directorName,
                    style = CorusFont.artistName,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (movie.releaseYear.isNotEmpty()) {
                    Text(
                        text = "(${movie.releaseYear})",
                        style = CorusFont.artistName,
                        color = CorusColors.Tertiary,
                    )
                }
            }
        }
        Text(
            text = "${movie.cymbalCount}",
            style = CorusFont.captionMedium,
            color = CorusColors.Secondary,
        )
    }
}

// ── Shared Components ──

@Composable
private fun SectionHeader(emoji: String, title: String) {
    Row(
        modifier = Modifier.padding(
            horizontal = CorusSpacing.lg,
            vertical = CorusSpacing.sm,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = CorusFont.sectionHeader)
        Spacer(modifier = Modifier.width(CorusSpacing.xs))
        Text(
            text = title,
            style = CorusFont.sectionHeader,
            color = CorusColors.Secondary,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = CorusFont.body,
        color = CorusColors.Tertiary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(CorusSpacing.xxl),
        textAlign = TextAlign.Center,
    )
}
