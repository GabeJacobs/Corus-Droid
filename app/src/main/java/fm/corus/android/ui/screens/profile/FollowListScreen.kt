package fm.corus.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    userId: String,
    isFollowers: Boolean,
    viewModel: FollowListViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val followingStatus by viewModel.followingStatus.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(userId, isFollowers) {
        viewModel.loadFollowList(userId, isFollowers)
    }

    // Pagination: detect when near the bottom of the list
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 3 && hasMore && !isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && searchQuery.isBlank()) {
            viewModel.loadMore()
        }
    }

    val filteredUsers = if (searchQuery.isBlank()) users else {
        users.filter {
            it.username.contains(searchQuery, ignoreCase = true) ||
                    it.displayName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isFollowers) "Followers" else "Following",
                        style = CorusFont.screenTitle,
                        color = CorusColors.Text,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                placeholder = { Text("Search", style = CorusFont.body, color = CorusColors.Tertiary) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = CorusColors.Secondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = CorusColors.Secondary, modifier = Modifier.size(16.dp))
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
                isLoading && users.isEmpty() -> {
                    // Skeleton loading
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
                filteredUsers.isEmpty() && !isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (isFollowers) Icons.Outlined.Group else Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            tint = CorusColors.Tertiary,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No results" else
                                if (isFollowers) "No followers yet" else "Not following anyone yet",
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
                        items(filteredUsers, key = { it.id }) { user ->
                            FollowUserRow(
                                user = user,
                                isFollowing = followingStatus[user.id] ?: false,
                                isCurrentUser = user.id == viewModel.currentUserId,
                                showFollowBack = !isFollowers && viewModel.isFollowedByTarget(user.id),
                                showFollowsYou = isFollowers && (followingStatus[user.id] ?: false),
                                onUserTap = { onNavigateToUser(user.id) },
                                onFollowTap = { viewModel.toggleFollow(user.id) },
                            )
                        }

                        // Loading more indicator
                        if (isLoadingMore) {
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
        UserAvatarView(avatarURL = user.avatarURL, size = (CorusSpacing.avatarMedium + 8.dp))

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
                        text = " \u00B7 Follows you",
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
                isFollowing -> "Following"
                showFollowBack -> "Follow back"
                else -> "Follow"
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
