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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
    var showUnavailableToast by remember(post.id) { mutableStateOf(false) }
    val flipState = backCoverFlipState
    val haptics = LocalHapticManager.current

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
        // 1. POST HEADER: avatar + username with badges + repost indicator + menu
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
                                        post.isMovie -> showFilmOverlay = !showFilmOverlay
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
                } else if (isAppleMusic) {
                    // Apple-Music-only tracks lock to the Apple Music brand
                    // glyph regardless of viewer preference — same pattern
                    // as SoundCloud above.
                    Image(
                        painter = painterResource(R.drawable.apple_music_logo),
                        contentDescription = cd,
                        modifier = tapModifier,
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    // Spotify-source post: glyph reflects the viewer's preferred
                    // service (Spotify / Apple Music / TIDAL / Deezer), mirroring iOS.
                    Image(
                        painter = painterResource(fm.corus.android.domain.MusicServiceLinkOut.logoRes(musicService)),
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
                        size = 20.dp,
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

            // Save button
            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = stringResource(R.string.post_card_cd_save),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSaveTap,
                    ),
                tint = CorusColors.Text,
            )
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
