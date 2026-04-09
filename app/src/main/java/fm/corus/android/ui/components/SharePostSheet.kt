package fm.corus.android.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun SharePostSheet(
    post: CymbalPost,
    recentContacts: List<CymbalUser>,
    searchResults: List<CymbalUser>,
    isSearching: Boolean,
    isLoadingContacts: Boolean,
    instagramShareEnabled: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSendToUser: (userId: String, message: String) -> Unit,
    onRepost: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<CymbalUser?>(null) }
    var messageText by remember { mutableStateOf("") }
    var showCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f),
    ) {
        // Drag indicator
        Box(
            modifier = Modifier
                .padding(top = CorusSpacing.sm)
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CorusColors.Tertiary.copy(alpha = 0.4f)),
        )

        // Search bar
        TextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                onSearchQueryChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            placeholder = { Text("Search", style = CorusFont.body, color = CorusColors.Tertiary) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CorusColors.Tertiary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        onSearchQueryChange("")
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = CorusColors.Tertiary)
                    }
                }
            },
            singleLine = true,
            textStyle = CorusFont.body,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CorusColors.CardBackground,
                unfocusedContainerColor = CorusColors.CardBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        // Selected user input bar
        if (selectedUser != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatarView(avatarURL = selectedUser!!.avatarURL, size = CorusSpacing.avatarMedium)
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Text(selectedUser!!.displayName, style = CorusFont.bodyMedium, color = CorusColors.Text)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { selectedUser = null }) {
                    Icon(Icons.Filled.Close, contentDescription = "Deselect", tint = CorusColors.Secondary, modifier = Modifier.size(16.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a message...", style = CorusFont.body, color = CorusColors.Tertiary) },
                    singleLine = true,
                    textStyle = CorusFont.body,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CorusColors.CardBackground,
                        unfocusedContainerColor = CorusColors.CardBackground,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Button(
                    onClick = {
                        selectedUser?.let { user ->
                            onSendToUser(user.id, messageText)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                ) {
                    Text("Send", style = CorusFont.buttonSmall, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(CorusSpacing.md))
        }

        // Contact grid or search results
        if (searchQuery.length >= 2) {
            // Search results
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = CorusSpacing.sm),
            ) {
                if (isSearching) {
                    items(4) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        )
                    }
                } else if (searchResults.isEmpty()) {
                    item {
                        Text(
                            "No users found",
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                            modifier = Modifier.fillMaxWidth().padding(CorusSpacing.xxl),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    items(searchResults, key = { it.id }) { user ->
                        ShareUserRow(user = user) { selectedUser = user; searchQuery = "" }
                    }
                }
            }
        } else {
            // Recent contacts grid
            if (recentContacts.isEmpty() && !isLoadingContacts) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No recent contacts", style = CorusFont.body, color = CorusColors.Secondary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                ) {
                    items(recentContacts, key = { it.id }) { user ->
                        ShareContactCell(user = user) { selectedUser = user }
                    }
                }
            }
        }

        // Action buttons
        HorizontalDivider(color = CorusColors.Divider)
        val showInstagram = remember(instagramShareEnabled) { instagramShareEnabled && isInstagramAvailable(context) }
        var isSharingToInstagram by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
        ) {
            item {
                ShareActionButton(icon = Icons.Filled.Repeat, label = "Repost", isProminent = true) { onRepost() }
            }
            if (showInstagram) {
                item {
                    InstagramShareButton(
                        isLoading = isSharingToInstagram,
                        onClick = {
                            isSharingToInstagram = true
                            coroutineScope.launch {
                                shareToInstagramStories(context, post)
                                isSharingToInstagram = false
                            }
                        },
                    )
                }
            }
            item {
                ShareActionButton(icon = Icons.Filled.Share, label = "Share") {
                    val shareLink = "https://corus.fm/post/${post.id}"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Check out this post on Corus: $shareLink")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                }
            }
            item {
                ShareActionButton(icon = Icons.Filled.ContentCopy, label = if (showCopied) "Copied!" else "Copy Link") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Corus Link", "https://corus.fm/post/${post.id}"))
                    showCopied = true
                }
            }
        }
    }
}

@Composable
private fun ShareContactCell(
    user: CymbalUser,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, size = 72.dp)
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            user.username,
            style = CorusFont.caption,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShareUserRow(
    user: CymbalUser,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column {
            Text(user.displayName, style = CorusFont.bodyMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${user.username}", style = CorusFont.caption, color = CorusColors.Secondary)
        }
    }
    HorizontalDivider(color = CorusColors.Divider, modifier = Modifier.padding(start = 64.dp))
}

@Composable
private fun ShareActionButton(
    icon: ImageVector,
    label: String,
    isProminent: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isProminent) CorusColors.Accent else CorusColors.CardBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (isProminent) Color.White else CorusColors.Text, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(label, style = CorusFont.captionMedium, color = CorusColors.Text)
    }
}

@Composable
private fun InstagramShareButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(enabled = !isLoading, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFF58529), // Instagram orange
                            Color(0xFFDD2A7B), // Instagram pink
                            Color(0xFF8134AF), // Instagram purple
                            Color(0xFF515BD4), // Instagram blue
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                // Camera icon approximation for Instagram
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Instagram",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text("Instagram", style = CorusFont.captionMedium, color = CorusColors.Text)
    }
}
