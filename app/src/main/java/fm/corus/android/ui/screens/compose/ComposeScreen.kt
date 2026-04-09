package fm.corus.android.ui.screens.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.ui.components.TrophyCelebrationView
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.VoiceNoteRecorderView
import fm.corus.android.ui.components.rememberVoiceNoteRecorderState
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun ComposeScreen(
    onDismiss: () -> Unit = {},
    movieModeEnabled: Boolean = false,
    preSelectedTrackId: String? = null,
    preSelectedMovieId: String? = null,
    viewModel: ComposeViewModel = hiltViewModel(),
) {
    val selectedTrack by viewModel.selectedTrack.collectAsState()
    val selectedMovie by viewModel.selectedMovie.collectAsState()
    val isPosting by viewModel.isPosting.collectAsState()
    val postSuccess by viewModel.postSuccess.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()
    val showTrophy by viewModel.showTrophy.collectAsState()
    val trophyPost by viewModel.trophyPost.collectAsState()
    val showPostLimitPaywall by viewModel.showPostLimitPaywall.collectAsState()
    var mediaType by remember { mutableStateOf(MediaType.TRACK) }
    var searchQuery by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var captionMode by remember { mutableStateOf("text") } // "text" or "voice"
    val voiceRecorderState = rememberVoiceNoteRecorderState()

    val hasSelection = selectedTrack != null || selectedMovie != null

    LaunchedEffect(postSuccess) {
        if (postSuccess) {
            onDismiss()
        }
    }

    LaunchedEffect(preSelectedTrackId) {
        if (preSelectedTrackId != null) viewModel.loadAndSelectTrack(preSelectedTrackId)
    }

    LaunchedEffect(preSelectedMovieId) {
        if (preSelectedMovieId != null) viewModel.loadAndSelectMovie(preSelectedMovieId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CorusColors.Background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            ) {
                // Left: back chevron (only in compose mode)
                if (hasSelection) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CorusColors.Text,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(20.dp)
                            .clickable {
                                viewModel.clearSelection()
                                searchQuery = ""
                            },
                    )
                }

                // Center: title
                Text(
                    text = "Set Corus",
                    style = CorusFont.displayName,
                    color = CorusColors.Text,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Right: Cancel button
                Text(
                    text = "Cancel",
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { onDismiss() },
                )
            }

            // ── Media type toggle chips (if movie mode enabled) ──
            if (movieModeEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    MediaTypeChip(
                        label = "Music",
                        icon = Icons.Filled.MusicNote,
                        selected = mediaType == MediaType.TRACK,
                        onClick = {
                            mediaType = MediaType.TRACK
                            viewModel.clearSelection()
                            searchQuery = ""
                        },
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                    MediaTypeChip(
                        label = "Film",
                        icon = Icons.Filled.Movie,
                        selected = mediaType == MediaType.MOVIE,
                        onClick = {
                            mediaType = MediaType.MOVIE
                            viewModel.clearSelection()
                            searchQuery = ""
                        },
                    )
                }
                Spacer(modifier = Modifier.height(CorusSpacing.sm))
            }

            // ── Error banner ──
            if (error != null) {
                Text(
                    text = error ?: "",
                    style = CorusFont.caption,
                    color = CorusColors.Error,
                    modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
                )
            }

            if (!hasSelection) {
                // ════════════════════════════════════════
                // SEARCH MODE
                // ════════════════════════════════════════
                SearchModeContent(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        if (query.length >= 2) {
                            viewModel.search(query, mediaType)
                        }
                    },
                    mediaType = mediaType,
                    isSearching = isSearching,
                    searchResults = searchResults,
                    onResultClick = { result ->
                        viewModel.selectResult(result, mediaType)
                    },
                )
            } else {
                // ════════════════════════════════════════
                // COMPOSE MODE
                // ════════════════════════════════════════
                ComposeModeContent(
                    mediaType = mediaType,
                    selectedTrack = selectedTrack,
                    selectedMovie = selectedMovie,
                    caption = caption,
                    onCaptionChange = { newCaption ->
                        caption = newCaption.take(700)
                        viewModel.checkForMention(caption)
                    },
                    isPosting = isPosting,
                    onPost = {
                        viewModel.createPost(
                            caption = if (captionMode == "text") caption else "",
                            mediaType = mediaType,
                            voiceNoteData = if (captionMode == "voice") voiceRecorderState.audioData else null,
                        )
                    },
                    mentionSuggestions = mentionSuggestions,
                    onMentionSelected = { user ->
                        val words = caption.split(" ").toMutableList()
                        if (words.isNotEmpty() && words.last().startsWith("@")) {
                            words[words.lastIndex] = "@${user.username} "
                            caption = words.joinToString(" ")
                        }
                        viewModel.clearMentionSuggestions()
                    },
                    captionMode = captionMode,
                    onCaptionModeChange = { captionMode = it },
                    voiceRecorderState = voiceRecorderState,
                )
            }
        }
    }

    // Trophy celebration overlay
    val currentTrophyPost = trophyPost
    if (currentTrophyPost != null) {
        TrophyCelebrationView(
            post = currentTrophyPost,
            visible = showTrophy,
            onDismiss = { viewModel.dismissTrophy() },
        )
    }

    // Post limit paywall
    if (showPostLimitPaywall) {
        fm.corus.android.ui.screens.subscription.PostLimitPaywallSheet(
            onDismiss = { viewModel.dismissPostLimitPaywall() },
            onNavigateToClub = {
                viewModel.dismissPostLimitPaywall()
                onDismiss()
                // The CymbalClubOfferScreen will be navigated to via the nav graph
            },
        )
    }
    } // end Box
}

// ════════════════════════════════════════════════════════════
// Search Mode
// ════════════════════════════════════════════════════════════

@Composable
private fun SearchModeContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    mediaType: MediaType,
    isSearching: Boolean,
    searchResults: List<Triple<String?, String, String>>,
    onResultClick: (Triple<String?, String, String>) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg),
            placeholder = {
                Text(
                    text = if (mediaType == MediaType.TRACK) "Search for a song..." else "Search for a film...",
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
        )

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CorusSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = CorusColors.Accent,
                    strokeWidth = 2.dp,
                )
            }
        }

        // Results list
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(searchResults) { result ->
                SearchResultRow(
                    imageURL = result.first,
                    title = result.second,
                    subtitle = result.third,
                    onClick = { onResultClick(result) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    imageURL: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = imageURL,
            contentDescription = title,
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch) // 48dp
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)), // 6dp
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CorusFont.songTitle, // ExtraBold 16sp
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = CorusFont.artistName, // Medium 14sp
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// Compose Mode
// ════════════════════════════════════════════════════════════

@Composable
private fun ComposeModeContent(
    mediaType: MediaType,
    selectedTrack: fm.corus.android.data.model.CymbalTrack?,
    selectedMovie: fm.corus.android.data.model.CymbalMovie?,
    caption: String,
    onCaptionChange: (String) -> Unit,
    isPosting: Boolean,
    onPost: () -> Unit,
    mentionSuggestions: List<CymbalUser> = emptyList(),
    onMentionSelected: (CymbalUser) -> Unit = {},
    captionMode: String = "text",
    onCaptionModeChange: (String) -> Unit = {},
    voiceRecorderState: fm.corus.android.ui.components.VoiceNoteRecorderState? = null,
) {
    val imageURL = if (mediaType == MediaType.TRACK) selectedTrack?.albumArtURL else selectedMovie?.posterURL
    val title = if (mediaType == MediaType.TRACK) selectedTrack?.name.orEmpty() else selectedMovie?.title.orEmpty()
    val subtitle = if (mediaType == MediaType.TRACK) selectedTrack?.artistName.orEmpty() else selectedMovie?.directorName.orEmpty()
    val hasPreview = mediaType == MediaType.TRACK && selectedTrack?.previewUrl != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CorusSpacing.lg),
    ) {
        // ── Selected media row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = imageURL,
                contentDescription = title,
                modifier = Modifier
                    .size(CorusSpacing.albumArtThumbnail) // 56dp
                    .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(CorusSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = CorusFont.songTitle,
                    color = CorusColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = CorusFont.artistName,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasPreview) {
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CorusColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play preview",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CorusSpacing.lg))

        // ── Caption mode toggle (Text / Voice) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            FilterChip(
                selected = captionMode == "text",
                onClick = { onCaptionModeChange("text") },
                label = { Text("Text", style = CorusFont.buttonSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CorusColors.Accent.copy(alpha = 0.15f),
                    selectedLabelColor = CorusColors.Accent,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    selectedBorderColor = CorusColors.Accent,
                    enabled = true,
                    selected = captionMode == "text",
                ),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            FilterChip(
                selected = captionMode == "voice",
                onClick = { onCaptionModeChange("voice") },
                label = { Text("Voice", style = CorusFont.buttonSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CorusColors.Accent.copy(alpha = 0.15f),
                    selectedLabelColor = CorusColors.Accent,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    selectedBorderColor = CorusColors.Accent,
                    enabled = true,
                    selected = captionMode == "voice",
                ),
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        if (captionMode == "voice" && voiceRecorderState != null) {
            // ── Voice note recorder ──
            VoiceNoteRecorderView(
                recorderState = voiceRecorderState,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
        // ── Caption text field ──
        OutlinedTextField(
            value = caption,
            onValueChange = onCaptionChange,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 120.dp),
            placeholder = {
                Text(
                    text = "Add a caption...",
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                )
            },
            maxLines = 8,
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
        )

        // Character counter (visible at 650+)
        if (caption.length >= 650) {
            Text(
                text = "${caption.length}/700",
                style = CorusFont.caption,
                color = if (caption.length >= 700) CorusColors.Error else CorusColors.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CorusSpacing.xs),
                textAlign = TextAlign.End,
            )
        }

        // ── Mention suggestions ──
        if (mentionSuggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CorusSpacing.xs),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CorusColors.CardBackground),
            ) {
                Column {
                    mentionSuggestions.forEachIndexed { index, user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMentionSelected(user) }
                                .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatarView(avatarURL = user.avatarURL, size = 28.dp)
                            Spacer(modifier = Modifier.width(CorusSpacing.sm))
                            Text(
                                text = user.username,
                                style = CorusFont.songTitle,
                                color = CorusColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.width(CorusSpacing.xs))
                            Text(
                                text = user.displayName,
                                style = CorusFont.caption,
                                color = CorusColors.Secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (index < mentionSuggestions.lastIndex) {
                            HorizontalDivider(color = CorusColors.Divider)
                        }
                    }
                }
            }
        }
        } // end else (text mode)

        Spacer(modifier = Modifier.weight(1f))

        // ── Post button ──
        Button(
            onClick = onPost,
            enabled = !isPosting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CorusSpacing.xxl),
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            colors = ButtonDefaults.buttonColors(
                containerColor = CorusColors.Accent,
                disabledContainerColor = CorusColors.Accent.copy(alpha = 0.5f),
            ),
            contentPadding = PaddingValues(vertical = CorusSpacing.md),
        ) {
            if (isPosting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "Post",
                    style = CorusFont.button,
                    color = Color.White,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// Media Type Chip
// ════════════════════════════════════════════════════════════

@Composable
private fun MediaTypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = CorusFont.buttonSmall,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CorusColors.Accent.copy(alpha = 0.15f),
            selectedLabelColor = CorusColors.Accent,
            selectedLeadingIconColor = CorusColors.Accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            selectedBorderColor = CorusColors.Accent,
            enabled = true,
            selected = selected,
        ),
    )
}
