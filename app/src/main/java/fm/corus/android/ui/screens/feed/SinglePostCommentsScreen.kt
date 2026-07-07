package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fm.corus.android.R
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.ui.navigation.FilmDetailRoute
import fm.corus.android.ui.components.CommentAttachmentCard
import fm.corus.android.ui.components.CommentAttachmentPendingChip
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.PickerMode
import fm.corus.android.ui.components.SongFilmPickerSheet
import fm.corus.android.ui.components.MentionSuggestionsList
import fm.corus.android.ui.components.PostCard
import fm.corus.android.ui.components.ReportContentType
import fm.corus.android.ui.components.ReportSheet
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.applyMention
import fm.corus.android.ui.components.buildMentionAnnotatedString
import fm.corus.android.ui.components.parseMentionQuery
import fm.corus.android.ui.components.TappableMentionText
import kotlinx.coroutines.Job
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.util.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePostCommentsScreen(
    postId: String,
    highlightCommentId: String? = null,
    viewModel: CommentsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (FilmDetailRoute) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    onNavigateToLikes: (String) -> Unit = {},
    onNavigateToCommentLikes: (commentId: String) -> Unit = {},
    onRepost: (CymbalPost) -> Unit = {},
    /** Artist/director pages (artist_pages_enabled) — null while the flag is
     *  off, which keeps post-card artist/director names as plain text. */
    onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)? = null,
    onNavigateToDirector: ((fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit)? = null,
    /** Album page (artist_pages_enabled) — null while the flag is off, which
     *  hides the "…" menu's "Go to Album" row. */
    onNavigateToAlbum: ((fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit)? = null,
) {
    val comments by viewModel.comments.collectAsState()
    val repliesByParent by viewModel.repliesByParent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val likedCommentIds by viewModel.likedCommentIds.collectAsState()
    val post by viewModel.post.collectAsState()
    val engagementStates by viewModel.engagementStates.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val nowPlayingState by viewModel.nowPlayingManager.state.collectAsState()
    val loadingTrackId by viewModel.nowPlayingManager.loadingTrackId.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicService by viewModel.musicServicePreference.current.collectAsState()

    val pendingSong by viewModel.pendingSong.collectAsState()
    val pendingFilm by viewModel.pendingFilm.collectAsState()
    val pendingGif by viewModel.pendingGif.collectAsState()

    var commentText by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showGifPicker by remember { mutableStateOf(false) }
    var showSongFilmPicker by remember { mutableStateOf(false) }
    var pickerInitialMode by remember { mutableStateOf(PickerMode.SONG) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val maxChars = 700
    val showCounter = commentText.text.length >= 650
    val listState = rememberLazyListState()
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()
    val isSearchingMentions by viewModel.isSearchingMentions.collectAsState()
    val editingComment by viewModel.editingComment.collectAsState()
    val commentBlockReason by viewModel.commentBlockReason.collectAsState()
    var reportingComment by remember { mutableStateOf<CymbalComment?>(null) }
    var mentionSearchJob by remember { mutableStateOf<Job?>(null) }
    var menuPost by remember { mutableStateOf<CymbalPost?>(null) }
    var sharePost by remember { mutableStateOf<CymbalPost?>(null) }
    var editCaptionPost by remember { mutableStateOf<CymbalPost?>(null) }
    var deleteConfirmPost by remember { mutableStateOf<CymbalPost?>(null) }
    val backCoverFlipState = fm.corus.android.ui.components.rememberBackCoverFlipState()

    LaunchedEffect(editingComment?.id) {
        val editing = editingComment
        if (editing != null) {
            commentText = TextFieldValue(editing.text, selection = TextRange(editing.text.length))
            focusRequester.requestFocus()
        } else {
            commentText = TextFieldValue("")
        }
    }

    // Highlight flash state — matches iOS 1.5s flash then fade
    var activeHighlightId by remember { mutableStateOf<String?>(null) }
    var hasScrolledToTarget by remember { mutableStateOf(false) }

    LaunchedEffect(postId) {
        viewModel.loadComments(postId)
        viewModel.loadPost(postId)
    }

    // Scroll to target comment once comments are loaded.
    //
    // The LazyColumn emits ONE item per top-level comment — each row renders its
    // own replies inline (see SingleCommentRow), so replies are NOT separate
    // lazy items. We therefore scroll to the row that *contains* the target: the
    // comment's own row, or its parent's row when the target is a reply. (A flat
    // index that counted replies as rows would overshoot and land the
    // highlighted comment off-screen whenever replies exist above it.)
    LaunchedEffect(comments, highlightCommentId) {
        if (highlightCommentId == null || comments.isEmpty() || hasScrolledToTarget) return@LaunchedEffect
        val targetRow = comments.indexOfFirst { it.id == highlightCommentId }
            .takeIf { it >= 0 }
            ?: comments.indexOfFirst { parent ->
                repliesByParent[parent.id]?.any { it.id == highlightCommentId } == true
            }
        if (targetRow >= 0) {
            // +1 for the post header item at index 0.
            listState.animateScrollToItem(targetRow + 1)
            hasScrolledToTarget = true
            activeHighlightId = highlightCommentId
            delay(1500)
            activeHighlightId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_screen_title_Corus), style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    CorusHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.feed_cd_back),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        bottomBar = {
            Column {
                // Audience-locked notice. When the viewer can't write a new
                // comment, swap the composer for an inline notice — but only
                // when they're not editing one of their own existing comments
                // (editing the user's own content is always allowed).
                val lockReason = commentBlockReason
                if (lockReason != null && editingComment == null) {
                    val post = viewModel.post.collectAsState().value
                    fm.corus.android.ui.components.CommentsLockedNotice(
                        reason = lockReason,
                        authorUsername = post?.user?.username,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CorusColors.Background)
                            .navigationBarsPadding()
                            .imePadding(),
                    )
                    return@Column
                }
                // Mention suggestions
                MentionSuggestionsList(
                    users = mentionSuggestions.take(4),
                    onSelect = { user ->
                        commentText = applyMention(commentText, user.username)
                        viewModel.clearMentions()
                    },
                    isSearching = isSearchingMentions,
                )
                // Editing indicator
                if (editingComment != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CorusColors.CardBackground)
                            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.comments_editing_your_comment),
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                viewModel.cancelEditing()
                                commentText = TextFieldValue("")
                                viewModel.clearMentions()
                            },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.comments_cd_cancel_edit), tint = CorusColors.Tertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                // Reply indicator
                if (replyingTo != null && editingComment == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CorusColors.CardBackground)
                            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.comments_replying_to_format, replyingTo?.user?.username ?: ""),
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.clearReply() },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.comments_cd_cancel_reply), tint = CorusColors.Tertiary, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Pending attachment chip (song / film / GIF) — left-aligned.
                if (editingComment == null && (pendingSong != null || pendingFilm != null || pendingGif != null)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
                    ) {
                        CommentAttachmentPendingChip(
                            attachedSong = pendingSong,
                            attachedFilm = pendingFilm,
                            pendingGif = pendingGif,
                            onClear = { viewModel.clearAttachment() },
                            nowPlaying = viewModel.nowPlayingManager,
                        )
                    }
                }

                // Input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CorusColors.Background)
                        .navigationBarsPadding()
                        .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editingComment == null) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(CorusColors.Accent)
                                    .clickable(enabled = pendingSong == null && pendingFilm == null && pendingGif == null) {
                                        if (viewModel.gifSupport) {
                                            showAttachmentMenu = true
                                        } else {
                                            pickerInitialMode = PickerMode.SONG
                                            showSongFilmPicker = true
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.comment_attachment_attach),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = showAttachmentMenu,
                                onDismissRequest = { showAttachmentMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.comment_attachment_gif)) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        showGifPicker = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.comment_attachment_song)) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        pickerInitialMode = PickerMode.SONG
                                        showSongFilmPicker = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.comment_attachment_film)) },
                                    onClick = {
                                        showAttachmentMenu = false
                                        pickerInitialMode = PickerMode.FILM
                                        showSongFilmPicker = true
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.width(CorusSpacing.sm))
                    }

                    TextField(
                        value = commentText,
                        onValueChange = { newValue ->
                            if (newValue.text.length <= maxChars) {
                                val textChanged = newValue.text != commentText.text
                                commentText = newValue
                                if (textChanged) {
                                    mentionSearchJob?.cancel()
                                    mentionSearchJob = scope.launch {
                                        delay(200)
                                        val query = parseMentionQuery(newValue.text, newValue.selection.start)
                                        if (query != null) viewModel.searchMentions(query) else viewModel.clearMentions()
                                    }
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                when {
                                    editingComment != null -> stringResource(R.string.comments_input_placeholder_edit)
                                    replyingTo != null -> stringResource(R.string.comments_input_placeholder_reply)
                                    else -> stringResource(R.string.comments_input_placeholder_add)
                                },
                                style = CorusFont.body,
                                color = CorusColors.Tertiary,
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = CorusFont.body,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = false,
                        maxLines = 4,
                        trailingIcon = {
                            if (showCounter) {
                                // Match iOS / CommentsSheet: counter visible at >= 650;
                                // turns red at >= maxChars - 10 (690).
                                Text(
                                    "${maxChars - commentText.text.length}",
                                    style = CorusFont.caption,
                                    color = if (commentText.text.length >= maxChars - 10) CorusColors.Error else CorusColors.Secondary,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val hasAnyAttachment = pendingSong != null || pendingFilm != null || pendingGif != null
                            if ((commentText.text.isNotBlank() || hasAnyAttachment) && !isSending) {
                                val editing = editingComment
                                val pendingGifSnapshot = pendingGif
                                when {
                                    editing != null -> viewModel.editComment(editing.id, commentText.text.trim())
                                    pendingGifSnapshot != null -> viewModel.sendGifComment(
                                        gifURL = pendingGifSnapshot.fullURL,
                                        slug = pendingGifSnapshot.slug,
                                        text = commentText.text.trim(),
                                    )
                                    else -> viewModel.addComment(postId, commentText.text.trim())
                                }
                                commentText = TextFieldValue("")
                                viewModel.clearMentions()
                                keyboardController?.hide()
                            }
                        }),
                    )
                    val hasAttachment = pendingSong != null || pendingFilm != null || pendingGif != null
                    val canSend = (commentText.text.isNotBlank() || hasAttachment) && !isSending
                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (canSend) CorusColors.Accent else CorusColors.Divider)
                            .clickable(enabled = canSend) {
                                val editing = editingComment
                                val pendingGifSnapshot = pendingGif
                                when {
                                    editing != null -> viewModel.editComment(editing.id, commentText.text.trim())
                                    pendingGifSnapshot != null -> viewModel.sendGifComment(
                                        gifURL = pendingGifSnapshot.fullURL,
                                        slug = pendingGifSnapshot.slug,
                                        text = commentText.text.trim(),
                                    )
                                    else -> viewModel.addComment(postId, commentText.text.trim())
                                }
                                commentText = TextFieldValue("")
                                viewModel.clearMentions()
                                keyboardController?.hide()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(R.string.comments_cd_send),
                            modifier = Modifier.size(18.dp),
                            tint = if (canSend) Color.White else CorusColors.Tertiary,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (viewModel.gifSupport && showGifPicker) {
            GifPickerSheet(
                onGifSelected = { gif ->
                    viewModel.attachGif(gif)
                    showGifPicker = false
                },
                onDismiss = { showGifPicker = false },
            )
        }

        if (showSongFilmPicker) {
            SongFilmPickerSheet(
                initialMode = pickerInitialMode,
                onSongSelected = { track ->
                    viewModel.attachSong(track)
                    showSongFilmPicker = false
                },
                onFilmSelected = { movie ->
                    viewModel.attachFilm(movie)
                    showSongFilmPicker = false
                },
                onDismiss = { showSongFilmPicker = false },
            )
        }

        reportingComment?.let { comment ->
            val reportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { reportingComment = null },
                sheetState = reportSheetState,
                containerColor = CorusColors.Background,
            ) {
                CorusSystemBars()
                ReportSheet(
                    contentType = ReportContentType.COMMENT,
                    contentId = comment.id,
                    authRepository = viewModel.authRepository,
                    userRepository = viewModel.userRepository,
                    analyticsService = viewModel.analyticsService,
                    onDismiss = { reportingComment = null },
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Full post card (matches iOS SinglePostCommentsView)
            post?.let { p ->
                item(key = "header") {
                    val engagement = engagementStates[p.id]
                    val likeCount = engagement?.likeCount ?: p.likeCount
                    val commentCount = engagement?.commentCount ?: p.commentCount
                    val repostCount = engagement?.repostCount ?: p.repostCount
                    val isLiked = engagement?.isLiked ?: p.isLiked
                    val isSaved = engagement?.isSaved ?: false
                    val saveCount = engagement?.saveCount ?: p.saveCount

                    PostCard(
                        post = p,
                        likeCount = likeCount,
                        commentCount = commentCount,
                        repostCount = repostCount,
                        isLiked = isLiked,
                        isSaved = isSaved,
                        saveCount = saveCount,
                        saveCountEnabled = viewModel.remoteConfig.saveCountEnabled,
                        currentUser = currentUserProfile,
                        isPreviewLoading = p.isTrack && loadingTrackId == p.track.id,
                        isPreviewPlaying = p.isTrack && nowPlayingState.trackId == p.track.id && nowPlayingState.isPlaying,
                        hideComments = true,
                        onLikeTap = { viewModel.togglePostLike(p.id) },
                        onSaveTap = { viewModel.togglePostSave(p.id) },
                        onUserTap = { onNavigateToUser(p.user.id) },
                        onPreviewTap = {
                            if (p.isMovie) {
                                p.movieId?.let { onNavigateToFilm(FilmDetailRoute(movieId = it)) }
                            } else {
                                viewModel.playPreview(p)
                            }
                        },
                        onTrailerTap = {
                            p.trailerURL?.let { url ->
                                viewModel.nowPlayingManager.pause()
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
                            }
                        },
                        onCommentTap = {
                            scope.launch { listState.animateScrollToItem(1) }
                        },
                        onRepostTap = { onRepost(p) },
                        onShareTap = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://corus.fm/post/${p.id}")
                            }
                            try { context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.post_detail_share_chooser))) } catch (_: Exception) { }
                        },
                        onSpotifyTap = {
                            if (p.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD) {
                                val permalink = p.track.soundcloudPermalinkUrl
                                if (!permalink.isNullOrBlank()) {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(permalink))) }
                                }
                            } else if (musicService == fm.corus.android.data.model.MusicService.SPOTIFY) {
                                val spotifyUri = p.track.spotifyURI
                                val spotifyWeb = p.track.spotifyWebURL
                                val uri = spotifyUri.ifBlank { spotifyWeb }
                                if (uri.isNotBlank()) {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } catch (_: Exception) { }
                                }
                            } else {
                                // Apple Music / TIDAL / Deezer: resolve + open (network, cached).
                                scope.launch {
                                    val url = viewModel.resolveServiceLinkUrl(p.track)
                                    if (!url.isNullOrBlank()) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    }
                                }
                            }
                        },
                        musicService = musicService,
                        onLikesTap = { onNavigateToLikes(p.id) },
                        onLikerTap = { liker -> onNavigateToUser(liker.id) },
                        onMentionTap = { username ->
                            scope.launch {
                                val userId = viewModel.resolveUsernameToId(username.removePrefix("@"))
                                if (userId != null) onNavigateToUser(userId)
                            }
                        },
                        onHashtagTap = { hashtag ->
                            viewModel.analyticsService.logTrendingHashtagTapped(hashtag.removePrefix("#"))
                            onNavigateToHashtag(hashtag.removePrefix("#"))
                        },
                        onSongCountTap = {
                            if (p.isMovie) {
                                p.movieId?.let { onNavigateToFilm(FilmDetailRoute(movieId = it)) }
                            } else {
                                onNavigateToSong(p.track)
                            }
                        },
                        onFilmPageTap = { p.movieId?.let { onNavigateToFilm(FilmDetailRoute(movieId = it)) } },
                        onVoiceNotePlayed = { viewModel.analyticsService.logVoiceNotePlayed() },
                        onMenuTap = { menuPost = p },
                        backCoverFlipState = backCoverFlipState,
                        onSubtitleTap = fm.corus.android.ui.components.postSubtitleTap(
                            p, onNavigateToArtist, onNavigateToDirector,
                        ),
                    )
                    HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(CorusSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = CorusColors.Accent)
                    }
                }
            } else if (comments.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(CorusSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.comments_no_comments), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text(stringResource(R.string.comments_be_the_first), style = CorusFont.caption, color = CorusColors.Tertiary)
                    }
                }
            } else {
                items(comments, key = { it.id }) { comment ->
                    SingleCommentRow(
                        comment = comment,
                        isOwnComment = comment.user.id == viewModel.currentUserId,
                        isLiked = likedCommentIds.contains(comment.id),
                        replies = repliesByParent[comment.id] ?: emptyList(),
                        likedCommentIds = likedCommentIds,
                        currentUserId = viewModel.currentUserId,
                        highlightedCommentId = activeHighlightId,
                        canReply = commentBlockReason == null,
                        onLike = { viewModel.toggleCommentLike(postId, comment.id) },
                        onLikeLongPress = { onNavigateToCommentLikes(comment.id) },
                        onReply = {
                            viewModel.setReplyTo(comment)
                            focusRequester.requestFocus()
                        },
                        onUserTap = { onNavigateToUser(comment.user.id) },
                        onReplyUserTap = { userId -> onNavigateToUser(userId) },
                        onMentionTap = { username ->
                            scope.launch {
                                val userId = viewModel.resolveUsernameToId(username.removePrefix("@"))
                                if (userId != null) onNavigateToUser(userId)
                            }
                        },
                        onHashtagTap = { hashtag ->
                            viewModel.analyticsService.logTrendingHashtagTapped(hashtag.removePrefix("#"))
                            onNavigateToHashtag(hashtag.removePrefix("#"))
                        },
                        onReplyReply = { reply ->
                            viewModel.setReplyTo(reply)
                            focusRequester.requestFocus()
                        },
                        onReplyLike = { replyId -> viewModel.toggleCommentLike(postId, replyId) },
                        onReplyLikeLongPress = { replyId -> onNavigateToCommentLikes(replyId) },
                        onEdit = { viewModel.startEditing(comment) },
                        onDelete = { viewModel.deleteComment(comment.id) },
                        onReport = { reportingComment = comment },
                        onBlock = { viewModel.blockUser(comment.user.id) },
                        onReplyEdit = { reply -> viewModel.startEditing(reply) },
                        onReplyDelete = { replyId -> viewModel.deleteComment(replyId) },
                        onReplyReport = { reply -> reportingComment = reply },
                        onReplyBlock = { reply -> viewModel.blockUser(reply.user.id) },
                        nowPlaying = viewModel.nowPlayingManager,
                        onNavigateToSong = onNavigateToSong,
                        onNavigateToFilm = { movie -> onNavigateToFilm(movie.toFilmDetailRoute()) },
                    )
                }
            }
        }
    }

    fm.corus.android.ui.components.PostMenuSheets(
        menuPost = menuPost,
        sharePost = sharePost,
        editCaptionPost = editCaptionPost,
        deleteConfirmPost = deleteConfirmPost,
        onMenuPostChange = { menuPost = it },
        onSharePostChange = { sharePost = it },
        onEditCaptionPostChange = { editCaptionPost = it },
        onDeleteConfirmPostChange = { deleteConfirmPost = it },
        actions = viewModel,
        musicService = musicService,
        backCoverStateFor = { backCoverFlipState },
        onNavigateToSong = onNavigateToSong,
        onNavigateToFilm = { movieId -> onNavigateToFilm(FilmDetailRoute(movieId = movieId)) },
        onRepost = onRepost,
        onPostDeleted = { onBack() },
        onCaptionSaved = { viewModel.loadPost(postId) },
        artistPagesEnabled = onNavigateToArtist != null,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToDirector = onNavigateToDirector,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleCommentRow(
    comment: CymbalComment,
    isOwnComment: Boolean,
    isLiked: Boolean,
    replies: List<CymbalComment>,
    likedCommentIds: Set<String>,
    currentUserId: String?,
    highlightedCommentId: String? = null,
    nowPlaying: fm.corus.android.domain.NowPlayingManager? = null,
    /** Inherited from the screen-level audience gate. False hides the
     *  Reply text on this row + on its child replies, so blocked viewers
     *  don't open a composer that would fail at submit. */
    canReply: Boolean = true,
    onLike: () -> Unit,
    onLikeLongPress: () -> Unit,
    onReply: () -> Unit,
    onUserTap: () -> Unit,
    onReplyUserTap: (String) -> Unit,
    onMentionTap: (String) -> Unit,
    onHashtagTap: (String) -> Unit,
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
    onReplyReply: (CymbalComment) -> Unit,
    onReplyLike: (String) -> Unit,
    onReplyLikeLongPress: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onReplyEdit: (CymbalComment) -> Unit,
    onReplyDelete: (String) -> Unit,
    onReplyReport: (CymbalComment) -> Unit,
    onReplyBlock: (CymbalComment) -> Unit,
) {
    val commentHighlightColor by animateColorAsState(
        targetValue = if (highlightedCommentId == comment.id) CorusColors.Accent.copy(alpha = 0.1f) else Color.Transparent,
        animationSpec = tween(durationMillis = if (highlightedCommentId == comment.id) 200 else 500),
        label = "commentHighlight",
    )
    Column {
        CommentContentRow(
            comment = comment,
            isOwnComment = isOwnComment,
            isLiked = isLiked,
            isReply = false,
            highlightColor = commentHighlightColor,
            canReply = canReply,
            onUserTap = onUserTap,
            onLike = onLike,
            onLikeLongPress = onLikeLongPress,
            onReply = onReply,
            onEdit = onEdit,
            onDelete = onDelete,
            onReport = onReport,
            onBlock = onBlock,
            onMentionTap = onMentionTap,
            onHashtagTap = onHashtagTap,
            nowPlaying = nowPlaying,
            onNavigateToSong = onNavigateToSong,
            onNavigateToFilm = onNavigateToFilm,
        )

        // Replies
        replies.forEach { reply ->
            val replyHighlightColor by animateColorAsState(
                targetValue = if (highlightedCommentId == reply.id) CorusColors.Accent.copy(alpha = 0.1f) else Color.Transparent,
                animationSpec = tween(durationMillis = if (highlightedCommentId == reply.id) 200 else 500),
                label = "replyHighlight",
            )
            CommentContentRow(
                comment = reply,
                isOwnComment = reply.user.id == currentUserId,
                isLiked = likedCommentIds.contains(reply.id),
                isReply = true,
                highlightColor = replyHighlightColor,
                canReply = canReply,
                onUserTap = { onReplyUserTap(reply.user.id) },
                onLike = { onReplyLike(reply.id) },
                onLikeLongPress = { onReplyLikeLongPress(reply.id) },
                onReply = { onReplyReply(reply) },
                onEdit = { onReplyEdit(reply) },
                onDelete = { onReplyDelete(reply.id) },
                onReport = { onReplyReport(reply) },
                onBlock = { onReplyBlock(reply) },
                onMentionTap = onMentionTap,
                onHashtagTap = onHashtagTap,
                nowPlaying = nowPlaying,
                onNavigateToSong = onNavigateToSong,
                onNavigateToFilm = onNavigateToFilm,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CommentContentRow(
    comment: CymbalComment,
    isOwnComment: Boolean,
    isLiked: Boolean,
    isReply: Boolean,
    highlightColor: Color,
    /** When false, the inline "Reply" link is hidden — viewer can't write
     *  a new reply on this post. Inherited from the screen-level audience
     *  gate via SingleCommentRow / ReplyRow. */
    canReply: Boolean = true,
    onUserTap: () -> Unit,
    onLike: () -> Unit,
    onLikeLongPress: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onMentionTap: (String) -> Unit,
    onHashtagTap: (String) -> Unit,
    nowPlaying: fm.corus.android.domain.NowPlayingManager? = null,
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val localContextForCopy = LocalContext.current
    val canCopy = comment.text.isNotEmpty()
    val canBlock = !isOwnComment && !comment.user.isBot
    val canLongPress = canCopy || !isOwnComment

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.comments_dialog_delete_title), style = CorusFont.songTitleLarge) },
            text = { Text(stringResource(R.string.comments_dialog_delete_message), style = CorusFont.body) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.comments_dialog_delete_confirm), color = CorusColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text(stringResource(R.string.comments_dialog_block_title_format, comment.user.username), style = CorusFont.songTitleLarge) },
            text = {
                Text(
                    stringResource(R.string.comments_dialog_block_message),
                    style = CorusFont.body,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirm = false
                    onBlock()
                }) {
                    Text(stringResource(R.string.comments_dialog_block_confirm), color = CorusColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .background(highlightColor)
        .then(
            if (canLongPress) Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = { showContextMenu = true },
            ) else Modifier
        )
        .then(
            if (isReply) Modifier.padding(start = 56.dp, end = CorusSpacing.lg, top = CorusSpacing.xxs, bottom = CorusSpacing.xxs)
            else Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm)
        )

    Row(modifier = rowModifier) {
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            if (canCopy) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.comments_menu_copy), style = CorusFont.body, color = CorusColors.Text) },
                    onClick = {
                        showContextMenu = false
                        clipboardManager.setText(AnnotatedString(comment.text))
                        ToastManager.show(localContextForCopy.getString(R.string.comments_toast_copied))
                    },
                )
            }
            if (!isOwnComment && !comment.user.isBot) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.comments_menu_report), style = CorusFont.body, color = CorusColors.Error) },
                    onClick = {
                        showContextMenu = false
                        onReport()
                    },
                )
            }
            if (canBlock) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.comments_menu_block), style = CorusFont.body, color = CorusColors.Error) },
                    onClick = {
                        showContextMenu = false
                        showBlockConfirm = true
                    },
                )
            }
        }

        UserAvatarView(
            avatarURL = comment.user.avatarURL,
            displayName = comment.user.displayName,
            size = if (isReply) 24.dp else 32.dp,
            modifier = Modifier.clickable(onClick = onUserTap),
        )
        Spacer(modifier = Modifier.width(CorusSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.user.username,
                    style = CorusFont.captionMedium,
                    color = CorusColors.Text,
                    modifier = Modifier.clickable(onClick = onUserTap),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Text(DateUtils.relativeTime(LocalContext.current, comment.timestamp), style = CorusFont.caption, color = CorusColors.Tertiary)
                if (comment.isEdited) {
                    Spacer(modifier = Modifier.width(CorusSpacing.sm))
                    Text(stringResource(R.string.comments_edited), style = CorusFont.caption, color = CorusColors.Tertiary)
                }
            }
            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
            // Body and the like control share a row so the heart top-aligns
            // with the first line of the comment text (Instagram-style).
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    // Text, then GIF (left-aligned), then song/film. Text + GIF can co-exist.
                    val displayedText = if (comment.textIsAttachmentFallback) "" else comment.text
                    if (displayedText.isNotEmpty()) {
                        val annotatedText = remember(displayedText) {
                            buildMentionAnnotatedString(
                                text = displayedText,
                                baseStyle = androidx.compose.ui.text.SpanStyle(
                                    fontFamily = CorusFont.body.fontFamily,
                                ),
                            )
                        }
                        TappableMentionText(
                            text = annotatedText,
                            style = CorusFont.body,
                            onMentionTap = onMentionTap,
                            onHashtagTap = onHashtagTap,
                        )
                    }
                    if (comment.gifURL != null) {
                        if (displayedText.isNotEmpty()) Spacer(Modifier.height(CorusSpacing.xs))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(comment.gifURL).build(),
                                contentDescription = stringResource(R.string.comments_cd_gif),
                                modifier = Modifier
                                    .widthIn(max = 200.dp)
                                    .heightIn(max = 150.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    } else {
                        if ((comment.attachedSong != null || comment.attachedFilm != null) && nowPlaying != null) {
                            if (displayedText.isNotEmpty()) Spacer(Modifier.height(CorusSpacing.xs))
                            CommentAttachmentCard(
                                attachedSong = comment.attachedSong,
                                attachedFilm = comment.attachedFilm,
                                nowPlaying = nowPlaying,
                                onNavigateToSong = onNavigateToSong,
                                onNavigateToFilm = onNavigateToFilm,
                            )
                        }
                    }
                    if (canReply) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Text(
                            stringResource(R.string.comments_reply),
                            style = CorusFont.captionMedium,
                            color = CorusColors.Secondary,
                            modifier = Modifier.clickable(onClick = onReply),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = CorusSpacing.sm),
                ) {
                    val haptic = LocalHapticFeedback.current
                    // Heart stacked over its like count, mirroring CommentsSheet
                    // (and iOS). The count is only shown when > 0.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLike,
                            onLongClick = {
                                if (comment.likeCount > 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLikeLongPress()
                                }
                            },
                        ),
                    ) {
                        Icon(
                            if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.comments_cd_like),
                            tint = if (isLiked) CorusColors.Like else CorusColors.Secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        if (comment.likeCount > 0) {
                            Text(
                                text = "${comment.likeCount}",
                                style = CorusFont.caption.copy(fontSize = 11.sp),
                                color = CorusColors.Secondary,
                            )
                        }
                    }
                    if (isOwnComment) {
                        var showMenu by remember { mutableStateOf(false) }
                        Spacer(modifier = Modifier.width(CorusSpacing.sm))
                        Box {
                            Icon(
                                imageVector = Icons.Filled.MoreHoriz,
                                contentDescription = stringResource(R.string.comments_cd_options),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showMenu = true },
                                    ),
                                tint = CorusColors.Secondary,
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                if (comment.gifURL == null && !comment.textIsAttachmentFallback) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.comments_menu_edit), style = CorusFont.body, color = CorusColors.Text) },
                                        onClick = {
                                            showMenu = false
                                            onEdit()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.comments_menu_delete), style = CorusFont.body, color = CorusColors.Error) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
