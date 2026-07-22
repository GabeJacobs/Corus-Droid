package fm.corus.android.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.ui.components.CommentsAudiencePicker
import fm.corus.android.ui.components.ToastHost
import fm.corus.android.ui.components.TrophyCelebrationView
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * The share-to-Corus sheet: mirrors the iOS extension's composer (song card,
 * caption, comment-audience picker, SET YOUR CORUS, album picker, trophy)
 * using the app's own components and design tokens.
 */
@Composable
fun ShareComposerScreen(
    sharedText: String?,
    onFinish: () -> Unit,
    onPosted: () -> Unit,
    viewModel: ShareComposerViewModel = hiltViewModel(),
) {
    val phase by viewModel.phase.collectAsState()
    val track by viewModel.track.collectAsState()
    val album by viewModel.album.collectAsState()
    val caption by viewModel.caption.collectAsState()
    val commentsAudience by viewModel.commentsAudience.collectAsState()
    val trophyPost by viewModel.trophyPost.collectAsState()

    LaunchedEffect(Unit) { viewModel.start(sharedText) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CorusColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when (val p = phase) {
            is ShareComposerViewModel.Phase.Posted -> {
                if (p.isFirstPoster && trophyPost != null) {
                    // Same staging as the app: the trophy overlays the inert
                    // composer; dismissing it lands the user in Corus on their post.
                    ComposerContent(viewModel, track, caption, commentsAudience, phase, enabled = false)
                    TrophyCelebrationView(
                        post = trophyPost!!,
                        visible = true,
                        onDismiss = onPosted,
                    )
                } else {
                    PostedConfirmation(onDone = onPosted)
                }
            }

            is ShareComposerViewModel.Phase.Blocked -> BlockedView(p.reason, viewModel::retry, onFinish)

            ShareComposerViewModel.Phase.LoadingAlbum -> CenteredLoading(stringResource(R.string.share_loading_album))

            ShareComposerViewModel.Phase.AlbumPicker -> AlbumPickerView(
                album = album,
                onCancel = onFinish,
                onSelect = viewModel::selectAlbumTrack,
            )

            else -> ComposerContent(viewModel, track, caption, commentsAudience, phase, enabled = true, onFinish = onFinish)
        }

        ToastHost(modifier = Modifier.align(Alignment.TopCenter))
    }
}

// ── Composer ───────────────────────────────────────────────────────────────

@Composable
private fun ComposerContent(
    viewModel: ShareComposerViewModel,
    track: fm.corus.android.data.model.CymbalTrack?,
    caption: String,
    commentsAudience: fm.corus.android.data.model.CommentsAudience,
    phase: ShareComposerViewModel.Phase,
    enabled: Boolean,
    onFinish: () -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val isPosting = phase == ShareComposerViewModel.Phase.Posting
    val isReady = phase == ShareComposerViewModel.Phase.Ready

    // Focus the caption once the song lands (mirrors iOS).
    LaunchedEffect(isReady) {
        if (isReady && enabled) focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // Top bar: back chevron (album flow), centered title, Cancel.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (viewModel.cameFromAlbum && (isReady || isPosting)) {
                IconButton(
                    onClick = { viewModel.backToAlbum() },
                    enabled = !isPosting,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.share_back),
                        tint = CorusColors.Secondary,
                    )
                }
            }
            Text(
                text = stringResource(R.string.share_title),
                style = CorusFont.screenTitle,
                color = CorusColors.Text,
                modifier = Modifier.align(Alignment.Center),
            )
            TextButton(
                onClick = onFinish,
                enabled = !isPosting,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Text(
                    text = stringResource(R.string.share_cancel),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                )
            }
        }

        // Song row (plain, artist-only subtitle — mirrors the compose screen).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                    .background(CorusColors.CardBackground),
            ) {
                val art = track?.albumArtLargeURL ?: track?.albumArtURL
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = CorusColors.Secondary,
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(CorusSpacing.md))
            if (track != null) {
                Column {
                    Text(
                        text = track.name,
                        style = CorusFont.songTitle,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artistName,
                        style = CorusFont.artistName,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = CorusColors.Secondary,
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                    Text(
                        text = stringResource(R.string.share_loading_song),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                    )
                }
            }
        }

        // Caption (borderless, like the compose screen).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = CorusSpacing.lg),
        ) {
            if (caption.isEmpty()) {
                Text(
                    text = stringResource(R.string.share_caption_placeholder),
                    style = CorusFont.body,
                    color = CorusColors.Secondary.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = caption,
                onValueChange = viewModel::setCaption,
                enabled = enabled && !isPosting,
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
                cursorBrush = SolidColor(CorusColors.Accent),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
            )
        }

        if (caption.length >= ShareComposerViewModel.CAPTION_COUNTER_THRESHOLD) {
            Text(
                text = "${caption.length}/${ShareComposerViewModel.CAPTION_LIMIT}",
                style = CorusFont.caption,
                color = if (caption.length >= ShareComposerViewModel.CAPTION_LIMIT - 10) {
                    CorusColors.Error
                } else {
                    CorusColors.Secondary
                },
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg),
            )
        }

        if (viewModel.commentControlsEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
            ) {
                CommentsAudiencePicker(
                    selection = commentsAudience,
                    onSelect = viewModel::setCommentsAudience,
                )
            }
        }

        // SET YOUR CORUS — the app's exact button (ComposeScreen parity).
        val limitMessage = stringResource(R.string.share_post_limit)
        val hardCapMessage = stringResource(R.string.share_hard_cap)
        val bannedMessage = stringResource(R.string.share_posting_banned)
        val genericMessage = stringResource(R.string.share_generic_error)
        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.post(
                    limitMessage = limitMessage,
                    hardCapMessage = hardCapMessage,
                    bannedMessage = bannedMessage,
                    genericMessage = genericMessage,
                )
            },
            enabled = enabled && isReady && track != null,
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            colors = ButtonDefaults.buttonColors(
                containerColor = CorusColors.Accent,
                disabledContainerColor = CorusColors.Accent.copy(alpha = 0.5f),
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = CorusSpacing.lg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
        ) {
            if (isPosting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text(
                    text = stringResource(R.string.compose_post_button),
                    style = CorusFont.button,
                    color = Color.White,
                )
            }
        }
    }
}

// ── Album picker (mirrors AlbumPageScreen's header + tracklist) ────────────

@Composable
private fun AlbumPickerView(
    album: ShareAlbum?,
    onCancel: () -> Unit,
    onSelect: (ShareAlbumTrack) -> Unit,
) {
    if (album == null) return
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = stringResource(R.string.share_title),
                style = CorusFont.screenTitle,
                color = CorusColors.Text,
                modifier = Modifier.align(Alignment.Center),
            )
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text(
                    text = stringResource(R.string.share_cancel),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = CorusSpacing.xl, bottom = CorusSpacing.md)
                            .size(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CorusColors.CardBackground),
                    ) {
                        if (album.coverUrl != null) {
                            AsyncImage(
                                model = album.coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        text = album.title,
                        style = CorusFont.songTitleLarge,
                        color = CorusColors.Text,
                        textAlign = TextAlign.Center,
                    )
                    if (album.artistName.isNotEmpty()) {
                        Text(
                            text = album.artistName,
                            style = CorusFont.artistName,
                            color = CorusColors.Text,
                        )
                    }
                    Text(
                        text = albumMetaLine(album),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                    Text(
                        text = stringResource(R.string.share_tap_song),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(top = CorusSpacing.md, bottom = CorusSpacing.lg),
                    )
                }
            }
            itemsIndexed(album.tracks, key = { _, t -> t.id }) { index, albumTrack ->
                AlbumTrackRow(
                    number = index + 1,
                    albumTrack = albumTrack,
                    onSelect = { onSelect(albumTrack) },
                )
            }
            item { Spacer(modifier = Modifier.height(CorusSpacing.xxl)) }
        }
    }
}

@Composable
private fun albumMetaLine(album: ShareAlbum): String {
    val parts = mutableListOf(stringResource(R.string.share_album_word))
    album.year?.let { parts.add(it) }
    val count = album.tracks.size
    parts.add(
        if (count == 1) stringResource(R.string.share_song_count_one)
        else stringResource(R.string.share_song_count_many, count)
    )
    return parts.joinToString(" · ")
}

@Composable
private fun AlbumTrackRow(
    number: Int,
    albumTrack: ShareAlbumTrack,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = CorusSpacing.lg, vertical = 10.dp),
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = CorusFont.caption,
                color = CorusColors.Secondary,
            )
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = albumTrack.name,
                style = CorusFont.bodyMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (albumTrack.artistName.isNotEmpty()) {
                Text(
                    text = albumTrack.artistName,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (albumTrack.durationMs > 0) {
            Text(
                text = albumTrack.formattedDuration,
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
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

// ── Terminal states ────────────────────────────────────────────────────────

@Composable
private fun CenteredLoading(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = CorusColors.Secondary)
        Spacer(modifier = Modifier.height(CorusSpacing.md))
        Text(text = label, style = CorusFont.body, color = CorusColors.Secondary)
    }
}

@Composable
private fun PostedConfirmation(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1200)
        onDone()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = CorusColors.Accent,
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.height(CorusSpacing.md))
        Text(
            text = stringResource(R.string.share_posted),
            style = CorusFont.displayName,
            color = CorusColors.Text,
        )
    }
}

@Composable
private fun BlockedView(
    reason: ShareComposerViewModel.BlockedReason,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val retryable = reason == ShareComposerViewModel.BlockedReason.SONG_UNAVAILABLE ||
        reason == ShareComposerViewModel.BlockedReason.ALBUM_UNAVAILABLE
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = CorusSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = when (reason) {
                ShareComposerViewModel.BlockedReason.NOT_SIGNED_IN -> Icons.Outlined.PersonOff
                else -> Icons.Filled.MusicNote
            },
            contentDescription = null,
            tint = CorusColors.Secondary,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(CorusSpacing.md))
        Text(
            text = stringResource(
                when (reason) {
                    ShareComposerViewModel.BlockedReason.NOT_SIGNED_IN -> R.string.share_blocked_signin_title
                    ShareComposerViewModel.BlockedReason.UNSUPPORTED_LINK -> R.string.share_blocked_unsupported_title
                    ShareComposerViewModel.BlockedReason.SONG_UNAVAILABLE -> R.string.share_blocked_song_title
                    ShareComposerViewModel.BlockedReason.ALBUM_UNAVAILABLE -> R.string.share_blocked_album_title
                    ShareComposerViewModel.BlockedReason.NOT_ON_CORUS -> R.string.share_blocked_nomatch_title
                    ShareComposerViewModel.BlockedReason.UNRELEASED -> R.string.share_blocked_unreleased_title
                }
            ),
            style = CorusFont.displayName,
            color = CorusColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            text = stringResource(
                when (reason) {
                    ShareComposerViewModel.BlockedReason.NOT_SIGNED_IN -> R.string.share_blocked_signin_subtitle
                    ShareComposerViewModel.BlockedReason.UNSUPPORTED_LINK -> R.string.share_blocked_unsupported_subtitle
                    ShareComposerViewModel.BlockedReason.NOT_ON_CORUS -> R.string.share_blocked_nomatch_subtitle
                    ShareComposerViewModel.BlockedReason.UNRELEASED -> R.string.share_blocked_unreleased_subtitle
                    else -> R.string.share_blocked_unavailable_subtitle
                }
            ),
            style = CorusFont.body,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )
        if (retryable) {
            Spacer(modifier = Modifier.height(CorusSpacing.lg))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            ) {
                Text(
                    text = stringResource(R.string.share_try_again),
                    style = CorusFont.button,
                    color = Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onClose, modifier = Modifier.padding(bottom = CorusSpacing.xl)) {
            Text(
                text = stringResource(R.string.share_close),
                style = CorusFont.button,
                color = CorusColors.Accent,
            )
        }
    }
}
