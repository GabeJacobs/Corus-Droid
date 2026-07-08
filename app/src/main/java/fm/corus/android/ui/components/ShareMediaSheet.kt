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
import fm.corus.android.R
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/** What the share sheet is sharing — a song or a film. Lets one sheet back both
 *  the song detail and film detail screens. */
sealed interface ShareMediaSubject {
    data class Track(val track: CymbalTrack) : ShareMediaSubject
    data class Film(val movie: CymbalMovie) : ShareMediaSubject
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
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<CymbalUser?>(null) }
    var messageText by remember { mutableStateOf("") }
    var showCopied by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val isSearchActive = isSearchFocused || searchQuery.isNotBlank()

    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(4000)
            showCopied = false
        }
    }

    val shareableLink = when (subject) {
        is ShareMediaSubject.Track -> "https://corus.fm/song/${subject.track.id}"
        is ShareMediaSubject.Film -> "https://corus.fm/film/${subject.movie.id}"
    }

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
                            shareMediaToX(context, subject)
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
    }
    val url = "$link?ref=x"
    val intentUrl = "https://twitter.com/intent/tweet?text=${Uri.encode(text)}&url=${Uri.encode(url)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
    try {
        context.startActivity(intent)
    } catch (_: Exception) { }
}
