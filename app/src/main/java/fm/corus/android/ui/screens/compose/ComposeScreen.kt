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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
import fm.corus.android.ui.components.applyHashtag
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
    val selectedTrack by viewModel.selectedTrack.collectAsState()
    val selectedMovie by viewModel.selectedMovie.collectAsState()
    val isPosting by viewModel.isPosting.collectAsState()
    val postSuccess by viewModel.postSuccess.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val filmResults by viewModel.filmResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchHasError by viewModel.searchHasError.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()
    val hashtagSuggestions by viewModel.hashtagSuggestions.collectAsState()
    val showTrophy by viewModel.showTrophy.collectAsState()
    val trophyPost by viewModel.trophyPost.collectAsState()
    val showPostLimitPaywall by viewModel.showPostLimitPaywall.collectAsState()
    val showHardCapAlert by viewModel.showHardCapAlert.collectAsState()
    val showApproachingCapAlert by viewModel.showApproachingCapAlert.collectAsState()
    val approachingCapRemaining by viewModel.approachingCapRemaining.collectAsState()
    val trendingSongs by viewModel.trendingSongs.collectAsState()
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val isLoadingTrending by viewModel.isLoadingTrending.collectAsState()
    val isLoadingPreSelection by viewModel.isLoadingPreSelection.collectAsState()
    val repostedFromUsername by viewModel.repostedFromUsername.collectAsState()
    val showRepostAttribution by viewModel.showRepostAttribution.collectAsState()
    val commentsAudience by viewModel.commentsAudience.collectAsState()
    // Post drafts
    val drafts by viewModel.drafts.collectAsState()
    val editingDraftId by viewModel.editingDraftId.collectAsState()
    val resumedVoiceNoteURL by viewModel.resumedVoiceNoteURL.collectAsState()
    val attachmentUnavailable by viewModel.attachmentUnavailable.collectAsState()
    val savingDraft by viewModel.savingDraft.collectAsState()
    var mediaType by remember { mutableStateOf(if (movieModeEnabled) MediaType.MOVIE else MediaType.TRACK) }
    var searchQuery by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf(TextFieldValue("")) }
    var captionMode by remember { mutableStateOf("text") } // "text" or "voice"
    var showDraftsSheet by remember { mutableStateOf(false) }
    var showExitSheet by remember { mutableStateOf(false) }
    // Which affordance opened the exit sheet: the up/back chevron (return to the
    // picker, composer stays open) vs. the top-level Cancel/close (leave the
    // composer entirely). Mirrors iOS `exitToPicker`.
    var exitToPicker by remember { mutableStateOf(false) }
    val voiceRecorderState = rememberVoiceNoteRecorderState()
    val nowPlayingState by viewModel.nowPlayingState.collectAsState()
    val previewLoadingTrackId by viewModel.previewLoadingTrackId.collectAsState()

    val hasSelection = selectedTrack != null || selectedMovie != null || isLoadingPreSelection
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    // Dismiss the soft keyboard, then run [reveal] only once it has fully
    // retracted: we watch the animated IME inset and wait for it to reach 0 (with
    // a short timeout as a safety net if it never reports fully-hidden), so a
    // bottom sheet's slide-up never races the keyboard's slide-down and flashes
    // mid-screen. If no keyboard is up the inset is already 0 and [reveal] runs
    // immediately. Shared by the exit-confirm sheet and the drafts sheet.
    fun dismissKeyboardThen(reveal: () -> Unit) {
        keyboardController?.hide()
        scope.launch {
            withTimeoutOrNull(500) {
                snapshotFlow { imeInsets.getBottom(density) }.first { it == 0 }
            }
            reveal()
        }
    }

    // Fetch the user's drafts once so the "Drafts (N)" entry can surface. No
    // feature flag: the entry only appears when the user has >=1 draft.
    LaunchedEffect(Unit) { viewModel.loadDrafts() }

    // In-screen toast events for draft delete outcomes / save FAILURE. The
    // "Draft saved" success is shown via the app-wide ToastManager instead (so it
    // survives the composer closing — mirrors iOS's top toast).
    LaunchedEffect(Unit) {
        viewModel.draftToast.collect { resId ->
            android.widget.Toast.makeText(context, context.getString(resId), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Download a resumed draft's saved voice note INTO the recorder so it shows
    // the normal recorded-note UI (play/scrub/delete) rather than a special row.
    // Driven imperatively from the drafts-sheet tap (NOT a resumedVoiceNoteURL
    // state key): re-resuming the SAME draft leaves that URL unchanged — and a
    // prior save already set it to the uploaded URL — so a key-driven effect
    // would silently skip the reload and drop the note (only a fresh screen,
    // starting from null, would ever transition). beginLoadingExisting shows a
    // spinner immediately so the idle "Record" control never flashes; the recorder
    // is cleared by the caller first, so there's no live recording to clobber.
    // Mirrors iOS resumeDraft.
    fun loadResumedVoiceNote(url: String) {
        voiceRecorderState.beginLoadingExisting()
        scope.launch {
            val bytes = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.net.URL(url).openStream().use { it.readBytes() }
                }
            }.getOrNull()
            if (bytes != null) {
                voiceRecorderState.loadExisting(context, bytes)
            } else {
                voiceRecorderState.cancelLoadingExisting()
            }
        }
    }

    // Current voice state for the dirty-signature: a freshly recorded note wins
    // ("new"); a note loaded from the resumed draft is unchanged ("saved"); a
    // resumed URL still downloading is also "saved"; otherwise "none"; "text"
    // when not in voice mode.
    val voiceState = if (captionMode == "voice") {
        when {
            voiceRecorderState.hasRecording ->
                if (voiceRecorderState.loadedFromExisting) "saved" else "new"
            voiceRecorderState.isLoadingExisting -> "saved"
            resumedVoiceNoteURL != null -> "saved"
            else -> "none"
        }
    } else "text"

    // The save-on-exit sheet appears whenever a song/film is selected and the
    // composer differs from the saved-draft signature — a picked song IS a draft
    // worth keeping, even with no caption yet. A resumed-but-unchanged draft, or
    // the bare picker with nothing selected, leaves silently. Reposts never save.
    val isRepost = repostedFromUsername != null
    val hasSelectionForDraft = selectedTrack != null || selectedMovie != null
    val isDirty = viewModel.isDraftDirty(
        mediaType = mediaType,
        trackId = selectedTrack?.id,
        movieId = selectedMovie?.id,
        caption = if (captionMode == "text") caption.text else "",
        captionMode = captionMode,
        voiceState = voiceState,
    )
    val canSaveDraft = !isRepost && hasSelectionForDraft && isDirty &&
        preSelectedTrackId == null && preSelectedMovieId == null

    // Drafts entry count — hidden while reposting or pre-selected (no drafts UI
    // in those flows), otherwise the live count.
    val draftsCount = if (isRepost || preSelectedTrackId != null || preSelectedMovieId != null) 0 else drafts.size
    val draftsEntryAccessibilityLabel =
        "${stringResource(R.string.compose_drafts_title)} ($draftsCount)"

    // voiceNoteData to hand to post/save: a freshly recorded note (bytes to
    // upload); null when the note was loaded from the resumed draft (reuse the
    // stored URL — don't re-upload identical audio) or when there's no note.
    fun freshVoiceNoteData(): ByteArray? =
        if (captionMode == "voice" && !voiceRecorderState.loadedFromExisting) {
            voiceRecorderState.audioData
        } else null

    // Whether the up/back affordance is at the SELECTED step (compose mode) with
    // a picker to return to — i.e. not pre-selected and not a repost.
    val backReturnsToPicker = hasSelectionForDraft &&
        preSelectedTrackId == null && preSelectedMovieId == null && !isRepost

    // The up/back affordance (and the Android system back button while in compose
    // mode): when there's a keepable draft, confirm; Discard/Save returns to the
    // PICKER (composer stays open). Otherwise clear the selection back to the
    // picker without prompting. Mirrors iOS's back-chevron routing.
    fun handleBackToPicker() {
        if (canSaveDraft) {
            exitToPicker = true
            dismissKeyboardThen { showExitSheet = true }
        } else {
            viewModel.clearSelectionKeepingResults()
        }
    }

    // The top-level Cancel/close (and the system back button at the picker step):
    // when there's a keepable draft, confirm; Discard/Save DISMISSES the
    // composer. Otherwise dismiss silently. Mirrors iOS's Cancel routing.
    fun handleCancel() {
        if (canSaveDraft) {
            exitToPicker = false
            dismissKeyboardThen { showExitSheet = true }
        } else {
            onDismiss()
        }
    }

    // Wire the system back button to the same behavior as the up affordance: at
    // the selected step it routes to the picker; at the bare picker it cancels.
    BackHandler {
        if (backReturnsToPicker) handleBackToPicker() else handleCancel()
    }

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
                // Left slot (nav): at the selected step, the back chevron (routes
                // to the picker, confirming a keepable draft first). At the pick
                // step, a low-emphasis drafts entry (document icon + count) shown
                // only when the user has >=1 draft — the Material equivalent of
                // iOS's icon+count nav button.
                if (backReturnsToPicker) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.compose_cd_back),
                        tint = CorusColors.Secondary,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(20.dp)
                            .clickable { handleBackToPicker() },
                    )
                } else if (!hasSelection && draftsCount > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
                            .clickable { dismissKeyboardThen { showDraftsSheet = true } }
                            .padding(horizontal = CorusSpacing.xs, vertical = CorusSpacing.xxs)
                            .semantics {
                                contentDescription = draftsEntryAccessibilityLabel
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileCopy,
                            contentDescription = null,
                            tint = CorusColors.Secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "$draftsCount",
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                        )
                    }
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
                        .clickable { handleCancel() },
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
                            // Keep the query: switching tabs mid-search usually
                            // means "same search, other medium" (typed a film
                            // title into the Songs tab) — re-run it against the
                            // new type instead of making the user retype.
                            if (searchQuery.length >= 2) {
                                viewModel.search(searchQuery, newType)
                            }
                        },
                        isSearching = isSearching,
                        searchResults = searchResults,
                        filmResults = filmResults,
                        searchHasError = searchHasError,
                        isConnected = isConnected,
                        onRetrySearch = {
                            viewModel.search(searchQuery, mediaType)
                        },
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
                                // Reuse the stored URL for an unchanged resumed
                                // note (freshVoiceNoteData() returns null then).
                                voiceNoteData = freshVoiceNoteData(),
                            )
                        },
                        mentionSuggestions = mentionSuggestions,
                        onMentionSelected = { user ->
                            caption = applyMention(caption, user.username)
                            viewModel.clearMentionSuggestions()
                        },
                        hashtagSuggestions = hashtagSuggestions,
                        onHashtagSelected = { tag ->
                            caption = applyHashtag(caption, tag.name)
                            viewModel.clearHashtagSuggestions()
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
                        commentControlsOnPosts = viewModel.commentControlsOnPosts,
                        commentsAudience = commentsAudience,
                        onCommentsAudienceChange = viewModel::setCommentsAudience,
                        attachmentUnavailable = attachmentUnavailable,
                        onChooseAnother = { viewModel.clearUnavailableAttachment() },
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

    // Hard cap (6h ceiling, applies to subscribers too) — "You're on a roll."
    if (showHardCapAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissHardCapAlert() },
            title = { Text("You're on a roll.") },
            text = { Text("The Corus servers need a cooldown. Come back in 6 hours to post more.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissHardCapAlert() }) { Text("OK") }
            },
        )
    }

    // Approaching cap — paid users only, throttled to once per 6h window.
    if (showApproachingCapAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissApproachingCapAlert() },
            title = { Text("You're on a roll.") },
            text = {
                Text(
                    "The Corus servers will need a cooldown soon. " +
                        "$approachingCapRemaining posts left before a 6-hour break.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissApproachingCapAlert() }) { Text("Got it") }
            },
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

    // ── Drafts list sheet ──
    if (showDraftsSheet) {
        val draftsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDraftsSheet = false },
            sheetState = draftsSheetState,
            containerColor = CorusColors.Background,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            CorusSystemBars()
            DraftsSheetContent(
                drafts = drafts,
                onResume = { draft ->
                    // Clear any live recording first so the resumed note replaces it.
                    voiceRecorderState.deleteRecording()
                    val resumed = viewModel.resumeDraft(draft)
                    // Seed the composer's local UI state from the resumed draft.
                    mediaType = resumed.mediaType
                    caption = TextFieldValue(resumed.caption)
                    captionMode = resumed.captionMode
                    showDraftsSheet = false
                    // Load the saved voice note imperatively (see loadResumedVoiceNote)
                    // so re-resuming the same draft still reloads it.
                    val voiceUrl = draft.voiceNoteURL
                    if (resumed.captionMode == "voice" && voiceUrl != null) {
                        loadResumedVoiceNote(voiceUrl)
                    }
                },
                onDelete = { draft -> viewModel.deleteDraft(draft.id) },
            )
        }
    }

    // ── Save-on-exit action sheet — Discard / Save draft / Cancel ──
    if (showExitSheet) {
        val exitSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val draftSavedMsg = stringResource(R.string.compose_draft_saved)
        // After Discard/Save the follow-up "leave" depends on which affordance
        // opened the sheet: the up/back chevron returns to the picker (composer
        // stays open); the top-level Cancel dismisses the composer entirely.
        fun leaveAfterExit() {
            showExitSheet = false
            if (exitToPicker) {
                viewModel.clearSelectionKeepingResults()
                // Start the next pick fresh — drop caption/mode/note like iOS.
                caption = TextFieldValue("")
                captionMode = "text"
                voiceRecorderState.deleteRecording()
            } else {
                onDismiss()
            }
        }
        ModalBottomSheet(
            onDismissRequest = { if (!savingDraft) showExitSheet = false },
            sheetState = exitSheetState,
            containerColor = CorusColors.Background,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            CorusSystemBars()
            ExitDraftSheetContent(
                saving = savingDraft,
                onDiscard = { leaveAfterExit() },
                onSave = {
                    scope.launch {
                        val ok = viewModel.saveCurrentDraft(
                            mediaType = mediaType,
                            caption = caption.text,
                            captionMode = captionMode,
                            voiceNoteData = freshVoiceNoteData(),
                        )
                        if (ok) {
                            // App-wide top toast so it survives the composer/sheet
                            // closing (mirrors iOS's top toast). Uses the same
                            // ToastManager the app shows other global confirmations
                            // through, rendered by ToastHost in MainTabScreen.
                            fm.corus.android.ui.components.ToastManager.show(draftSavedMsg)
                            leaveAfterExit()
                        }
                    }
                },
                onCancel = { if (!savingDraft) showExitSheet = false },
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
    searchHasError: Boolean,
    isConnected: Boolean,
    onRetrySearch: () -> Unit,
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

        // Drafts entry lives in the header nav slot (document icon + count),
        // shown only at the pick step when the user has >=1 draft.

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
            val currentResultsEmpty = if (mediaType == MediaType.MOVIE) filmResults.isEmpty() else searchResults.isEmpty()
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
            } else if (searchHasError && currentResultsEmpty) {
                // Same online-vs-offline branching as the Search tab so the
                // empty state doesn't tell a user with working internet to
                // check their connection.
                fm.corus.android.ui.components.OfflineRetryState(
                    modifier = Modifier.fillMaxSize(),
                    onRetry = onRetrySearch,
                    icon = if (isConnected) Icons.Filled.WarningAmber else Icons.Filled.WifiOff,
                    title = if (isConnected) {
                        stringResource(fm.corus.android.R.string.search_service_unavailable_title)
                    } else {
                        stringResource(fm.corus.android.R.string.feed_offline_title)
                    },
                    subtitle = if (isConnected) {
                        stringResource(fm.corus.android.R.string.search_service_unavailable_subtitle)
                    } else {
                        stringResource(fm.corus.android.R.string.feed_offline_subtitle)
                    },
                )
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
    hashtagSuggestions: List<fm.corus.android.data.model.HashtagSuggestion> = emptyList(),
    onHashtagSelected: (fm.corus.android.data.model.HashtagSuggestion) -> Unit = {},
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
    commentControlsOnPosts: Boolean = false,
    commentsAudience: fm.corus.android.data.model.CommentsAudience = fm.corus.android.data.model.CommentsAudience.EVERYONE,
    onCommentsAudienceChange: (fm.corus.android.data.model.CommentsAudience) -> Unit = {},
    attachmentUnavailable: Boolean = false,
    onChooseAnother: () -> Unit = {},
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
            // A resumed voice draft loads its saved note straight into the
            // recorder (see the resumedVoiceNoteURL LaunchedEffect in
            // ComposeScreen), so this shows the NORMAL recorded-note UI
            // (play/scrub/delete) — or a brief loading placeholder while it
            // downloads — instead of a special "saved voice note" row.
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
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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

        } // end else (text mode)

        // Mention/hashtag suggestions sit at the very bottom (above the keyboard)
        // and REPLACE the audience picker + Set button while active — mirroring
        // iOS. Rendering them inline above the button instead squeezed the
        // weight(1f) caption to zero height (couldn't see what you typed) and
        // overflowed the button (its label vanished) once the keyboard was up.
        val showingSuggestions = captionMode == "text" &&
            (mentionSuggestions.isNotEmpty() || hashtagSuggestions.isNotEmpty())
        if (showingSuggestions) {
            ComposeMentionSuggestionsCard(
                mentionSuggestions = mentionSuggestions,
                onMentionSelected = onMentionSelected,
            )
            ComposeHashtagSuggestionsCard(
                hashtagSuggestions = hashtagSuggestions,
                onHashtagSelected = onHashtagSelected,
            )
            // Small gap so the card isn't flush against the keyboard (iOS parity).
            Spacer(modifier = Modifier.height(CorusSpacing.sm))
        } else {
            // ── Delisted attachment — block posting until re-pick ──
            // A resumed draft whose track/film 404'd on refresh.
            if (attachmentUnavailable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CorusSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.compose_attachment_unavailable),
                        style = CorusFont.caption,
                        color = CorusColors.Error,
                    )
                    Spacer(modifier = Modifier.width(CorusSpacing.xs))
                    Text(
                        text = stringResource(R.string.compose_draft_choose_another),
                        style = CorusFont.captionMedium,
                        color = CorusColors.Error,
                        modifier = Modifier.clickable(onClick = onChooseAnother),
                    )
                }
            }

            if (commentControlsOnPosts) {
                fm.corus.android.ui.components.CommentsAudiencePicker(
                    selection = commentsAudience,
                    onSelect = onCommentsAudienceChange,
                    modifier = Modifier.padding(top = CorusSpacing.xs),
                )
            }

            // ── Post button — "SET YOUR CORUS →" like iOS ──
            Button(
                onClick = {
                    keyboardController?.hide()
                    // Tapping "Set your Corus" mid-recording should upload the
                    // take, not error out. stopRecording() is synchronous and
                    // populates audioData before onPost() reads it.
                    if (voiceRecorderState?.isRecording == true) {
                        voiceRecorderState.stopRecording()
                    }
                    onPost()
                },
                enabled = !isPosting && !attachmentUnavailable,
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
}

// The compose-screen suggestion cards use a rounded Card look (vs. the flat
// list in the comments sheet). Kept as small composables so the bottom of
// ComposeModeContent can swap between them and the Set button.
@Composable
private fun ComposeMentionSuggestionsCard(
    mentionSuggestions: List<CymbalUser>,
    onMentionSelected: (CymbalUser) -> Unit,
) {
    if (mentionSuggestions.isEmpty()) return
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

@Composable
private fun ComposeHashtagSuggestionsCard(
    hashtagSuggestions: List<fm.corus.android.data.model.HashtagSuggestion>,
    onHashtagSelected: (fm.corus.android.data.model.HashtagSuggestion) -> Unit,
) {
    if (hashtagSuggestions.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CorusSpacing.xs),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CorusColors.CardBackground),
    ) {
        Column {
            hashtagSuggestions.forEachIndexed { index, tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHashtagSelected(tag) }
                        .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(CorusColors.Accent.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "#", style = CorusFont.songTitle, color = CorusColors.Accent)
                    }
                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                    Column {
                        Text(
                            text = "#${tag.name}",
                            style = CorusFont.songTitle,
                            color = CorusColors.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (tag.trending || tag.count > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (tag.trending) {
                                    Text(
                                        text = stringResource(R.string.hashtag_suggestion_trending),
                                        style = CorusFont.caption,
                                        color = CorusColors.Accent,
                                        maxLines = 1,
                                    )
                                }
                                if (tag.trending && tag.count > 0) {
                                    Text(
                                        text = " · ",
                                        style = CorusFont.caption,
                                        color = CorusColors.Secondary,
                                    )
                                }
                                if (tag.count > 0) {
                                    val velocityRes = if (tag.count == 1) {
                                        R.string.hashtag_velocity_this_week_one
                                    } else {
                                        R.string.hashtag_velocity_this_week
                                    }
                                    Text(
                                        text = stringResource(velocityRes, tag.count.toString()),
                                        style = CorusFont.caption,
                                        color = CorusColors.Secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                if (index < hashtagSuggestions.lastIndex) {
                    HorizontalDivider(color = CorusColors.Divider)
                }
            }
        }
    }
}
