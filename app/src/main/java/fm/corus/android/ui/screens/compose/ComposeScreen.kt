package fm.corus.android.ui.screens.compose

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.MediaType
import fm.corus.android.data.model.TrendingMovie
import fm.corus.android.data.model.TrendingSong
import fm.corus.android.ui.components.FilmSearchResultRow
import fm.corus.android.ui.components.SkeletonFilmRow
import fm.corus.android.ui.components.SkeletonSongRow
import fm.corus.android.ui.components.TrophyCelebrationView
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.VoiceNoteRecorderView
import fm.corus.android.ui.components.applyMention
import fm.corus.android.ui.components.rememberVoiceNoteRecorderState
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onDismiss: () -> Unit = {},
    movieModeEnabled: Boolean = false,
    preSelectedTrackId: String? = null,
    preSelectedMovieId: String? = null,
    viewModel: ComposeViewModel = hiltViewModel(),
) {
    BackHandler { onDismiss() }

    val selectedTrack by viewModel.selectedTrack.collectAsState()
    val selectedMovie by viewModel.selectedMovie.collectAsState()
    val isPosting by viewModel.isPosting.collectAsState()
    val postSuccess by viewModel.postSuccess.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val filmResults by viewModel.filmResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()
    val showTrophy by viewModel.showTrophy.collectAsState()
    val trophyPost by viewModel.trophyPost.collectAsState()
    val showPostLimitPaywall by viewModel.showPostLimitPaywall.collectAsState()
    val trendingSongs by viewModel.trendingSongs.collectAsState()
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val isLoadingPreSelection by viewModel.isLoadingPreSelection.collectAsState()
    val repostedFromUsername by viewModel.repostedFromUsername.collectAsState()
    val showRepostAttribution by viewModel.showRepostAttribution.collectAsState()
    var mediaType by remember { mutableStateOf(if (movieModeEnabled) MediaType.MOVIE else MediaType.TRACK) }
    var searchQuery by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf(TextFieldValue("")) }
    var captionMode by remember { mutableStateOf("text") } // "text" or "voice"
    val voiceRecorderState = rememberVoiceNoteRecorderState()
    val nowPlayingState by viewModel.nowPlayingState.collectAsState()
    val previewLoadingTrackId by viewModel.previewLoadingTrackId.collectAsState()

    val hasSelection = selectedTrack != null || selectedMovie != null || isLoadingPreSelection
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(postSuccess) {
        if (postSuccess) {
            onDismiss()
        }
    }

    // Hide the soft keyboard when the post-limit paywall opens so the sheet isn't
    // covered by it (mirrors iOS, which clears caption focus before showing the offer).
    LaunchedEffect(showPostLimitPaywall) {
        if (showPostLimitPaywall) {
            keyboardController?.hide()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CorusColors.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ── Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
            ) {
                // Left: back chevron (only in compose mode, not when pre-selected or reposting)
                if (hasSelection && preSelectedTrackId == null && preSelectedMovieId == null && repostedFromUsername == null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.compose_cd_back),
                        tint = CorusColors.Secondary,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(20.dp)
                            .clickable {
                                viewModel.clearSelectionKeepingResults()
                            },
                    )
                }

                // Center: title
                Text(
                    text = stringResource(R.string.compose_title),
                    style = CorusFont.displayName,
                    color = CorusColors.Text,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Right: Cancel button
                Text(
                    text = stringResource(R.string.compose_cancel),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { onDismiss() },
                )
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

            // Animated transition between search and compose (push from right like iOS)
            AnimatedContent(
                targetState = hasSelection,
                transitionSpec = {
                    if (targetState) {
                        // Search → Compose: slide in from right
                        (slideInHorizontally(tween(250)) { it } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(tween(250)) { -it / 3 } + fadeOut(tween(150)))
                    } else {
                        // Compose → Search: slide out to right
                        (slideInHorizontally(tween(250)) { -it / 3 } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(tween(250)) { it } + fadeOut(tween(150)))
                    }
                },
                label = "search_compose_transition",
            ) { selected ->
                if (!selected) {
                    SearchModeContent(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { query ->
                            searchQuery = query
                            if (query.length >= 2) {
                                viewModel.search(query, mediaType)
                            }
                        },
                        mediaType = mediaType,
                        onMediaTypeChange = { newType ->
                            mediaType = newType
                            viewModel.clearSelection()
                            searchQuery = ""
                        },
                        isSearching = isSearching,
                        searchResults = searchResults,
                        filmResults = filmResults,
                        onResultClick = { result ->
                            viewModel.selectResult(result, mediaType)
                        },
                        onFilmClick = { movie ->
                            viewModel.selectFilmResult(movie)
                        },
                        onPreviewTap = { trackId ->
                            viewModel.toggleSearchResultPreview(trackId)
                        },
                        nowPlayingTrackId = if (nowPlayingState.isPlaying) nowPlayingState.trackId else null,
                        previewLoadingTrackId = previewLoadingTrackId,
                        trendingSongs = trendingSongs,
                        trendingMovies = trendingMovies,
                        isLoadingTrending = isLoadingTrending,
                        onTrendingSongClick = { viewModel.selectTrendingSong(it) },
                        onTrendingMovieClick = { viewModel.selectTrendingMovie(it) },
                        nowPlaying = viewModel.nowPlayingManager,
                    )
                } else if (selectedTrack != null || selectedMovie != null) {
                    ComposeModeContent(
                        mediaType = mediaType,
                        selectedTrack = selectedTrack,
                        selectedMovie = selectedMovie,
                        caption = caption,
                        onCaptionChange = { newCaption ->
                            val trimmed = if (newCaption.text.length > 700) {
                                newCaption.copy(text = newCaption.text.take(700))
                            } else newCaption
                            val textChanged = trimmed.text != caption.text
                            caption = trimmed
                            if (textChanged) viewModel.checkForMention(trimmed.text, trimmed.selection.start)
                        },
                        isPosting = isPosting,
                        onPost = {
                            viewModel.createPost(
                                caption = if (captionMode == "text") caption.text else "",
                                mediaType = mediaType,
                                voiceNoteData = if (captionMode == "voice") voiceRecorderState.audioData else null,
                            )
                        },
                        mentionSuggestions = mentionSuggestions,
                        onMentionSelected = { user ->
                            caption = applyMention(caption, user.username)
                            viewModel.clearMentionSuggestions()
                        },
                        captionMode = captionMode,
                        onCaptionModeChange = { captionMode = it },
                        voiceRecorderState = voiceRecorderState,
                        onVoiceNoteRecorded = { viewModel.analyticsService.logVoiceNoteRecorded() },
                        isPreviewPlaying = selectedTrack != null && nowPlayingState.trackId == selectedTrack?.id && nowPlayingState.isPlaying,
                        isPreviewLoading = selectedTrack != null && previewLoadingTrackId == selectedTrack?.id,
                        onAlbumArtTap = {
                            selectedTrack?.let { viewModel.togglePreview(it) }
                        },
                        repostedFromUsername = repostedFromUsername,
                        showRepostAttribution = showRepostAttribution,
                        onShowRepostAttributionChange = { viewModel.setShowRepostAttribution(it) },
                    )
                } else {
                    // Pre-selected track/movie is loading — show empty placeholder
                    // so the search mode doesn't flash briefly
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = CorusColors.Secondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
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

    // Post limit reached inside compose — show the Cymbal Club offer sheet.
    // Triggered when the server confirms the user has hit the rolling 24h limit at submit time.
    if (showPostLimitPaywall) {
        val paywallSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.dismissPostLimitPaywall()
                onDismiss()
            },
            sheetState = paywallSheetState,
            containerColor = CorusColors.Background,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            CorusSystemBars()
            fm.corus.android.ui.screens.subscription.CymbalClubOfferSheet(
                source = fm.corus.android.ui.screens.subscription.PaywallSource.POST_LIMIT,
                onDismiss = {
                    viewModel.dismissPostLimitPaywall()
                    onDismiss()
                },
            )
        }
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
    onMediaTypeChange: (MediaType) -> Unit,
    isSearching: Boolean,
    searchResults: List<SearchResultItem>,
    filmResults: List<CymbalMovie>,
    onResultClick: (SearchResultItem) -> Unit,
    onFilmClick: (CymbalMovie) -> Unit,
    onPreviewTap: (String) -> Unit,
    nowPlayingTrackId: String?,
    previewLoadingTrackId: String?,
    trendingSongs: List<TrendingSong>,
    trendingMovies: List<TrendingMovie>,
    isLoadingTrending: Boolean,
    onTrendingSongClick: (TrendingSong) -> Unit,
    onTrendingMovieClick: (TrendingMovie) -> Unit,
    nowPlaying: fm.corus.android.domain.NowPlayingManager,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollDismissConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    keyboardController?.hide()
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().nestedScroll(scrollDismissConnection)) {
        // ── Songs / Films segmented toggle ──
        SegmentedToggle(
            options = listOf(stringResource(R.string.compose_segment_songs), stringResource(R.string.compose_segment_films)),
            selectedIndex = if (mediaType == MediaType.TRACK) 0 else 1,
            onSelected = { index ->
                onMediaTypeChange(if (index == 0) MediaType.TRACK else MediaType.MOVIE)
            },
            modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        )

        // ── Search bar with icon ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg)
                .background(CorusColors.CardBackground, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = CorusColors.Tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                cursorBrush = SolidColor(CorusColors.Accent),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = if (mediaType == MediaType.TRACK) stringResource(R.string.compose_search_song) else stringResource(R.string.compose_search_film),
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                        )
                    }
                    innerTextField()
                },
            )
        }

        Spacer(modifier = Modifier.height(CorusSpacing.sm))

        if (searchQuery.isNotEmpty()) {
            // ── Search results ──
            if (isSearching) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(8) { index ->
                        if (mediaType == MediaType.MOVIE) {
                            SkeletonFilmRow()
                        } else {
                            SkeletonSongRow()
                        }
                        if (index < 7) {
                            HorizontalDivider(
                                color = CorusColors.Divider,
                                modifier = Modifier.padding(start = 72.dp),
                            )
                        }
                    }
                }
            } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (mediaType == MediaType.MOVIE) {
                    itemsIndexed(filmResults) { index, movie ->
                        FilmSearchResultRow(
                            movie = movie,
                            onClick = { onFilmClick(movie) },
                        )
                        if (index < filmResults.lastIndex) {
                            HorizontalDivider(
                                color = CorusColors.Divider,
                                modifier = Modifier.padding(start = 72.dp),
                            )
                        }
                    }
                } else {
                    itemsIndexed(searchResults) { index, result ->
                        SearchResultRow(
                            imageURL = result.imageURL,
                            title = result.title,
                            subtitle = result.subtitle,
                            trailingText = result.trailingText,
                            showPlayOverlay = result.showPlayOverlay,
                            isPlaying = result.showPlayOverlay && nowPlayingTrackId == result.id,
                            isLoading = result.showPlayOverlay && previewLoadingTrackId == result.id,
                            isSoundCloud = result.isSoundCloud,
                            onAlbumArtTap = if (result.showPlayOverlay) {{ onPreviewTap(result.id) }} else null,
                            onClick = { onResultClick(result) },
                        )
                        if (index < searchResults.lastIndex) {
                            HorizontalDivider(
                                color = CorusColors.Divider,
                                modifier = Modifier.padding(start = 72.dp),
                            )
                        }
                    }
                }
            }
            }
        } else {
            // ── Trending content (when no search query) ──
            if (isLoadingTrending) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CorusSpacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = CorusColors.Accent,
                        strokeWidth = 2.dp,
                    )
                }
            } else if (mediaType == MediaType.TRACK && trendingSongs.isNotEmpty()) {
                TrendingSongsSection(
                    songs = trendingSongs,
                    onSongClick = onTrendingSongClick,
                    nowPlaying = nowPlaying,
                )
            } else if (mediaType == MediaType.MOVIE && trendingMovies.isNotEmpty()) {
                TrendingMoviesSection(
                    movies = trendingMovies,
                    onMovieClick = onTrendingMovieClick,
                )
            }
        }
    }
}

// ── Segmented Toggle (matches iOS Picker .segmented) ──

@Composable
private fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CorusColors.CardBackground, RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .padding(CorusSpacing.xxs),
    ) {
        options.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (index == selectedIndex) {
                            Modifier.background(CorusColors.SegmentedSelected, RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelected(index) }
                    .padding(vertical = CorusSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = CorusFont.bodyMedium,
                    color = if (index == selectedIndex) CorusColors.Text else CorusColors.Secondary,
                )
            }
        }
    }
}

// ── Trending Songs Section ──

@Composable
private fun TrendingSongsSection(
    songs: List<TrendingSong>,
    onSongClick: (TrendingSong) -> Unit,
    nowPlaying: fm.corus.android.domain.NowPlayingManager,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(top = CorusSpacing.sm, bottom = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.compose_trending_songs),
                    style = CorusFont.sectionHeader,
                    color = CorusColors.Secondary,
                )
            }
        }

        itemsIndexed(songs) { index, song ->
            TrendingSongRow(
                song = song,
                nowPlaying = nowPlaying,
                onClick = { onSongClick(song) },
            )
            if (index < songs.lastIndex) {
                HorizontalDivider(
                    color = CorusColors.Divider,
                    modifier = Modifier.padding(start = 72.dp),
                )
            }
        }
    }
}

// ── Trending Movies Section ──

@Composable
private fun TrendingMoviesSection(
    movies: List<TrendingMovie>,
    onMovieClick: (TrendingMovie) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(top = CorusSpacing.sm, bottom = CorusSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = CorusColors.Accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.compose_trending_films),
                    style = CorusFont.sectionHeader,
                    color = CorusColors.Secondary,
                )
            }
        }

        itemsIndexed(movies) { index, movie ->
            TrendingMovieRow(
                movie = movie,
                onClick = { onMovieClick(movie) },
            )
            if (index < movies.lastIndex) {
                HorizontalDivider(
                    color = CorusColors.Divider,
                    modifier = Modifier.padding(start = 72.dp),
                )
            }
        }
    }
}

// ── Trending Song Row ──

@Composable
private fun TrendingSongRow(
    song: TrendingSong,
    nowPlaying: fm.corus.android.domain.NowPlayingManager,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .defaultMinSize(minHeight = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${song.rank}",
            style = CorusFont.engagementCount,
            color = CorusColors.Secondary,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        fm.corus.android.ui.components.SongPreviewArtwork(
            track = song.track,
            nowPlaying = nowPlaying,
            size = CorusSpacing.albumArtSearch,
            cornerRadius = CorusSpacing.cornerRadius,
            contentDescription = song.track.name,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.track.name,
                style = CorusFont.body,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.track.artistName,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${song.cymbalCount}",
            style = CorusFont.captionMedium,
            color = CorusColors.Secondary,
        )
    }
}

// ── Trending Movie Row ──

@Composable
private fun TrendingMovieRow(
    movie: TrendingMovie,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
            .defaultMinSize(minHeight = CorusSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${movie.rank}",
            style = CorusFont.engagementCount,
            color = CorusColors.Secondary,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        AsyncImage(
            model = movie.posterURL,
            contentDescription = movie.movieTitle,
            modifier = Modifier
                .width(40.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.movieTitle,
                style = CorusFont.body,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs)) {
                Text(
                    text = movie.directorName,
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (movie.releaseYear.isNotEmpty()) {
                    Text(
                        text = "(${movie.releaseYear})",
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                    )
                }
            }
        }
        Text(
            text = "${movie.cymbalCount}",
            style = CorusFont.captionMedium,
            color = CorusColors.Secondary,
        )
    }
}

@Composable
private fun SearchResultRow(
    imageURL: String?,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    showPlayOverlay: Boolean = false,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    isSoundCloud: Boolean = false,
    onAlbumArtTap: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art with optional play overlay
        Box(
            modifier = Modifier
                .size(CorusSpacing.albumArtSearch) // 48dp
                .then(if (onAlbumArtTap != null) Modifier.clickable { onAlbumArtTap() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageURL,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                contentScale = ContentScale.Crop,
            )
            if (isSoundCloud) {
                fm.corus.android.ui.components.SoundCloudBadgeOverlay(
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            if (showPlayOverlay) {
                if (isPlaying || isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.compose_cd_pause),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.compose_cd_play_preview),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(CorusSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CorusFont.body,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingText != null) {
            Spacer(modifier = Modifier.width(CorusSpacing.sm))
            Text(
                text = trailingText,
                style = CorusFont.caption,
                color = CorusColors.Secondary,
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
    caption: TextFieldValue,
    onCaptionChange: (TextFieldValue) -> Unit,
    isPosting: Boolean,
    onPost: () -> Unit,
    mentionSuggestions: List<CymbalUser> = emptyList(),
    onMentionSelected: (CymbalUser) -> Unit = {},
    captionMode: String = "text",
    onCaptionModeChange: (String) -> Unit = {},
    voiceRecorderState: fm.corus.android.ui.components.VoiceNoteRecorderState? = null,
    onVoiceNoteRecorded: () -> Unit = {},
    isPreviewPlaying: Boolean = false,
    isPreviewLoading: Boolean = false,
    onAlbumArtTap: () -> Unit = {},
    repostedFromUsername: String? = null,
    showRepostAttribution: Boolean = true,
    onShowRepostAttributionChange: (Boolean) -> Unit = {},
) {
    val imageURL = if (mediaType == MediaType.TRACK) (selectedTrack?.albumArtLargeURL ?: selectedTrack?.albumArtURL) else selectedMovie?.posterURL
    val title = if (mediaType == MediaType.TRACK) selectedTrack?.name.orEmpty() else selectedMovie?.title.orEmpty()
    val unknownDirector = stringResource(R.string.compose_director_unknown)
    val subtitle = if (mediaType == MediaType.TRACK) {
        selectedTrack?.artistName.orEmpty()
    } else {
        buildString {
            val director = selectedMovie?.directorName?.ifEmpty { unknownDirector } ?: unknownDirector
            append(director)
            val year = selectedMovie?.year.orEmpty()
            if (year.isNotEmpty()) append("  $year")
        }
    }

    val captionFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(captionMode) {
        if (captionMode == "text") {
            captionFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = CorusSpacing.lg),
    ) {
        // ── Selected media row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(CorusSpacing.albumArtThumbnail) // 56dp
                    .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                    .clickable(enabled = mediaType == MediaType.TRACK) { onAlbumArtTap() },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageURL,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (mediaType == MediaType.TRACK) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isPreviewLoading -> CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = Color.White,
                            )
                            isPreviewPlaying -> Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.compose_cd_pause_preview),
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                            else -> Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.comment_attachment_play_preview),
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
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
        }

        // ── Repost attribution toggle (only visible when reposting) ──
        if (repostedFromUsername != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Repeat,
                    contentDescription = null,
                    tint = CorusColors.Text,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.xs))
                Text(
                    text = stringResource(R.string.compose_reposted_from_format, repostedFromUsername),
                    style = CorusFont.bodyMedium,
                    color = CorusColors.Text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    checked = showRepostAttribution,
                    onCheckedChange = onShowRepostAttributionChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CorusColors.Accent,
                    ),
                )
            }
        }

        // ── Caption mode toggle (Text / Voice) — segmented style like iOS ──
        SegmentedToggle(
            options = listOf(stringResource(R.string.compose_segment_text), stringResource(R.string.compose_segment_voice)),
            selectedIndex = if (captionMode == "text") 0 else 1,
            onSelected = { index ->
                onCaptionModeChange(if (index == 0) "text" else "voice")
            },
        )

        Spacer(modifier = Modifier.height(CorusSpacing.md))

        if (captionMode == "voice" && voiceRecorderState != null) {
            // ── Voice note recorder ──
            VoiceNoteRecorderView(
                recorderState = voiceRecorderState,
                modifier = Modifier.fillMaxWidth(),
                onRecorded = onVoiceNoteRecorded,
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
        // ── Caption text field (borderless, like iOS TextEditor) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (caption.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.compose_caption_placeholder),
                    style = CorusFont.body,
                    color = CorusColors.Secondary.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = CorusSpacing.xs),
                )
            }
            BasicTextField(
                value = caption,
                onValueChange = onCaptionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = CorusSpacing.xs)
                    .focusRequester(captionFocusRequester),
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
                maxLines = Int.MAX_VALUE,
                cursorBrush = SolidColor(CorusColors.Accent),
            )
        }

        // Character counter (visible at 650+)
        if (caption.text.length >= 650) {
            Text(
                text = stringResource(R.string.compose_char_count_format, caption.text.length),
                style = CorusFont.caption,
                color = if (caption.text.length >= 700) CorusColors.Error else CorusColors.Secondary,
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
                            UserAvatarView(avatarURL = user.avatarURL, displayName = user.displayName, size = 28.dp)
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

        // ── Post button — "SET YOUR CORUS →" like iOS ──
        Button(
            onClick = {
                keyboardController?.hide()
                onPost()
            },
            enabled = !isPosting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = CorusSpacing.lg),
            shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
            colors = ButtonDefaults.buttonColors(
                containerColor = CorusColors.Accent,
                disabledContainerColor = CorusColors.Accent.copy(alpha = 0.5f),
            ),
            contentPadding = PaddingValues(vertical = CorusSpacing.lg),
        ) {
            if (isPosting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
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
