package fm.corus.android.ui.screens.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikesBottomSheet(
    postId: String,
    onDismiss: () -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        BackHandler { onDismiss() }
        LikesSheetContent(
            postId = postId,
            onDismiss = onDismiss,
            onNavigateToUser = onNavigateToUser,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LikesSheetContent(
    postId: String,
    viewModel: LikesViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    val likers by viewModel.likers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val followingIds by viewModel.followingIds.collectAsState()
    val followerIds by viewModel.followerIds.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(postId) {
        viewModel.loadLikers(postId)
    }

    val filteredLikers = if (searchQuery.isBlank()) likers else {
        val query = searchQuery.lowercase()
        likers.filter {
            it.username.lowercase().contains(query) ||
                    it.displayName.lowercase().contains(query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 400.dp),
    ) {
        // Title
        Text(
            text = "Likes",
            style = CorusFont.screenTitle,
            color = CorusColors.Text,
            modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        )

        // Search bar — only show when 10+ likers (matching iOS)
        if (likers.size >= 10) TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            placeholder = { Text("Search", style = CorusFont.body, color = CorusColors.Tertiary) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = CorusColors.Tertiary, modifier = Modifier.size(15.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = CorusColors.Tertiary, modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(CorusSpacing.cornerRadius),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CorusColors.CardBackground,
                unfocusedContainerColor = CorusColors.CardBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = CorusColors.Accent,
            ),
            textStyle = CorusFont.body.copy(color = CorusColors.Text),
        )

        HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)

        // Single LazyColumn for all states. The bottom spacer guarantees the content
        // always overflows, which is required for ModalBottomSheet drag-to-expand to work.
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading && likers.isEmpty()) {
                items(10) { SkeletonLikerRow() }
            } else if (filteredLikers.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = CorusColors.Tertiary,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = if (searchQuery.isEmpty()) "No likes yet" else "No results",
                                style = CorusFont.bodyMedium,
                                color = CorusColors.Secondary,
                            )
                        }
                    }
                }
            } else {
                items(filteredLikers, key = { it.id }) { user ->
                    val isSelf = user.id == viewModel.currentUserId
                    val isFollowing = followingIds.contains(user.id)
                    val isFollower = followerIds.contains(user.id)

                    LikerRow(
                        user = user,
                        isFollowing = isFollowing,
                        isFollower = isFollower,
                        isSelf = isSelf,
                        onUserTap = {
                            onNavigateToUser(user.id)
                            onDismiss()
                        },
                        onFollowTap = { viewModel.toggleFollow(user.id) },
                    )

                    if (user.id == filteredLikers.lastOrNull()?.id && searchQuery.isEmpty()) {
                        LaunchedEffect(user.id) {
                            viewModel.loadNextPageIfNeeded()
                        }
                    }
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = CorusSpacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = CorusColors.Accent,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }

            // Invisible spacer that forces content to always overflow the visible area.
            // Without this, drag-to-expand won't work when there are few items.
            item(key = "expand_spacer") {
                Spacer(modifier = Modifier.fillParentMaxHeight())
            }
        }
    }
}

@Composable
private fun LikerRow(
    user: CymbalUser,
    isFollowing: Boolean,
    isFollower: Boolean,
    isSelf: Boolean,
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
        UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = CorusSpacing.avatarMedium + 8.dp)

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsernameWithFlair(
                    username = user.username,
                    isVerified = user.isVerified,
                    isClubMember = user.isClubMember,
                    flairStyle = user.flairStyle,
                    isBot = user.isBot,
                )
            }
            Text(
                text = user.displayName,
                style = CorusFont.body,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!isSelf) {
            Spacer(modifier = Modifier.width(CorusSpacing.sm))

            val buttonText = when {
                isFollowing -> "Following"
                isFollower -> "Follow back"
                else -> "Follow"
            }

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
                Text(buttonText, style = CorusFont.buttonSmall)
            }
        }
    }
}

@Composable
private fun SkeletonLikerRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(CorusSpacing.avatarMedium + 8.dp)
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
