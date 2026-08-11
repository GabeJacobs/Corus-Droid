package fm.corus.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import fm.corus.android.R
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/** Minimal payloads for sharing a catalog entity (artist / album / director). */
data class ShareArtistSubject(val id: String, val name: String, val imageUrl: String?)
data class ShareAlbumSubject(
    val id: String,
    val title: String,
    val artistName: String,
    val coverUrl: String?,
    val year: String?,
)
data class ShareDirectorSubject(val id: String, val name: String, val imageUrl: String?)

/** Minimal payload for sharing a user's *profile* — the uid (for in-app nav +
 *  the DM message), the username (for the `/u/{username}` deep link), and
 *  optional display name / avatar for the DM bubble. */
data class ShareProfileSubject(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bio: String? = null,
    val artworkUrls: List<String> = emptyList(),
    val previewVersion: String? = null,
) {
    /** Enough profile context to draw the light card locally in the share sheet. */
    val hasLocalPreview: Boolean
        get() = !avatarUrl.isNullOrBlank() || artworkUrls.isNotEmpty() || !bio.isNullOrBlank()
}

/** What the share sheet is sharing — a song, film, artist, album, director, or
 *  a user's profile. Lets one sheet back every detail / destination screen. */
sealed interface ShareMediaSubject {
    data class Track(val track: CymbalTrack) : ShareMediaSubject
    data class Film(val movie: CymbalMovie) : ShareMediaSubject
    data class Artist(val artist: ShareArtistSubject) : ShareMediaSubject
    data class Album(val album: ShareAlbumSubject) : ShareMediaSubject
    data class Director(val director: ShareDirectorSubject) : ShareMediaSubject
    data class Profile(val profile: ShareProfileSubject) : ShareMediaSubject
}

/** Hooks for profile share Firebase events. Passed only for profile subjects. */
data class ProfileShareAnalytics(
    val profileUserId: String,
    val isOwnProfile: Boolean,
    val entryPoint: String,
    val onShared: (method: String, cardTheme: ShareCardTheme?) -> Unit,
    val onSheetOpened: () -> Unit,
    val onThemeChanged: (ShareCardTheme) -> Unit,
)

private fun logShareMethod(
    method: String,
    cardTheme: ShareCardTheme? = null,
    profileShareAnalytics: ProfileShareAnalytics? = null,
    onAnalyticsLog: ((method: String) -> Unit)? = null,
) {
    if (profileShareAnalytics != null) {
        profileShareAnalytics.onShared(method, cardTheme)
    } else {
        onAnalyticsLog?.invoke(method)
    }
}

/**
 * Share sheet for *media* — a song or a film (as opposed to a post), presented
 * from the song / film detail screen's top-bar. Mirrors [SharePostSheet]'s
 * recipient picker and reuses its cells/buttons, but shares media: DMs send a
 * `sharedTrack` / `sharedFilm` message (deep-links to the detail page in-app)
 * and external channels carry a `corus.fm/song/{id}` or `corus.fm/film/{id}`
 * link.
 *
 * Repost and Instagram Stories are intentionally omitted — a detail page has no
 * underlying post to repost, and the Instagram card is built around a poster's
 * avatar + caption, which bare media has neither of.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMediaSheet(
    subject: ShareMediaSubject,
    recentContacts: List<CymbalUser>,
    searchResults: List<CymbalUser>,
    isSearching: Boolean,
    isLoadingContacts: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSendToUser: (userId: String, message: String) -> Unit,
    onDismiss: () -> Unit,
    onAnalyticsLog: ((method: String) -> Unit)? = null,
    isOwnProfile: Boolean = false,
    instagramShareEnabled: Boolean = false,
    profileShareAnalytics: ProfileShareAnalytics? = null,
) {
    val sharedProfile = (subject as? ShareMediaSubject.Profile)?.profile
    val showOwnProfileSheet = isOwnProfile && sharedProfile != null

    LaunchedEffect(profileShareAnalytics?.profileUserId, showOwnProfileSheet) {
        profileShareAnalytics?.onSheetOpened()
    }

    if (showOwnProfileSheet) {
        OwnProfileShareSheet(
            profile = sharedProfile!!,
            instagramShareEnabled = instagramShareEnabled,
            onDismiss = onDismiss,
            profileShareAnalytics = profileShareAnalytics,
            onAnalyticsLog = onAnalyticsLog,
        )
        return
    }

    RecipientPickerShareMediaSheet(
        subject = subject,
        recentContacts = recentContacts,
        searchResults = searchResults,
        isSearching = isSearching,
        isLoadingContacts = isLoadingContacts,
        onSearchQueryChange = onSearchQueryChange,
        onSendToUser = onSendToUser,
        onDismiss = onDismiss,
        onAnalyticsLog = onAnalyticsLog,
        profileShareAnalytics = profileShareAnalytics,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnProfileShareSheet(
    profile: ShareProfileSubject,
    instagramShareEnabled: Boolean,
    onDismiss: () -> Unit,
    profileShareAnalytics: ProfileShareAnalytics? = null,
    onAnalyticsLog: ((method: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = LocalCorusDarkTheme.current
    var shareCardTheme by remember {
        mutableStateOf(if (isDarkTheme) ShareCardTheme.DARK else ShareCardTheme.LIGHT)
    }
    var hasLoggedInitialTheme by remember { mutableStateOf(false) }
    var showCopied by remember { mutableStateOf(false) }
    var isSharingToInstagram by remember { mutableStateOf(false) }

    LaunchedEffect(shareCardTheme) {
        if (!hasLoggedInitialTheme) {
            hasLoggedInitialTheme = true
        } else {
            profileShareAnalytics?.onThemeChanged(shareCardTheme)
        }
    }

    val shareableLink = "https://corus.fm/u/${profile.username}"
    val outboundShareLink = if (shareCardTheme == ShareCardTheme.LIGHT) {
        "$shareableLink?theme=light"
    } else {
        shareableLink
    }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(4000)
            showCopied = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ShareSheetDragIndicator()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xxl)
                .padding(bottom = CorusSpacing.sm),
        ) {
            Text(
                stringResource(R.string.share_profile_title),
                style = CorusFont.songTitleLarge,
                color = CorusColors.Text,
            )
            Text(
                stringResource(R.string.share_profile_subtitle),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
            )
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.xxl)
                .padding(bottom = CorusSpacing.sm),
        ) {
            ShareCardTheme.entries.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = shareCardTheme == theme,
                    onClick = { shareCardTheme = theme },
                    shape = SegmentedButtonDefaults.itemShape(index, ShareCardTheme.entries.size),
                ) {
                    Text(theme.label, style = CorusFont.bodyMedium)
                }
            }
        }

        if (profile.hasLocalPreview) {
            LocalProfileSharePreviewCard(profile = profile, theme = shareCardTheme)
        } else {
            ShareLinkPreviewCard(
                shareableLink = shareableLink,
                version = profile.previewVersion,
                theme = shareCardTheme,
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        HorizontalDivider(color = CorusColors.Divider)

        val showInstagram = remember(instagramShareEnabled) {
            instagramShareEnabled && isInstagramAvailable(context)
        }
        val showWhatsApp = remember { isWhatsAppAvailable(context) }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
        ) {
            if (showInstagram) {
                item {
                    InstagramShareButton(
                        isLoading = isSharingToInstagram,
                        onClick = {
                            logShareMethod(
                                method = "instagram_stories",
                                cardTheme = shareCardTheme,
                                profileShareAnalytics = profileShareAnalytics,
                                onAnalyticsLog = onAnalyticsLog,
                            )
                            isSharingToInstagram = true
                            val themeToShare = shareCardTheme
                            coroutineScope.launch {
                                shareProfileToInstagramStories(context, profile, themeToShare)
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
                        logShareMethod(
                            method = "whatsapp",
                            cardTheme = shareCardTheme,
                            profileShareAnalytics = profileShareAnalytics,
                            onAnalyticsLog = onAnalyticsLog,
                        )
                        val encoded = Uri.encode(outboundShareLink)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encoded"))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) { }
                    }
                }
            }
            item {
                XShareButton {
                    logShareMethod(
                        method = "x",
                        cardTheme = shareCardTheme,
                        profileShareAnalytics = profileShareAnalytics,
                        onAnalyticsLog = onAnalyticsLog,
                    )
                    shareMediaToX(context, ShareMediaSubject.Profile(profile))
                }
            }
            item {
                ShareActionButton(icon = Icons.Filled.Share, label = stringResource(R.string.share_post_share_link)) {
                    logShareMethod(
                        method = "share_link",
                        cardTheme = shareCardTheme,
                        profileShareAnalytics = profileShareAnalytics,
                        onAnalyticsLog = onAnalyticsLog,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, outboundShareLink)
                    }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_post_share_chooser)))
                }
            }
            item {
                ShareActionButton(
                    icon = if (showCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    label = if (showCopied) stringResource(R.string.share_post_copied) else stringResource(R.string.share_post_copy_link),
                ) {
                    logShareMethod(
                        method = "copy_link",
                        cardTheme = shareCardTheme,
                        profileShareAnalytics = profileShareAnalytics,
                        onAnalyticsLog = onAnalyticsLog,
                    )
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.share_post_clip_label), outboundShareLink))
                    showCopied = true
                }
            }
        }
    }
}

@Composable
private fun ShareSheetDragIndicator() {
    Box(
        modifier = Modifier
            .padding(top = CorusSpacing.sm, bottom = CorusSpacing.xs)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(CorusColors.Tertiary.copy(alpha = 0.4f)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientPickerShareMediaSheet(
    subject: ShareMediaSubject,
    recentContacts: List<CymbalUser>,
    searchResults: List<CymbalUser>,
    isSearching: Boolean,
    isLoadingContacts: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSendToUser: (userId: String, message: String) -> Unit,
    onDismiss: () -> Unit,
    onAnalyticsLog: ((method: String) -> Unit)?,
    profileShareAnalytics: ProfileShareAnalytics? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<CymbalUser?>(null) }
    var messageText by remember { mutableStateOf("") }
    var showCopied by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    // A recipient chosen from search results is pinned to the front of the recents
    // grid so the selection stays visible (with a checkmark) once the query clears —
    // otherwise a searched-for, non-recent recipient would vanish from the sheet.
    var pinnedUser by remember { mutableStateOf<CymbalUser?>(null) }

    val isSearchActive = isSearchFocused || searchQuery.isNotBlank()

    // Recents with the pinned (searched-and-selected) recipient moved to the front.
    val displayContacts = remember(recentContacts, pinnedUser) {
        val pin = pinnedUser
        if (pin != null) listOf(pin) + recentContacts.filter { it.id != pin.id }
        else recentContacts
    }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(4000)
            showCopied = false
        }
    }

    val shareableLink = when (subject) {
        is ShareMediaSubject.Track -> "https://corus.fm/song/${subject.track.id}"
        is ShareMediaSubject.Film -> "https://corus.fm/film/${subject.movie.id}"
        is ShareMediaSubject.Artist -> "https://corus.fm/artist/${subject.artist.id}"
        is ShareMediaSubject.Album -> "https://corus.fm/album/${subject.album.id}"
        is ShareMediaSubject.Director -> "https://corus.fm/director/${subject.director.id}"
        is ShareMediaSubject.Profile -> "https://corus.fm/u/${subject.profile.username}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSearchActive) Modifier.fillMaxHeight() else Modifier),
    ) {
        ShareSheetDragIndicator()

        // No link preview here — matching iOS, the card only appears on the
        // own-profile outbound sheet. Catalog media and other-profile shares
        // go straight to recipients.

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
            val usersToShow = if (hasQuery) searchResults else displayContacts

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
                            pinnedUser = user
                            searchQuery = ""
                            onSearchQueryChange("")
                            isSearchFocused = false
                            focusManager.clearFocus()
                        }
                    }
                }
            }
        } else {
            if (isLoadingContacts) {
                SkeletonShareContactsGrid()
            } else if (displayContacts.isEmpty()) {
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
                    items(displayContacts.take(6), key = { it.id }) { user ->
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
                                logShareMethod(
                                    method = "direct_message",
                                    profileShareAnalytics = profileShareAnalytics,
                                    onAnalyticsLog = onAnalyticsLog,
                                )
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
                val showWhatsApp = remember { isWhatsAppAvailable(context) }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CorusSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                    contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                ) {
                    if (showWhatsApp) {
                        item {
                            ShareActionButton(
                                label = stringResource(R.string.share_post_whatsapp),
                                painter = painterResource(R.drawable.whatsapp_logo),
                                iconSize = 22.dp,
                                backgroundColor = Color(0xFF25D366),
                                iconTint = Color.White,
                            ) {
                                logShareMethod(
                                    method = "whatsapp",
                                    profileShareAnalytics = profileShareAnalytics,
                                    onAnalyticsLog = onAnalyticsLog,
                                )
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
                            logShareMethod(
                                method = "x",
                                profileShareAnalytics = profileShareAnalytics,
                                onAnalyticsLog = onAnalyticsLog,
                            )
                            shareMediaToX(context, subject)
                        }
                    }
                    item {
                        ShareActionButton(icon = Icons.Filled.Share, label = stringResource(R.string.share_post_share_link)) {
                            logShareMethod(
                                method = "share_link",
                                profileShareAnalytics = profileShareAnalytics,
                                onAnalyticsLog = onAnalyticsLog,
                            )
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
                            logShareMethod(
                                method = "copy_link",
                                profileShareAnalytics = profileShareAnalytics,
                                onAnalyticsLog = onAnalyticsLog,
                            )
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

/**
 * Opens the X compose intent pre-filled with Shazam-style copy plus the media
 * link, tagged `?ref=x`. Mirrors [SharePostSheet]'s post variant.
 */
private fun shareMediaToX(context: Context, subject: ShareMediaSubject) {
    val (text, link) = when (subject) {
        is ShareMediaSubject.Track ->
            "${subject.track.name} by ${subject.track.artistName} on @corusapp" to
                "https://corus.fm/song/${subject.track.id}"
        is ShareMediaSubject.Film -> {
            val t = if (subject.movie.directorName.isBlank())
                "${subject.movie.title} on @corusapp"
            else
                "${subject.movie.title} by ${subject.movie.directorName} on @corusapp"
            t to "https://corus.fm/film/${subject.movie.id}"
        }
        is ShareMediaSubject.Artist ->
            "${subject.artist.name} on @corusapp" to
                "https://corus.fm/artist/${subject.artist.id}"
        is ShareMediaSubject.Album -> {
            val t = if (subject.album.artistName.isBlank())
                "${subject.album.title} on @corusapp"
            else
                "${subject.album.title} by ${subject.album.artistName} on @corusapp"
            t to "https://corus.fm/album/${subject.album.id}"
        }
        is ShareMediaSubject.Director ->
            "${subject.director.name} on @corusapp" to
                "https://corus.fm/director/${subject.director.id}"
        is ShareMediaSubject.Profile ->
            "@${subject.profile.username} on @corusapp" to
                "https://corus.fm/u/${subject.profile.username}"
    }
    val url = "$link?ref=x"
    val intentUrl = "https://twitter.com/intent/tweet?text=${Uri.encode(text)}&url=${Uri.encode(url)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
    try {
        context.startActivity(intent)
    } catch (_: Exception) { }
}
