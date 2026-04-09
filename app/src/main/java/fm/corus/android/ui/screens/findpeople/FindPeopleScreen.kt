package fm.corus.android.ui.screens.findpeople

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.SuggestedUserMatch
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
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
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (String) -> Unit = {},
    onNavigateToFilm: (String) -> Unit = {},
    onNavigateToBotList: (String?) -> Unit = {},
    onNavigateToSuggestedUsers: () -> Unit = {},
    onNavigateToContactFriends: () -> Unit = {},
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val userResults by viewModel.userSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
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

    LaunchedEffect(Unit) {
        viewModel.loadInitialData()
    }

    // Derived suggestion categories
    val musicMatchUsers = remember(suggestedMatches) {
        suggestedMatches
            .filter { it.matchData?.hasSimilarityData == true || (it.user.artistsInCommonCount ?: 0) > 0 }
            .sortedByDescending { it.matchData?.similarityScore ?: 0.0 }
    }

    val mutualConnectionUsers = remember(suggestedMatches) {
        suggestedMatches
            .filter { it.matchData?.hasSimilarityData != true && (it.user.artistsInCommonCount ?: 0) == 0 }
            .filter { it.user.cymbalCount > 0 }
            .filter { it.suggestionReason?.mutualNames?.isNotEmpty() == true }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        SearchBarSection(
            query = searchQuery,
            onQueryChange = { viewModel.onSearchQueryChange(it) },
            placeholder = when (activeTab) {
                SearchTab.SONGS -> "Search for a song"
                SearchTab.FILMS -> "Search for a film"
                SearchTab.USERS -> "Search by username"
            },
        )

        // Tab picker
        TabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = Color.White,
            contentColor = CorusColors.Text,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    color = CorusColors.Accent,
                )
            },
        ) {
            SearchTab.entries.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = {
                        Text(
                            tab.label,
                            style = CorusFont.bodyMedium,
                            color = if (activeTab == tab) CorusColors.Text else CorusColors.Tertiary,
                        )
                    },
                )
            }
        }

        // Content
        when (activeTab) {
            SearchTab.USERS -> {
                if (searchQuery.isBlank()) {
                    SuggestedUsersContent(
                        recentSearches = recentSearches,
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
                } else {
                    UserSearchResults(
                        results = userResults,
                        isSearching = isSearching,
                        viewModel = viewModel,
                        onNavigateToUser = { userId ->
                            val user = userResults.find { it.id == userId }
                            if (user != null) viewModel.onUserSelected(user)
                            onNavigateToUser(userId)
                        },
                    )
                }
            }
            SearchTab.SONGS -> {
                if (searchQuery.isBlank()) {
                    TrendingSongsContent(
                        songs = trendingSongs,
                        isLoading = isTrendingLoading,
                        onSongTap = onNavigateToSong,
                    )
                } else {
                    // Song search results would go here (uses Spotify API)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = CorusColors.Accent)
                        }
                    }
                }
            }
            SearchTab.FILMS -> {
                if (searchQuery.isBlank()) {
                    TrendingFilmsContent(
                        movies = trendingMovies,
                        isLoading = isTrendingMoviesLoading,
                        onFilmTap = onNavigateToFilm,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = CorusColors.Accent)
                        }
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
    placeholder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = CorusColors.Tertiary,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder, style = CorusFont.body, color = CorusColors.Tertiary) },
            modifier = Modifier.weight(1f),
            textStyle = CorusFont.body,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = CorusColors.Tertiary)
            }
        }
    }
    HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
}

@Composable
private fun SuggestedUsersContent(
    recentSearches: List<String>,
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(6) {
                SkeletonUserRow()
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = CorusSpacing.lg),
    ) {
        // ── Recent Searches ──
        if (recentSearches.isNotEmpty()) {
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
                    showSeeAll = mutualConnectionUsers.size > 3,
                    onSeeAll = onNavigateToSuggestedUsers,
                )
            }
            items(mutualConnectionUsers.take(3)) { match ->
                SuggestedUserRow(
                    user = match.user,
                    subtitle = match.suggestionReason?.mutualNames?.let {
                        if (it.isNotEmpty()) "Followed by ${it.first()}" else null
                    },
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
            items(popularUsers.take(5), key = { "popular-${it.id}" }) { user ->
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    curatedMusicBots.take(2).forEach { match ->
                        TasteMatchCard(
                            match = match,
                            isFollowing = viewModel.isFollowed(match.user.id),
                            onUserTap = { onNavigateToUser(match.user.id) },
                            onFollowTap = { viewModel.toggleFollow(match.user) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (curatedMusicBots.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    curatedFilmBots.take(2).forEach { match ->
                        TasteMatchCard(
                            match = match,
                            isFollowing = viewModel.isFollowed(match.user.id),
                            onUserTap = { onNavigateToUser(match.user.id) },
                            onFollowTap = { viewModel.toggleFollow(match.user) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (curatedFilmBots.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
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
    results: List<CymbalUser>,
    isSearching: Boolean,
    viewModel: FindPeopleViewModel,
    onNavigateToUser: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
        UserAvatarView(avatarURL = user.avatarURL, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName.ifBlank { user.username },
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(CorusSpacing.xs))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Verified",
                        tint = CorusColors.Verified,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = subtitle ?: "@${user.username}",
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onFollow,
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFollowed) CorusColors.Divider else CorusColors.Accent,
                contentColor = if (isFollowed) CorusColors.Secondary else Color.White,
            ),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
            modifier = Modifier.height(30.dp),
        ) {
            Text(if (isFollowed) "Following" else "Follow", style = CorusFont.buttonSmall)
        }
    }
}

@Composable
private fun TrendingSongsContent(
    songs: List<TrendingSong>,
    isLoading: Boolean,
    onSongTap: (String) -> Unit,
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(60.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CorusColors.Accent)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(icon = "music", title = "TRENDING THIS WEEK")
        }
        items(songs, key = { it.id }) { song ->
            TrendingSongRow(song = song, onClick = { onSongTap(song.track.id) })
            if (song.id != songs.lastOrNull()?.id) {
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
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
    movies: List<TrendingMovie>,
    isLoading: Boolean,
    onFilmTap: (String) -> Unit,
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(60.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CorusColors.Accent)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader(icon = "film", title = "TRENDING FILMS")
        }
        items(movies, key = { it.id }) { movie ->
            TrendingFilmRow(movie = movie, onClick = { onFilmTap(movie.movieId) })
            if (movie.id != movies.lastOrNull()?.id) {
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = CorusColors.Divider, thickness = 0.5.dp)
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
