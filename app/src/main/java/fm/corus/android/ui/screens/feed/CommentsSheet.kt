package fm.corus.android.ui.screens.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fm.corus.android.R
import fm.corus.android.data.model.CymbalComment
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.ui.components.CommentAttachmentCard
import fm.corus.android.ui.components.CommentAttachmentPendingChip
import fm.corus.android.ui.components.CorusDraggableSheet
import fm.corus.android.ui.components.CorusSheetState
import fm.corus.android.ui.components.CorusSheetValue
import fm.corus.android.ui.components.rememberCorusSheetState
import fm.corus.android.ui.components.HashtagSuggestionsList
import fm.corus.android.ui.components.MentionSuggestionsList
import fm.corus.android.ui.components.PickerMode
import fm.corus.android.ui.components.ReportContentType
import fm.corus.android.ui.components.ReportSheet
import fm.corus.android.ui.components.SkeletonCommentRow
import fm.corus.android.ui.components.GifPickerSheet
import fm.corus.android.ui.components.SongFilmPickerSheet
import fm.corus.android.ui.components.TappableMentionText
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.components.VoiceNotePlayerView
import fm.corus.android.ui.components.applyHashtag
import fm.corus.android.ui.components.applyMention
import fm.corus.android.ui.components.buildMentionAnnotatedString
import fm.corus.android.ui.components.parseHashtagQuery
import fm.corus.android.ui.components.parseMentionQuery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
import fm.corus.android.ui.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    postId: String,
    onDismiss: () -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
) {
    val viewModel: CommentsViewModel = hiltViewModel()
    // Custom two-detent sheet (see CorusDraggableSheet): opens at the half-height
    // peek, drag up to full, drag down to dismiss. Unlike Material3's
    // ModalBottomSheet, its panel height tracks the detent so the composer stays
    // pinned and visible at the peek, mirroring the iOS
    // .presentationDetents([.medium, .large]) comments sheet.
    val sheetState = rememberCorusSheetState()
    val scope = rememberCoroutineScope()

    // Tapping a user / hashtag / song / film inside a comment animates the sheet closed,
    // then lands on the destination — instead of the sheet vanishing abruptly (iOS parity:
    // navigate intent, then animated dismiss). The push happens immediately behind the
    // still-visible sheet; the slide-down reveals it. The host clears the sheet via onDismiss
    // once it settles at the Hidden anchor, so the nav callbacks here must NOT also clear it.
    fun navigateClosing(navigate: () -> Unit) {
        navigate()
        scope.launch { sheetState.hide() }
    }

    CorusDraggableSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        containerColor = CorusColors.Background,
    ) {
        CorusSystemBars()
        CommentsSheetContent(
            postId = postId,
            viewModel = viewModel,
            sheetState = sheetState,
            onDismiss = onDismiss,
            onNavigateToUser = { userId -> navigateClosing { onNavigateToUser(userId) } },
            onNavigateToSong = { track -> navigateClosing { onNavigateToSong(track) } },
            onNavigateToFilm = { movie -> navigateClosing { onNavigateToFilm(movie) } },
            onNavigateToHashtag = { hashtag -> navigateClosing { onNavigateToHashtag(hashtag) } },
            autoFocusInput = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    postId: String,
    viewModel: CommentsViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.comments_screen_title), style = CorusFont.screenTitle, color = CorusColors.Text) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feed_cd_back), tint = CorusColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            CommentsSheetContent(
                postId = postId,
                viewModel = viewModel,
                onDismiss = onDismiss,
                onNavigateToUser = onNavigateToUser,
                onNavigateToSong = onNavigateToSong,
                onNavigateToFilm = onNavigateToFilm,
                onNavigateToHashtag = onNavigateToHashtag,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheetContent(
    postId: String,
    viewModel: CommentsViewModel = hiltViewModel(),
    sheetState: CorusSheetState? = null,
    onDismiss: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    autoFocusInput: Boolean = false,
) {
    val comments by viewModel.comments.collectAsState()
    val repliesByParent by viewModel.repliesByParent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val likedCommentIds by viewModel.likedCommentIds.collectAsState()
    val post by viewModel.post.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val editingComment by viewModel.editingComment.collectAsState()
    val blockReason by viewModel.commentBlockReason.collectAsState()

    // The retained ViewModel may still hold the PREVIOUS post's comments on the first frame
    // after reopening for a different post. Until the new load commits (loadComments sets
    // loadedPostId), treat the list as loading so we show a skeleton, never the old sheet.
    val loadedPostId by viewModel.loadedPostId.collectAsState()
    val isStale = loadedPostId != postId

    // A fast load (e.g. a post with no comments) used to flash the shimmer skeleton for a
    // single frame and then hard-cut to the empty state. Only reveal the skeleton once loading
    // has actually persisted past a short threshold, so quick loads settle straight to the
    // result without the flash. (The LaunchedEffect is cancelled the moment loading ends, so
    // reaching the line past the delay means we are still genuinely loading.)
    val isLoadingNow = isStale || (isLoading && comments.isEmpty())
    var showSkeleton by remember { mutableStateOf(false) }
    LaunchedEffect(isLoadingNow) {
        if (isLoadingNow) {
            delay(180)
            showSkeleton = true
        } else {
            showSkeleton = false
        }
    }

    val pendingSong by viewModel.pendingSong.collectAsState()
    val pendingFilm by viewModel.pendingFilm.collectAsState()
    val pendingGif by viewModel.pendingGif.collectAsState()

    var commentText by remember { mutableStateOf(TextFieldValue("")) }
    val commentListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showGifPicker by remember { mutableStateOf(false) }
    var showSongFilmPicker by remember { mutableStateOf(false) }
    var pickerInitialMode by remember { mutableStateOf(PickerMode.SONG) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var reportingComment by remember { mutableStateOf<CymbalComment?>(null) }
    var viewingLikesCommentId by remember { mutableStateOf<String?>(null) }
    val maxChars = 700
    val showCounter = commentText.text.length >= 650
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()
    val isSearchingMentions by viewModel.isSearchingMentions.collectAsState()
    val hashtagSuggestions by viewModel.hashtagSuggestions.collectAsState()
    var mentionSearchJob by remember { mutableStateOf<Job?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val handleMentionTap: (String) -> Unit = { username ->
        coroutineScope.launch {
            val userId = viewModel.resolveUsernameToId(username)
            if (userId != null) onNavigateToUser(userId)
        }
    }
    val handleHashtagTap: (String) -> Unit = { hashtag ->
        val tag = hashtag.removePrefix("#")
        viewModel.analyticsService.logTrendingHashtagTapped(tag)
        onNavigateToHashtag(tag)
    }

    LaunchedEffect(sendError) {
        if (sendError != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSendError()
        }
    }

    LaunchedEffect(postId) {
        viewModel.loadComments(postId)
        viewModel.loadPost(postId)
    }

    // Keyboard ⟺ Expanded invariant (iOS parity): the keyboard only belongs at the full
    // detent. As soon as the sheet is dragged toward a lower detent, dismiss the keyboard
    // and drop focus, so you can't get stuck half-collapsed with the keyboard still up.
    // snapshotFlow emits only on change, so the focus -> expand() open path (which sets the
    // target straight to Expanded) never trips this.
    if (sheetState != null) {
        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.targetValue }
                .collect { target ->
                    if (target != CorusSheetValue.Expanded) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }
        }
    }

    LaunchedEffect(replyingTo, blockReason) {
        if (replyingTo != null && blockReason == null) {
            focusRequester.requestFocus()
        }
    }

    // Scroll the replied-to comment into view when Reply is tapped (iOS parity:
    // scrollProxy.scrollTo(newId, anchor: .center) in CommentsSheet.swift). We bring it to
    // the top of the list area rather than centering, so it stays visible above the keyboard
    // that opens at the same time. Index mirrors the LazyColumn's flattened order below:
    // an optional header (voice note / caption) row, then each comment followed by its replies.
    LaunchedEffect(replyingTo?.id) {
        val targetId = replyingTo?.id ?: return@LaunchedEffect
        val hasHeader = !post?.voiceNoteURL.isNullOrEmpty() || !post?.caption.isNullOrEmpty()
        var index = if (hasHeader) 1 else 0
        var found = -1
        run {
            comments.forEach { comment ->
                if (comment.id == targetId) { found = index; return@run }
                index++
                (repliesByParent[comment.id] ?: emptyList()).forEach { reply ->
                    if (reply.id == targetId) { found = index; return@run }
                    index++
                }
            }
        }
        if (found >= 0) commentListState.animateScrollToItem(found)
    }

    LaunchedEffect(editingComment?.id, blockReason) {
        val editing = editingComment
        if (editing != null) {
            commentText = TextFieldValue(editing.text, selection = TextRange(editing.text.length))
            if (blockReason == null) focusRequester.requestFocus()
        } else {
            commentText = TextFieldValue("")
        }
    }

    // Auto-focus input to open keyboard immediately (Instagram-style).
    // Re-key on blockReason: if the post hasn't loaded yet we don't know whether
    // the composer will render, so wait until the gate resolves before grabbing
    // focus (otherwise requestFocus throws on a detached FocusRequester).
    if (autoFocusInput) {
        LaunchedEffect(blockReason) {
            if (blockReason == null) focusRequester.requestFocus()
        }
    }

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

    val likesCommentId = viewingLikesCommentId
    if (likesCommentId != null) {
        BackHandler(enabled = true) { viewingLikesCommentId = null }
        Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { viewingLikesCommentId = null },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.comments_cd_back_to_comments),
                        tint = CorusColors.Text,
                    )
                }
                Text(
                    text = stringResource(R.string.likes_title),
                    style = CorusFont.screenTitle,
                    color = CorusColors.Text,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(vertical = CorusSpacing.sm),
                    textAlign = TextAlign.Center,
                )
            }
            HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            CommentLikesContent(
                postId = postId,
                commentId = likesCommentId,
                onNavigateToUser = { userId ->
                    viewingLikesCommentId = null
                    // onNavigateToUser already animates the sheet closed (see
                    // CommentsBottomSheet.navigateClosing); no separate onDismiss() so the
                    // close animates instead of the sheet vanishing.
                    onNavigateToUser(userId)
                },
                fillContainer = true,
            )
        }
        return
    }

    // Explicit two-slot layout: the composer (bottom bar) is measured at its NATURAL
    // height first; the comment list gets exactly the remaining height. The nav-bar inset
    // is handled by the sheet container (CorusDraggableSheet), so nothing to do here.
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            // ── Slot 0: comment list (title + divider + scrolling list) ──
            Column(modifier = Modifier.fillMaxWidth()) {
                // Centered title (Instagram-style)
                Text(
                    text = stringResource(R.string.comments_screen_title),
                    style = CorusFont.screenTitle.copy(fontSize = 16.sp),
                    color = CorusColors.Text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CorusSpacing.md, bottom = CorusSpacing.sm),
                    textAlign = TextAlign.Center,
                )

                // No divider under the header (matches iOS — the title floats above the list).

                // weight(1f) fills the list slot below the title; it is the last child
                // here (no fixed sibling after it), so the weight resolves cleanly.
                // Top padding gives the first row (voice note / caption) breathing room,
                // matching iOS.
                LazyColumn(
                    state = commentListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(top = CorusSpacing.md, bottom = CorusSpacing.sm),
                ) {
            if (showSkeleton) {
                val skeletonCount = if (isStale) 3 else post?.let { maxOf(minOf(it.commentCount, 5), 2) } ?: 3
                items(skeletonCount) { SkeletonCommentRow() }
            } else if (comments.isEmpty() && !isLoading && post?.caption.isNullOrEmpty() == true && post?.voiceNoteURL.isNullOrEmpty() == true) {
                item(key = "empty") {
                    // Ease the empty state in so it never appears as a hard cut.
                    val emptyAlpha = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { emptyAlpha.animateTo(1f, tween(220)) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                            .alpha(emptyAlpha.value),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.comments_no_comments), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                            Spacer(modifier = Modifier.height(CorusSpacing.xs))
                            Text(stringResource(R.string.comments_start_conversation), style = CorusFont.caption, color = CorusColors.Tertiary)
                        }
                    }
                }
            } else {
                val voiceNoteURL = post?.voiceNoteURL
                val captionText = post?.caption
                if (!voiceNoteURL.isNullOrEmpty()) {
                    // Voice-note post: show the player at the top, like iOS.
                    item(key = "voicenote") {
                        VoiceNotePlayerView(
                            voiceNoteURL = voiceNoteURL,
                            username = post?.user?.username ?: "",
                            onUsernameTap = { post?.user?.id?.let { onNavigateToUser(it) } },
                            modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                            color = CorusColors.Divider,
                        )
                    }
                } else if (!captionText.isNullOrEmpty()) {
                    item(key = "caption") {
                        CaptionRow(
                            user = post?.user,
                            caption = captionText,
                            timestamp = post?.timestamp,
                            onUserTap = { post?.user?.id?.let { onNavigateToUser(it) } },
                            onMentionTap = handleMentionTap,
                            onHashtagTap = handleHashtagTap,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                            color = CorusColors.Divider,
                        )
                    }
                }

                comments.forEach { comment ->
                    item(key = comment.id) {
                        CommentRow(
                            comment = comment,
                            isOwnComment = comment.user.id == viewModel.currentUserId,
                            isLiked = likedCommentIds.contains(comment.id),
                            nowPlaying = viewModel.nowPlayingManager,
                            onUserTap = { onNavigateToUser(comment.user.id) },
                            onReplyTap = {
                                if (blockReason == null) {
                                    viewModel.setReplyingTo(comment)
                                    focusRequester.requestFocus()
                                }
                            },
                            onLikeTap = { viewModel.toggleCommentLike(comment.id) },
                            onLikeLongPress = { viewingLikesCommentId = comment.id },
                            onDeleteTap = { viewModel.deleteComment(comment.id) },
                            onEditTap = { viewModel.startEditing(comment) },
                            onReportTap = { reportingComment = comment },
                            onBlockTap = { viewModel.blockUser(comment.user.id) },
                            onMentionTap = handleMentionTap,
                            onHashtagTap = handleHashtagTap,
                            onNavigateToSong = onNavigateToSong,
                            onNavigateToFilm = onNavigateToFilm,
                        )
                    }
                    val replies = repliesByParent[comment.id] ?: emptyList()
                    replies.forEach { reply ->
                        item(key = reply.id) {
                            CommentRow(
                                comment = reply,
                                isOwnComment = reply.user.id == viewModel.currentUserId,
                                isReply = true,
                                isLiked = likedCommentIds.contains(reply.id),
                                nowPlaying = viewModel.nowPlayingManager,
                                onUserTap = { onNavigateToUser(reply.user.id) },
                                onReplyTap = {
                                    if (blockReason == null) {
                                        viewModel.setReplyingTo(reply)
                                        focusRequester.requestFocus()
                                    }
                                },
                                onLikeTap = { viewModel.toggleCommentLike(reply.id) },
                                onLikeLongPress = { viewingLikesCommentId = reply.id },
                                onDeleteTap = { viewModel.deleteComment(reply.id) },
                                onEditTap = { viewModel.startEditing(reply) },
                                onReportTap = { reportingComment = reply },
                                onBlockTap = { viewModel.blockUser(reply.user.id) },
                                onMentionTap = handleMentionTap,
                                onHashtagTap = handleHashtagTap,
                                onNavigateToSong = onNavigateToSong,
                                onNavigateToFilm = onNavigateToFilm,
                            )
                        }
                    }
                }
            }

                }
            } // end Slot 0 (comment list)

            // ── Slot 1: bottom bar (composer, or locked notice). Measured at its
            // natural height; the list above is given the remaining space. ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Floor the composer bar to roughly the input row's natural height so
                    // the custom Layout can't collapse it to nothing, without adding dead
                    // space above the nav bar. Banners/mentions/keyboard grow it past this.
                    .heightIn(min = 72.dp)
                    .background(CorusColors.Background)
                    // Background is applied BEFORE this padding, so the composer's fill
                    // extends down behind the nav bar (one continuous sheet), while its
                    // CONTENT sits above both the keyboard and the 3-button nav bar. The
                    // union takes the larger inset per side: nav bar when the keyboard is
                    // closed, the (taller) IME when it's open — never double-counted. This
                    // also lets the input grow upward as the text wraps to a 2nd line.
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            ) {
            val currentBlockReason = blockReason
        if (currentBlockReason != null) {
            // Mirror iOS: viewer can't write here, so swap the composer for an
            // inline notice instead of the input bar. The comment list above
            // stays visible.
            fm.corus.android.ui.components.CommentsLockedNotice(
                reason = currentBlockReason,
                authorUsername = post?.user?.username,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.Background),
            )
        } else {

        // ── Mention suggestions (above the input bar) ──
        MentionSuggestionsList(
            users = mentionSuggestions.take(4),
            onSelect = { user ->
                commentText = applyMention(commentText, user.username)
                viewModel.clearMentions()
            },
            isSearching = isSearchingMentions,
        )

        // ── Hashtag suggestions (trending on a bare "#", prefix matches as typed) ──
        HashtagSuggestionsList(
            hashtags = hashtagSuggestions.take(3),
            onSelect = { tag ->
                commentText = applyHashtag(commentText, tag.name)
                viewModel.clearHashtags()
            },
        )

        // ── Input bar pinned at the bottom ──

        if (sendError != null) {
            Text(
                text = sendError ?: "",
                style = CorusFont.caption,
                color = CorusColors.Error,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.Error.copy(alpha = 0.1f))
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            )
        }

        // Pending attachment chip (song / film / GIF) — left-aligned, hugs content.
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

        if (editingComment != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.CardBackground)
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.comments_editing_comment), style = CorusFont.caption, color = CorusColors.Secondary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.comments_cd_cancel_edit), modifier = Modifier.size(16.dp).clickable { viewModel.cancelEditing(); commentText = TextFieldValue(""); viewModel.clearMentions() }, tint = CorusColors.Secondary)
            }
        } else if (replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CorusColors.CardBackground)
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.comments_replying_to_format, replyingTo?.user?.username ?: ""), style = CorusFont.caption, color = CorusColors.Secondary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.comments_cd_cancel_reply), modifier = Modifier.size(16.dp).clickable { viewModel.setReplyingTo(null) }, tint = CorusColors.Secondary)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CorusColors.Background)
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
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
                        // Don't steal focus from the composer, so opening the attachment
                        // menu while typing leaves the keyboard up (iOS parity: the SwiftUI
                        // Menu behaves this way). Selecting an item opens the full picker,
                        // which dismisses the keyboard as expected.
                        properties = PopupProperties(focusable = false),
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
                            mentionSearchJob = coroutineScope.launch {
                                delay(200)
                                val caret = newValue.selection.start
                                val mention = parseMentionQuery(newValue.text, caret)
                                if (mention != null) {
                                    viewModel.clearHashtags()
                                    viewModel.searchMentions(mention)
                                } else {
                                    viewModel.clearMentions()
                                    val hashtag = parseHashtagQuery(newValue.text, caret)
                                    if (hashtag != null) viewModel.searchHashtags(hashtag) else viewModel.clearHashtags()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        // Mirror iOS onChange(isInputFocused){ selectedDetent = .large }:
                        // focusing the composer grows the sheet to full height so the
                        // comment list stays readable above the keyboard.
                        if (focusState.isFocused) {
                            coroutineScope.launch { sheetState?.expand() }
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
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide() },
                ),
                shape = RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CorusColors.CardBackground,
                    unfocusedContainerColor = CorusColors.CardBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = CorusColors.Accent,
                ),
                textStyle = CorusFont.body.copy(color = CorusColors.Text),
                trailingIcon = {
                    if (showCounter) {
                        // Match iOS: counter visible at >= 650; turns red at >= maxChars - 10 (690).
                        // Previous behavior only turned red at the cap (700), missing the iOS warning band.
                        Text(
                            "${maxChars - commentText.text.length}",
                            style = CorusFont.caption,
                            color = if (commentText.text.length >= maxChars - 10) CorusColors.Error else CorusColors.Secondary,
                        )
                    }
                },
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
                        val pendingGifSnapshot = pendingGif
                        when {
                            editingComment != null -> viewModel.editComment(editingComment!!.id, commentText.text)
                            pendingGifSnapshot != null -> viewModel.sendGifComment(
                                gifURL = pendingGifSnapshot.fullURL,
                                slug = pendingGifSnapshot.slug,
                                text = commentText.text,
                            )
                            else -> viewModel.sendComment(commentText.text)
                        }
                        commentText = TextFieldValue("")
                        viewModel.clearMentions()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(R.string.comments_cd_send),
                        modifier = Modifier.size(18.dp),
                        tint = if (canSend) Color.White else CorusColors.Tertiary,
                    )
                }
            }
        }
            } // end else (composer shown)
            } // end Slot 1 (bottom bar)
        },
    ) { measurables, constraints ->
        // Composer (slot 1) at its natural height; list (slot 0) gets the rest.
        val barPlaceable = measurables[1].measure(constraints.copy(minHeight = 0))
        val listHeight = (constraints.maxHeight - barPlaceable.height).coerceAtLeast(0)
        val listPlaceable = measurables[0].measure(
            constraints.copy(minHeight = listHeight, maxHeight = listHeight)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            listPlaceable.place(0, 0)
            barPlaceable.place(0, listHeight)
        }
    }
}

@Composable
private fun CaptionRow(
    user: CymbalUser?,
    caption: String,
    timestamp: java.util.Date?,
    onUserTap: () -> Unit = {},
    onMentionTap: (String) -> Unit = {},
    onHashtagTap: (String) -> Unit = {},
) {
    if (user == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
    ) {
        UserAvatarView(
            avatarURL = user.avatarURL,
            displayName = user.displayName,
            size = CorusSpacing.avatarSmall,
            modifier = Modifier.clickable(onClick = onUserTap),
        )

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                UsernameWithFlair(
                    username = user.username,
                    isVerified = user.isVerified,
                    isClubMember = user.isClubMember,
                    flairStyle = user.flairStyle,
                    isBot = user.isBot,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onUserTap,
                    ),
                )
                if (timestamp != null) {
                    Text(
                        text = DateUtils.relativeTime(LocalContext.current, timestamp),
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                    )
                }
            }
            val annotatedCaption = remember(caption) {
                buildMentionAnnotatedString(
                    text = caption,
                    baseStyle = SpanStyle(
                        fontFamily = CorusFont.body.fontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                    ),
                )
            }
            TappableMentionText(
                text = annotatedCaption,
                style = CorusFont.body.copy(fontSize = 15.sp),
                onMentionTap = onMentionTap,
                onHashtagTap = onHashtagTap,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: CymbalComment,
    isOwnComment: Boolean = false,
    isReply: Boolean = false,
    isLiked: Boolean = false,
    nowPlaying: NowPlayingManager? = null,
    onUserTap: () -> Unit = {},
    onReplyTap: () -> Unit = {},
    onLikeTap: () -> Unit = {},
    onLikeLongPress: () -> Unit = {},
    onDeleteTap: () -> Unit = {},
    onEditTap: () -> Unit = {},
    onReportTap: () -> Unit = {},
    onBlockTap: () -> Unit = {},
    onMentionTap: (String) -> Unit = {},
    onHashtagTap: (String) -> Unit = {},
    onNavigateToSong: (CymbalTrack) -> Unit = {},
    onNavigateToFilm: (CymbalMovie) -> Unit = {},
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val rowContext = LocalContext.current
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
                    onDeleteTap()
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
                    onBlockTap()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canLongPress) Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { showContextMenu = true },
                ) else Modifier
            )
            .padding(
                start = if (isReply) CorusSpacing.xxxl + CorusSpacing.lg else CorusSpacing.lg,
                end = CorusSpacing.lg,
                top = CorusSpacing.sm,
                bottom = CorusSpacing.sm,
            ),
    ) {
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
                        ToastManager.show(rowContext.getString(R.string.comments_toast_copied))
                    },
                )
            }
            if (!isOwnComment && !comment.user.isBot) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.comments_menu_report), style = CorusFont.body, color = CorusColors.Error) },
                    onClick = {
                        showContextMenu = false
                        onReportTap()
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
            size = if (isReply) 24.dp else CorusSpacing.avatarSmall,
            modifier = Modifier.clickable(onClick = onUserTap),
        )

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            // Username with flair badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                UsernameWithFlair(
                    username = comment.user.username,
                    isVerified = comment.user.isVerified,
                    isClubMember = comment.user.isClubMember,
                    flairStyle = comment.user.flairStyle,
                    isBot = comment.user.isBot,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onUserTap,
                    ),
                )
                Text(
                    text = DateUtils.relativeTime(LocalContext.current, comment.timestamp),
                    style = CorusFont.caption,
                    color = CorusColors.Tertiary,
                )
                if (comment.isEdited) {
                    Text(
                        text = stringResource(R.string.comments_edited),
                        style = CorusFont.caption,
                        color = CorusColors.Tertiary,
                    )
                }
            }

            // Body and the like control share a row so the heart top-aligns
            // with the first line of the comment text (Instagram-style).
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    // Text, then GIF (left-aligned), then song/film attachment.
                    // Text + GIF can co-exist (caption + GIF in one comment).
                    val displayedText = if (comment.textIsAttachmentFallback) "" else comment.text
                    if (displayedText.isNotEmpty()) {
                        val annotatedText = remember(displayedText) {
                            buildMentionAnnotatedString(
                                text = displayedText,
                                baseStyle = SpanStyle(
                                    fontFamily = CorusFont.body.fontFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp,
                                ),
                            )
                        }
                        TappableMentionText(
                            text = annotatedText,
                            style = CorusFont.body.copy(fontSize = 15.sp),
                            onMentionTap = onMentionTap,
                            onHashtagTap = onHashtagTap,
                        )
                    }

                    if (comment.gifURL != null) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xs))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(comment.gifURL)
                                    .build(),
                                contentDescription = stringResource(R.string.comments_cd_gif),
                                modifier = Modifier
                                    .widthIn(max = 200.dp)
                                    .heightIn(max = 150.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    } else if ((comment.attachedSong != null || comment.attachedFilm != null) && nowPlaying != null) {
                        if (displayedText.isNotEmpty()) Spacer(Modifier.height(CorusSpacing.xs))
                        CommentAttachmentCard(
                            attachedSong = comment.attachedSong,
                            attachedFilm = comment.attachedFilm,
                            nowPlaying = nowPlaying,
                            onNavigateToSong = onNavigateToSong,
                            onNavigateToFilm = onNavigateToFilm,
                        )
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.xs))

                    // Reply button
                    Text(
                        text = stringResource(R.string.comments_reply),
                        style = CorusFont.captionMedium,
                        color = CorusColors.Secondary,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onReplyTap,
                        ),
                    )
                }

                // Like button + menu — top-aligned with the first line of text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = CorusSpacing.sm),
                ) {
                    val haptic = LocalHapticFeedback.current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLikeTap,
                            onLongClick = {
                                if (comment.likeCount > 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLikeLongPress()
                                }
                            },
                        ),
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.comments_cd_like_comment),
                            modifier = Modifier.size(14.dp),
                            tint = if (isLiked) CorusColors.Like else CorusColors.Secondary,
                        )
                        if (comment.likeCount > 0) {
                            Text(
                                text = "${comment.likeCount}",
                                style = CorusFont.caption.copy(fontSize = 11.sp),
                                color = CorusColors.Secondary,
                            )
                        }
                    }

                    // "..." menu button for own comments (matching iOS)
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
                                        text = {
                                            Text(
                                                stringResource(R.string.comments_menu_edit),
                                                style = CorusFont.body,
                                                color = CorusColors.Text,
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onEditTap()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.comments_menu_delete),
                                            style = CorusFont.body,
                                            color = CorusColors.Error,
                                        )
                                    },
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
