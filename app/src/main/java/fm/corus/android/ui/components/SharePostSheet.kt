package fm.corus.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
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
    onAnalyticsLog: ((method: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<CymbalUser?>(null) }
    var messageText by remember { mutableStateOf("") }
    var showCopied by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isSearchActive = isSearchFocused || searchQuery.isNotBlank()

    // Auto-reset copied confirmation after 4 seconds
    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(4000)
            showCopied = false
        }
    }

    val shareableLink = "https://corus.fm/post/${post.id}"

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Drag indicator
        Box(
            modifier = Modifier
                .padding(top = CorusSpacing.sm, bottom = CorusSpacing.xs)
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(CorusColors.Tertiary.copy(alpha = 0.4f)),
        )

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearchQueryChange(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isSearchFocused = it.isFocused },
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

            if (isSearchActive) {
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                TextButton(onClick = {
                    searchQuery = ""
                    onSearchQueryChange("")
                    isSearchFocused = false
                }) {
                    Text("Cancel", style = CorusFont.bodyMedium, color = CorusColors.Accent)
                }
            }
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Contact grid or search results
        if (isSearchActive) {
            val hasQuery = searchQuery.isNotBlank()
            val usersToShow = if (hasQuery) searchResults else recentContacts

            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                if (isSearching) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = CorusSpacing.xxl),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = CorusColors.Secondary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                } else if (usersToShow.isEmpty() && hasQuery) {
                    item {
                        Text(
                            "No results found",
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                            modifier = Modifier.fillMaxWidth().padding(top = CorusSpacing.xxl),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    items(usersToShow, key = { it.id }) { user ->
                        ShareUserRow(
                            user = user,
                            isSelected = selectedUser?.id == user.id,
                        ) {
                            selectedUser = user
                            searchQuery = ""
                            onSearchQueryChange("")
                            isSearchFocused = false
                        }
                    }
                }
            }
        } else {
            // Recent contacts grid
            if (isLoadingContacts) {
                SkeletonShareContactsGrid()
            } else if (recentContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CorusSpacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No recent contacts", style = CorusFont.caption, color = CorusColors.Secondary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 300.dp),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                ) {
                    items(recentContacts.take(6), key = { it.id }) { user ->
                        ShareContactCell(
                            user = user,
                            isSelected = selectedUser?.id == user.id,
                        ) {
                            selectedUser = if (selectedUser?.id == user.id) null else user
                        }
                    }
                }
            }
        }

        // Bottom area: message input OR action buttons
        if (!isSearchActive) {
            if (selectedUser != null) {
                HorizontalDivider(color = CorusColors.Divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Write a message...", style = CorusFont.body, color = CorusColors.Tertiary) },
                        singleLine = true,
                        textStyle = CorusFont.body,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                    Button(
                        onClick = {
                            selectedUser?.let { user ->
                                onAnalyticsLog?.invoke("direct_message")
                                onSendToUser(user.id, messageText)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = CorusSpacing.xl, vertical = CorusSpacing.sm),
                    ) {
                        Text("Send", style = CorusFont.buttonSmall, color = Color.White)
                    }
                }
            } else {
                HorizontalDivider(color = CorusColors.Divider)
                val showInstagram = remember(instagramShareEnabled) { instagramShareEnabled && isInstagramAvailable(context) }
                val showWhatsApp = remember { isWhatsAppAvailable(context) }
                var isSharingToInstagram by remember { mutableStateOf(false) }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CorusSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                ) {
                    item {
                        ShareActionButton(icon = Icons.Filled.Repeat, label = "Repost", isProminent = true) {
                            onAnalyticsLog?.invoke("repost")
                            onRepost()
                        }
                    }
                    if (showInstagram) {
                        item {
                            InstagramShareButton(
                                isLoading = isSharingToInstagram,
                                onClick = {
                                    onAnalyticsLog?.invoke("instagram_stories")
                                    isSharingToInstagram = true
                                    coroutineScope.launch {
                                        shareToInstagramStories(context, post)
                                        isSharingToInstagram = false
                                    }
                                },
                            )
                        }
                    }
                    if (showWhatsApp) {
                        item {
                            ShareActionButton(
                                icon = Icons.Filled.Share,
                                label = "WhatsApp",
                                backgroundColor = Color(0xFF25D366),
                                iconTint = Color.White,
                            ) {
                                onAnalyticsLog?.invoke("whatsapp")
                                val encoded = Uri.encode(shareableLink)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encoded"))
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            }
                        }
                    }
                    item {
                        ShareActionButton(icon = Icons.Filled.Share, label = "Share Link") {
                            onAnalyticsLog?.invoke("share_link")
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareableLink)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share via"))
                        }
                    }
                    item {
                        ShareActionButton(
                            icon = if (showCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            label = if (showCopied) "Copied" else "Copy Link",
                        ) {
                            onAnalyticsLog?.invoke("copy_link")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Corus Link", shareableLink))
                            showCopied = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonShareContactsGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .shimmer(),
        contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
        userScrollEnabled = false,
    ) {
        items(6) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(CorusColors.CardBackground),
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.CardBackground),
                )
            }
        }
    }
}

@Composable
private fun ShareContactCell(
    user: CymbalUser,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            UserAvatarView(avatarURL = user.avatarURL, size = 72.dp)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .clip(CircleShape)
                        .background(CorusColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
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
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(avatarURL = user.avatarURL, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.username, style = CorusFont.bodyMedium, color = CorusColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(user.displayName, style = CorusFont.caption, color = CorusColors.Secondary)
        }
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = CorusColors.Accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
    HorizontalDivider(
        color = CorusColors.Divider,
        modifier = Modifier.padding(start = CorusSpacing.lg + CorusSpacing.avatarMedium + CorusSpacing.md),
    )
}

@Composable
private fun ShareActionButton(
    icon: ImageVector,
    label: String,
    isProminent: Boolean = false,
    backgroundColor: Color? = null,
    iconTint: Color? = null,
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
                .background(
                    backgroundColor
                        ?: if (isProminent) CorusColors.Accent else CorusColors.CardBackground,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = iconTint
                    ?: if (isProminent) Color.White else CorusColors.Text,
                modifier = Modifier.size(20.dp),
            )
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
                            Color(0xFFF58529),
                            Color(0xFFDD2A7B),
                            Color(0xFF8134AF),
                            Color(0xFF515BD4),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
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

private fun isWhatsAppAvailable(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.whatsapp", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
