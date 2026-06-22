package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.TrailerPlaybackCoordinator
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.NunitoFamily
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun PostCard(
    post: CymbalPost,
    likeCount: Int = post.likeCount,
    commentCount: Int = post.commentCount,
    repostCount: Int = post.repostCount,
    isLiked: Boolean = post.isLiked,
    isSaved: Boolean = false,
    saveCount: Int = post.saveCount,
    /** Gates the per-post save count next to the bookmark (`save_count_enabled`
     *  Remote Config flag). When false the bookmark renders with no number. */
    saveCountEnabled: Boolean = false,
    currentUser: CymbalUser? = null,
    onLikeTap: () -> Unit = {},
    onSaveTap: () -> Unit = {},
    onUserTap: () -> Unit = {},
    onPostTap: () -> Unit = {},
    isPreviewLoading: Boolean = false,
    isPreviewPlaying: Boolean = false,
    onPreviewTap: () -> Unit = {},
    onTrailerTap: () -> Unit = {},
    onCommentTap: () -> Unit = {},
    onRepostTap: () -> Unit = {},
    onShareTap: () -> Unit = {},
    onMenuTap: () -> Unit = {},
    onSpotifyTap: () -> Unit = {},
    musicService: fm.corus.android.data.model.MusicService = fm.corus.android.data.model.MusicService.SPOTIFY,
    onLikesTap: () -> Unit = {},
    onLikerTap: (CymbalUser) -> Unit = {},
    onMentionTap: (String) -> Unit = {},
    onHashtagTap: (String) -> Unit = {},
    trackPostCount: Int = post.trackPostCount ?: 0,
    onSongCountTap: () -> Unit = {},
    backCoverFlipState: BackCoverFlipState = rememberBackCoverFlipState(),
    onFilmPageTap: () -> Unit = {},
    onVoiceNotePlayed: () -> Unit = {},
    onRepostedFromUserTap: (userId: String?, username: String) -> Unit = { _, _ -> },
    onCommentUserTap: (CymbalUser) -> Unit = {},
    hideComments: Boolean = false,
    showsTapHint: Boolean = false,
    onAlbumArtTap: () -> Unit = {},
    /** True only on the Trending feed. Surfaces an inline "Follow" pill to the
     *  left of the "..." button for authors the viewer doesn't already follow. */
    isTrendingFeed: Boolean = false,
    /** Whether the viewer currently follows this post's author. Resolved
     *  synchronously from the cached following set so the pill is correct on
     *  first composition (no per-post read, no pop-in). */
    isFollowingAuthor: Boolean = false,
    /** Whether the viewer's following set has been seeded yet. While false (a
     *  true cold start with no persisted cache), the inline pill stays hidden
     *  instead of flashing "Follow" for an author who may already be followed.
     *  Defaults true so non-Trending callers are unaffected. */
    isFollowingKnown: Boolean = true,
    /** Invoked when the inline Trending pill is tapped — should optimistically
     *  follow the author (and log analytics). */
    onFollowAuthor: () -> Unit = {},
) {
    // For the viewer's own posts, overlay the live profile from
    // AuthRepository.userProfile (passed in as `currentUser`) on top of the
    // denormalized author stamped on the post doc. Keeps a fresh
    // name/avatar/flair/badge change reflected on the user's own feed and
    // detail screens immediately — the backend `backfillPostAuthorOnUserUpdate`
    // trigger propagates the same change to other viewers within a few seconds.
    val displayUser: CymbalUser = remember(currentUser, post.user) {
        if (currentUser != null && currentUser.id == post.user.id) currentUser
        else post.user
    }
    val scope = rememberCoroutineScope()
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var showFilmOverlay by remember { mutableStateOf(false) }
    // Prototype: when set, the poster swaps to an inline YouTube trailer player.
    var inlineTrailerVideoID by remember(post.id) { mutableStateOf<String?>(null) }
    // When another post (or music) takes over the single trailer slot, tear this
    // one down so at most one trailer plays at a time.
    val activeTrailerPostId by TrailerPlaybackCoordinator.activePostId.collectAsState()
    LaunchedEffect(activeTrailerPostId) {
        if (activeTrailerPostId != post.id && inlineTrailerVideoID != null) {
            inlineTrailerVideoID = null
        }
    }
    DisposableEffect(post.id) {
        onDispose { TrailerPlaybackCoordinator.stop(post.id) }
    }
    var showUnavailableToast by remember(post.id) { mutableStateOf(false) }
    val flipState = backCoverFlipState
    val haptics = LocalHapticManager.current

    // Inline Trending "Follow" pill state. `followTapped` latches so the pill
    // keeps rendering through its confirm→fade animation even after the
    // optimistic follow flips `isFollowingAuthor` true; `inlineFollowDismissed`
    // hides it once the fade completes. Keyed by post.id so recycled rows reset.
    val isOwnPost = currentUser?.id == post.user.id
    var followTapped by remember(post.id) { mutableStateOf(false) }
    var inlineFollowDismissed by remember(post.id) { mutableStateOf(false) }
    val showInlineFollow = isTrendingFeed && !isOwnPost && !inlineFollowDismissed &&
        (followTapped || (isFollowingKnown && !isFollowingAuthor))

    // Tap-hint pulse: only runs when showsTapHint and there is no playback yet.
    val hintActive = showsTapHint && post.isTrack && !post.track.unavailable
            && !isPreviewPlaying && !isPreviewLoading
    val hintIconAlpha = remember { Animatable(0f) }
    val hintIconScale = remember { Animatable(0.85f) }
    LaunchedEffect(hintActive) {
        if (!hintActive) {
            hintIconAlpha.snapTo(0f)
            hintIconScale.snapTo(0.85f)
            return@LaunchedEffect
        }
        val fadeIn = 600
        val fadeOut = 600
        val rest = 150L
        kotlinx.coroutines.delay(450) // wait for image fade-in
        while (true) {
            kotlinx.coroutines.coroutineScope {
                launch { hintIconAlpha.animateTo(1f, tween(fadeIn, easing = EaseInOut)) }
                launch { hintIconScale.animateTo(1f, tween(fadeIn, easing = EaseInOut)) }
            }
            kotlinx.coroutines.coroutineScope {
                launch { hintIconAlpha.animateTo(0f, tween(fadeOut, easing = EaseInOut)) }
                launch { hintIconScale.animateTo(0.85f, tween(fadeOut, easing = EaseInOut)) }
            }
            kotlinx.coroutines.delay(rest)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 1. POST HEADER: avatar + username + badges + repost indicator + pill + menu.
        // Wrapped in RowThatFits — the inline Follow pill is the lowest-priority
        // element and drops when the full username + badges leave no room. The
        // name is never truncated to fit the pill, and nothing overlaps the "...".
        val headerContent: @Composable (Boolean) -> Unit = { includePill ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatarView(
                avatarURL = displayUser.avatarURL,
                avatarThumbURL = displayUser.avatarThumbURL,
                displayName = displayUser.displayName,
                size = 28.dp,
                modifier = Modifier.clickable(onClick = onUserTap),
            )

            Spacer(modifier = Modifier.width(CorusSpacing.sm))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onUserTap),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                UsernameWithFlair(
                    username = displayUser.username,
                    isBot = displayUser.isBot,
                    isVerified = displayUser.isVerified,
                    isClubMember = displayUser.isClubMember,
                    flairStyle = displayUser.flairStyle,
                    isFirstPoster = post.isFirstPoster,
                    isNewRelease = post.isNewRelease(),
                    style = CorusFont.username,
                    color = CorusColors.Text,
                )

                // Repost indicator — only the @username is tappable so a near-miss on
                // the poster's username above doesn't accidentally land on the original poster's handle
                val repostedFromUsername = post.repostedFromUsername
                if (!repostedFromUsername.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = CorusColors.Secondary,
                        )
                        Text(
                            text = stringResource(R.string.post_card_reposted_from),
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                        )
                        Text(
                            text = "@$repostedFromUsername",
                            style = CorusFont.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = CorusColors.Secondary,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    onRepostedFromUserTap(
                                        post.repostedFromUserId?.takeIf { it.isNotEmpty() },
                                        repostedFromUsername,
                                    )
                                },
                            ),
                        )
                    }
                }
                // Injected by a followed hashtag — surface the tag so the user
                // knows why this post is in their feed and can jump to the
                // hashtag's full feed. Mirrors web + iOS.
                val injectedTag = post.injectedByHashtag
                if (!injectedTag.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "from",
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                        )
                        Text(
                            text = "#$injectedTag",
                            style = CorusFont.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = CorusColors.Accent,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onHashtagTap(injectedTag) },
                            ),
                        )
                    }
                }
            }

            if (includePill && showInlineFollow) {
                InlineFollowPill(
                    onTap = {
                        followTapped = true
                        onFollowAuthor()
                    },
                    onAnimationEnd = { inlineFollowDismissed = true },
                )
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
            }

            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = stringResource(R.string.post_card_cd_more_options),
                modifier = Modifier
                    .size(14.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMenuTap,
                    ),
                tint = CorusColors.Secondary,
            )
        }
        }
        RowThatFits(
            modifier = Modifier.fillMaxWidth(),
            primary = { headerContent(true) },
            fallback = { headerContent(false) },
        )

        // 2. ALBUM ART / MOVIE POSTER: full-bleed, no corner radius, double-tap to like
        val aspectRatio = if (post.isMovie) 2f / 3f else 1f
        val flipRotation by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (flipState.isFlipped) 180f else 0f,
            animationSpec = tween(durationMillis = 700),
            label = "albumFlip",
        )
        val density = androidx.compose.ui.platform.LocalDensity.current
        val cameraDistancePx = with(density) { 12.dp.toPx() } * 100f
        val showFront = flipRotation <= 90f

        // Only attach `graphicsLayer` when there's an actual rotation to
        // render. The modifier promotes this Box to its own RenderNode every
        // time it's present — paying that cost on every visible card while
        // scrolling, even though virtually no card is ever flipped, is the
        // same anti-pattern we removed on iOS (`rotation3DEffect` always-on).
        // When `flipRotation == 0f`, the transform would be identity anyway.
        val needsFlipLayer = flipRotation != 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .then(
                    if (needsFlipLayer) {
                        Modifier.graphicsLayer {
                            rotationY = flipRotation
                            cameraDistance = cameraDistancePx
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (showFront) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(post.id, post.isTrack, post.isMovie, flipState.isLoading) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (flipState.isLoading) return@detectTapGestures
                                    if (inlineTrailerVideoID != null) return@detectTapGestures
                                    // Mirrors iOS PostCard.doubleTapLike haptic.
                                    haptics.impact(HapticManager.ImpactStyle.MEDIUM)
                                    if (!isLiked) onLikeTap()
                                    showDoubleTapHeart = true
                                    scope.launch {
                                        heartScale.snapTo(0f)
                                        heartAlpha.snapTo(1f)
                                        heartScale.animateTo(1f, animationSpec = tween(300))
                                        kotlinx.coroutines.delay(400)
                                        heartAlpha.animateTo(0f, animationSpec = tween(300))
                                        showDoubleTapHeart = false
                                    }
                                },
                                onTap = {
                                    if (flipState.isLoading) return@detectTapGestures
                                    if (inlineTrailerVideoID != null) return@detectTapGestures
                                    if (post.isTrack) onAlbumArtTap()
                                    when {
                                        post.isTrack && post.track.unavailable -> {
                                            showUnavailableToast = true
                                            scope.launch {
                                                kotlinx.coroutines.delay(2000)
                                                showUnavailableToast = false
                                            }
                                        }
                                        post.isTrack -> onPreviewTap()
                                        // Prototype: a single tap plays the trailer inline when one
                                        // exists; posters without a trailer fall back to the overlay
                                        // so the film page is still reachable.
                                        post.isMovie -> {
                                            val videoID = youTubeVideoID(post.trailerURL)
                                            if (videoID != null) {
                                                inlineTrailerVideoID = videoID
                                                // Claim the single slot: stops any other
                                                // trailer and pauses music.
                                                TrailerPlaybackCoordinator.play(post.id)
                                            } else {
                                                showFilmOverlay = !showFilmOverlay
                                            }
                                        }
                                        else -> onPostTap()
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val isUnavailable = post.isTrack && post.track.unavailable
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(post.displayImageLargeURL ?: post.displayImageURL)
                            .crossfade(true)
                            .size(if (post.isMovie) Size(780, 1170) else Size(640, 640))
                            .build(),
                        contentDescription = post.displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        colorFilter = if (isUnavailable) {
                            androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0.3f) }
                            )
                        } else null,
                    )

                    if (isUnavailable) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.55f), shape = CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    if (showUnavailableToast) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(vertical = CorusSpacing.sm, horizontal = CorusSpacing.lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.post_card_cd_track_unavailable),
                                color = Color.White,
                                style = CorusFont.caption.copy(fontWeight = FontWeight.Medium),
                            )
                        }
                    }

                    // Back-cover loading overlay
                    if (flipState.isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(CorusSpacing.lg),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.post_card_finding_back_cover),
                                color = Color.White,
                                style = CorusFont.caption.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(bottom = CorusSpacing.xs),
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { flipState.progress.value.coerceIn(0f, 1f) },
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // "No back cover available" banner
                    if (flipState.showNotFound) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.post_card_no_back_cover),
                                color = Color.White,
                                style = CorusFont.body.copy(fontWeight = FontWeight.Medium),
                            )
                        }
                    }

                    // Preview loading/playing overlay (track posts only)
                    if (post.isTrack && (isPreviewLoading || isPreviewPlaying) && !flipState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isPreviewLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = stringResource(R.string.post_card_cd_pause),
                                    tint = Color.White,
                                    modifier = Modifier.size(52.dp),
                                )
                            }
                        }
                    }

            // Film overlay with action buttons
            androidx.compose.animation.AnimatedVisibility(
                visible = post.isMovie && showFilmOverlay,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showFilmOverlay = false },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        modifier = Modifier.width(IntrinsicSize.Max),
                    ) {
                        // View Film Page button
                        Button(
                            onClick = { onFilmPageTap() },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                            ),
                            contentPadding = PaddingValues(
                                horizontal = CorusSpacing.xl,
                                vertical = CorusSpacing.md,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Movie,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = stringResource(R.string.post_card_view_film_page),
                                    style = CorusFont.button,
                                )
                            }
                        }

                        // Watch Trailer button (only if trailer exists)
                        if (!post.trailerURL.isNullOrBlank()) {
                            Button(
                                onClick = { onTrailerTap() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White,
                                ),
                                border = BorderStroke(1.75.dp, Color.White),
                                contentPadding = PaddingValues(
                                    horizontal = CorusSpacing.xl,
                                    vertical = CorusSpacing.md,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.post_card_watch_trailer),
                                        style = CorusFont.button,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Prototype: inline trailer player. Letterboxed 16:9 against black
            // inside the portrait poster box so the card never reflows.
            inlineTrailerVideoID?.let { videoID ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    InlineYouTubePlayer(
                        videoID = videoID,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        // Controls shown so the scrub bar is available; they
                        // auto-hide during playback, so it stays clean.
                        showControls = true,
                        onEnded = {
                            inlineTrailerVideoID = null
                            TrailerPlaybackCoordinator.stop(post.id)
                        },
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(CorusSpacing.md)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .clickable {
                                inlineTrailerVideoID = null
                                TrailerPlaybackCoordinator.stop(post.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.post_card_cd_close_trailer),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Tap-to-play hint (first feed post for new accounts only).
            if (hintActive) {
                Box(contentAlignment = Alignment.Center) {
                    // Soft blurred shadow so the white triangle reads on light covers.
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .size(56.dp)
                            .offset(y = 6.dp)
                            .blur(14.dp)
                            .graphicsLayer {
                                alpha = hintIconAlpha.value
                                scaleX = hintIconScale.value
                                scaleY = hintIconScale.value
                            },
                    )
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                alpha = hintIconAlpha.value
                                scaleX = hintIconScale.value
                                scaleY = hintIconScale.value
                            },
                    )
                }
            }

            // Heart animation overlay
            if (showDoubleTapHeart) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(heartScale.value)
                        .alpha(heartAlpha.value),
                )
            }
                }
            } else {
                // BACK FACE: counter-rotate so the image isn't mirrored; tap to flip back
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .pointerInput(post.id) {
                            detectTapGestures(onTap = { flipState.flipBack() })
                        },
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(flipState.backCoverURL)
                            .crossfade(true)
                            .size(Size(640, 640))
                            .build(),
                        contentDescription = stringResource(R.string.post_card_cd_album_back_cover),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        // 3. SONG INFO ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg)
                .padding(top = CorusSpacing.md, bottom = CorusSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = post.displayTitle,
                    style = CorusFont.songTitle, // ExtraBold 16sp
                    color = CorusColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = post.displaySubtitle,
                    style = CorusFont.artistName, // Medium 14sp
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(CorusSpacing.sm))

            if (post.isMovie) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Film info icon — circle with "i", matching iOS FilmInfoIcon
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .offset(y = (-4).dp)
                            .background(
                                CorusColors.Secondary.copy(alpha = 0.15f),
                                CircleShape,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSpotifyTap, // navigates to film detail page
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "i",
                            style = CorusFont.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                            ),
                            color = CorusColors.Secondary,
                        )
                    }

                    // Trailer button — red rectangle with white play icon, matching iOS TrailerButton
                    if (!post.trailerURL.isNullOrBlank()) {
                        Image(
                            painter = painterResource(R.drawable.ic_play_rectangle_fill),
                            contentDescription = stringResource(R.string.post_card_cd_watch_trailer),
                            modifier = Modifier
                                .height(22.dp)
                                .offset(y = (-4).dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onTrailerTap,
                                ),
                        )
                    }
                }
            } else {
                val isSoundCloud = post.track.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD
                val isAppleMusic = post.track.source == fm.corus.android.data.model.TrackSource.APPLEMUSIC
                val cd = stringResource(
                    when {
                        isSoundCloud -> R.string.post_card_cd_play_soundcloud
                        isAppleMusic -> R.string.post_card_cd_play_spotify // reuse generic "play" copy; brand handled via icon
                        else -> R.string.post_card_cd_play_spotify
                    }
                )
                val tapModifier = Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSpotifyTap,
                    )
                if (isSoundCloud) {
                    SoundCloudAdaptiveLogo(
                        modifier = tapModifier.semantics { contentDescription = cd },
                        size = 28.dp,
                    )
                } else {
                    // Glyph reflects the service the tap opens. Default is the
                    // viewer's preference; we only fall back to the source when
                    // the backend has CONFIRMED the track isn't on Apple Music
                    // (appleMusicId == ""), which is rare. null appleMusicId means
                    // unknown (legacy post, or the feed payload hasn't carried it
                    // yet) and must NOT flip — otherwise the whole feed shows
                    // Spotify to an Apple Music viewer. Apple-only tracks always
                    // route a Spotify viewer to Apple Music. Mirrors iOS.
                    val hasAppleMusicEquivalent = isAppleMusic || post.track.appleMusicId != ""
                    val displayedService = when {
                        isAppleMusic && musicService == fm.corus.android.data.model.MusicService.SPOTIFY ->
                            fm.corus.android.data.model.MusicService.APPLE_MUSIC
                        musicService == fm.corus.android.data.model.MusicService.APPLE_MUSIC && !hasAppleMusicEquivalent ->
                            fm.corus.android.data.model.MusicService.SPOTIFY
                        else -> musicService
                    }
                    Image(
                        painter = painterResource(fm.corus.android.domain.MusicServiceLinkOut.logoRes(displayedService)),
                        contentDescription = cd,
                        modifier = tapModifier,
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }

        // 4. ENGAGEMENT ROW — naturally sized buttons matching iOS HStack(spacing: .lg)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg)
                .padding(vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
        ) {
            // Like button
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLikeTap,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.post_card_cd_like),
                    modifier = Modifier.size(22.dp),
                    tint = if (isLiked) CorusColors.Like else CorusColors.Text,
                )
                if (likeCount > 0) {
                    Text(
                        text = likeCount.toString(),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                    )
                }
            }

            // Comment button
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCommentTap,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = stringResource(R.string.post_card_cd_comment),
                    modifier = Modifier.size(20.dp),
                    tint = CorusColors.Text,
                )
                if (commentCount > 0) {
                    Text(
                        text = commentCount.toString(),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                    )
                }
            }

            // Repost button
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRepostTap,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Filled.Repeat,
                    contentDescription = stringResource(R.string.post_card_cd_repost),
                    modifier = Modifier.size(20.dp),
                    tint = CorusColors.Text,
                )
                if (repostCount > 0) {
                    Text(
                        text = repostCount.toString(),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                    )
                }
            }

            // Share button
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = stringResource(R.string.post_card_cd_share),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onShareTap,
                    ),
                tint = CorusColors.Text,
            )

            // Track post count — only show when 2+ people posted the same song/film
            if (trackPostCount > 1) {
                Row(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSongCountTap,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
                ) {
                    VennDiagramIcon(
                        size = 23.dp,
                        color = CorusColors.Text,
                    )
                    Text(
                        text = trackPostCount.toString(),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save button. Floor the count to 1 while optimistically saved so a
            // just-saved post never shows 0 before the server trigger commits.
            // Gated by the save_count_enabled flag — 0 (hidden) when off.
            val displaySaveCount = when {
                !saveCountEnabled -> 0
                isSaved -> maxOf(saveCount, 1)
                else -> saveCount
            }
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSaveTap,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.xs),
            ) {
                // Count sits to the LEFT of the right-anchored bookmark so it
                // reads inward instead of off the row's trailing edge.
                if (displaySaveCount > 0) {
                    Text(
                        text = displaySaveCount.toString(),
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                    )
                }
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(R.string.post_card_cd_save),
                    modifier = Modifier.size(20.dp),
                    tint = CorusColors.Text,
                )
            }
        }

        // 5. LIKED BY
        LikedBySection(
            likers = post.likers,
            likeCount = likeCount,
            onLikesTap = onLikesTap,
            onLikerTap = onLikerTap,
            currentUser = currentUser,
            isLiked = isLiked,
        )

        // 6. CAPTION or VOICE NOTE
        if (!post.voiceNoteURL.isNullOrBlank()) {
            VoiceNotePlayerView(
                voiceNoteURL = post.voiceNoteURL!!,
                username = displayUser.username,
                onUsernameTap = onUserTap,
                onPlaybackStarted = onVoiceNotePlayed,
                modifier = Modifier
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(bottom = CorusSpacing.xs),
            )
        } else if (!post.caption.isNullOrBlank()) {
            // Drop the caption cap from 3 → 2 lines when a comment preview will
            // render, so the preview is never squeezed out by a long caption.
            val willShowCommentPreview = !hideComments && post.comments.isNotEmpty()
            val captionMaxLines = if (willShowCommentPreview) 2 else 3
            ExpandableCaptionText(
                username = displayUser.username,
                caption = post.caption,
                maxCollapsedLines = captionMaxLines,
                onMentionTap = { onMentionTap(it) },
                onHashtagTap = { onHashtagTap(it) },
                onUsernameTap = onUserTap,
                onCommentTap = onCommentTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(bottom = CorusSpacing.xs),
            )
        }

        // 7. COMMENT PREVIEW — tap username → profile, tap elsewhere → open comments sheet
        // Match iOS: show up to 3 when no caption, fewer when caption is present
        val hasCaption = !post.caption.isNullOrBlank()
        val maxVisibleComments = if (hasCaption) 2 else minOf(3, post.comments.size)
        if (!hideComments && post.comments.isNotEmpty() && maxVisibleComments > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCommentTap,
                    )
                    .padding(horizontal = CorusSpacing.lg)
                    .padding(bottom = CorusSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                post.comments.take(maxVisibleComments).forEach { comment ->
                    val commentText = remember(comment.user.id, comment.user.username, comment.text) {
                        buildAnnotatedString {
                            pushStringAnnotation(tag = "commentUser", annotation = comment.user.id)
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                )
                            ) {
                                append(comment.user.username)
                            }
                            pop()
                            append(" ")
                            val bodyStyle = SpanStyle(
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp,
                            )
                            append(
                                buildMentionAnnotatedString(
                                    text = comment.text,
                                    baseStyle = bodyStyle,
                                )
                            )
                        }
                    }
                    androidx.compose.foundation.text.ClickableText(
                        text = commentText,
                        style = CorusFont.body.copy(color = CorusColors.Text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onClick = { offset ->
                            commentText.getStringAnnotations(
                                tag = "commentUser",
                                start = offset,
                                end = offset,
                            ).firstOrNull()?.let {
                                onCommentUserTap(comment.user)
                                return@ClickableText
                            }
                            commentText.getStringAnnotations(
                                tag = "mention",
                                start = offset,
                                end = offset,
                            ).firstOrNull()?.let {
                                onMentionTap(it.item)
                                return@ClickableText
                            }
                            commentText.getStringAnnotations(
                                tag = "hashtag",
                                start = offset,
                                end = offset,
                            ).firstOrNull()?.let {
                                onHashtagTap(it.item)
                                return@ClickableText
                            }
                            onCommentTap()
                        },
                    )
                }
            }
        }

        // 8. TIMESTAMP
        val timestampContext = LocalContext.current
        Text(
            text = fm.corus.android.ui.util.DateUtils.relativeTimeLong(timestampContext, post.timestamp),
            style = CorusFont.caption, // Normal 12sp
            color = CorusColors.Secondary,
            modifier = Modifier
                .padding(horizontal = CorusSpacing.lg)
                .padding(bottom = CorusSpacing.sm),
        )
    }
}

/**
 * Renders [primary] if its natural (intrinsic) width fits the available width,
 * otherwise [fallback]. A lightweight Compose analog of SwiftUI's `ViewThatFits`,
 * used so the post header can drop the inline Follow pill when the full username
 * + badges leave no room — without truncating the username for the pill's sake.
 *
 * `maxIntrinsicWidth` measures the natural content width ignoring greedy
 * `Spacer(weight)`s, so the decision is driven by the real username + badge +
 * pill widths. The chosen variant is then measured with the real constraints.
 */
@Composable
private fun RowThatFits(
    modifier: Modifier = Modifier,
    primary: @Composable () -> Unit,
    fallback: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val naturalWidth = subcompose("measure", primary)
            .firstOrNull()
            ?.maxIntrinsicWidth(constraints.maxHeight)
            ?: 0
        val fits = naturalWidth <= constraints.maxWidth
        val placeable = subcompose(if (fits) "primary" else "fallback") {
            if (fits) primary() else fallback()
        }.first().measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

/**
 * Inline blue "Follow" pill shown on Trending-feed posts, left of the "..."
 * button. On tap it haptic-confirms, pops to a "✓ Following" state, holds
 * briefly, then fades itself out (calling [onAnimationEnd] so the parent can
 * drop it). Mirrors the iOS PostCard inline-follow pill exactly.
 */
@Composable
private fun InlineFollowPill(
    onTap: () -> Unit,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticManager.current
    val scope = rememberCoroutineScope()
    var confirmed by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }

    Row(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(CorusColors.Accent)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                enabled = false
                haptics.impact(HapticManager.ImpactStyle.LIGHT)
                onTap()
                scope.launch {
                    confirmed = true
                    // Little spring-like pop into the confirmed state.
                    scale.animateTo(1.10f, tween(130, easing = EaseInOut))
                    scale.animateTo(1f, tween(130, easing = EaseInOut))
                    // Hold so "Following" reads, then fade away.
                    kotlinx.coroutines.delay(720)
                    alpha.animateTo(0f, tween(450))
                    onAnimationEnd()
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = if (confirmed) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(
                if (confirmed) R.string.likes_button_following else R.string.likes_button_follow
            ),
            style = CorusFont.caption.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}
