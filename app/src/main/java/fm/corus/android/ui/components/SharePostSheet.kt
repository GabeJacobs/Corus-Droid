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
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePostSheet(
    post: CymbalPost,
    recentContacts: List<CymbalUser>,
    searchResults: List<CymbalUser>,
    isSearching: Boolean,
    isLoadingContacts: Boolean,
    instagramShareEnabled: Boolean,
    sheetState: SheetState,
    onSearchQueryChange: (String) -> Unit,
    onSendToUser: (userId: String, message: String) -> Unit,
    onRepost: () -> Unit,
    onDismiss: () -> Unit,
    onAnalyticsLog: ((method: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
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
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSearchActive) Modifier.fillMaxHeight() else Modifier),
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
                placeholder = { Text(stringResource(R.string.share_post_search_placeholder), style = CorusFont.body, color = CorusColors.Tertiary) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = CorusColors.Tertiary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            onSearchQueryChange("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.share_post_cd_clear), tint = CorusColors.Tertiary)
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
                    Text(stringResource(R.string.share_post_cancel), style = CorusFont.bodyMedium, color = CorusColors.Accent)
                }
            }
        }

        HorizontalDivider(color = CorusColors.Divider)

        // Contact grid or search results
        if (isSearchActive) {
            val hasQuery = searchQuery.isNotBlank()
            val usersToShow = if (hasQuery) searchResults else recentContacts

            LazyColumn(
                modifier = Modifier.weight(1f),
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
                            stringResource(R.string.share_post_no_results),
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
                            focusManager.clearFocus()
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
                    Text(stringResource(R.string.share_post_no_recent), style = CorusFont.caption, color = CorusColors.Secondary)
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
                        placeholder = { Text(stringResource(R.string.share_post_message_placeholder), style = CorusFont.body, color = CorusColors.Tertiary) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
                        Text(stringResource(R.string.share_post_send), style = CorusFont.buttonSmall, color = Color.White)
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
                        ShareActionButton(icon = Icons.Filled.Repeat, label = stringResource(R.string.share_post_repost), isProminent = true) {
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
                                label = stringResource(R.string.share_post_whatsapp),
                                painter = painterResource(R.drawable.whatsapp_logo),
                                iconSize = 22.dp,
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
                        XShareButton {
                            onAnalyticsLog?.invoke("x")
                            shareToX(context, post)
                        }
                    }
                    item {
                        ShareActionButton(icon = Icons.Filled.Share, label = stringResource(R.string.share_post_share_link)) {
                            onAnalyticsLog?.invoke("share_link")
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareableLink)
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_post_share_chooser)))
                        }
                    }
                    item {
                        ShareActionButton(
                            icon = if (showCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            label = if (showCopied) stringResource(R.string.share_post_copied) else stringResource(R.string.share_post_copy_link),
                        ) {
                            onAnalyticsLog?.invoke("copy_link")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.share_post_clip_label), shareableLink))
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
                        .background(CorusColors.Skeleton),
                )
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CorusColors.Skeleton),
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
            UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = 72.dp)
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
                        contentDescription = stringResource(R.string.share_post_cd_selected),
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
        UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = CorusSpacing.avatarMedium)
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            UsernameWithFlair(
                username = user.username,
                isVerified = user.isVerified,
                isClubMember = user.isClubMember,
                flairStyle = user.flairStyle,
                isBot = user.isBot,
                style = CorusFont.username,
                color = CorusColors.Text,
            )
            Text(user.displayName, style = CorusFont.caption, color = CorusColors.Secondary)
        }
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.share_post_cd_selected),
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
    label: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    iconSize: Dp = 20.dp,
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
            val tint = iconTint ?: if (isProminent) Color.White else CorusColors.Text
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            } else if (icon != null) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            }
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
                    painter = painterResource(R.drawable.instagram_logo),
                    contentDescription = stringResource(R.string.share_post_cd_instagram),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(stringResource(R.string.share_post_instagram), style = CorusFont.captionMedium, color = CorusColors.Text)
    }
}

@Composable
private fun XShareButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_x_logo),
                contentDescription = stringResource(R.string.share_post_cd_x),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(stringResource(R.string.share_post_x), style = CorusFont.captionMedium, color = CorusColors.Text)
    }
}

/**
 * Opens the X compose intent (X app if installed, otherwise the web composer)
 * pre-filled with Shazam-style copy plus the post link, tagged `?ref=x`. X turns
 * the URL into a tappable link and @corusapp into a mention.
 */
private fun shareToX(context: Context, post: CymbalPost) {
    val text = if (post.isMovie) {
        "${post.displayTitle} on @corusapp"
    } else {
        "${post.displayTitle} by ${post.displaySubtitle} on @corusapp"
    }
    val url = "https://corus.fm/post/${post.id}?ref=x"
    val intentUrl = "https://twitter.com/intent/tweet?text=${Uri.encode(text)}&url=${Uri.encode(url)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
    try {
        context.startActivity(intent)
    } catch (_: Exception) { }
}

private fun isWhatsAppAvailable(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.whatsapp", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
