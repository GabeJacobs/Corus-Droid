package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.valentinilk.shimmer.shimmer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.DiscoIntensity
import fm.corus.android.data.model.FrameStyle
import fm.corus.android.data.model.RainIntensity
import fm.corus.android.data.model.SnowIntensity
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.LocalCorusDarkTheme
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/** Drawable id for the user's frame style, theme-aware. */
@Composable
internal fun frameDrawableRes(frameStyle: FrameStyle): Int {
    val isDark = LocalCorusDarkTheme.current
    return remember(frameStyle, isDark) {
        when (frameStyle) {
            FrameStyle.BLACK -> if (isDark) R.drawable.frame_black_dark else R.drawable.frame_black
            FrameStyle.WHITE -> if (isDark) R.drawable.frame_white_dark else R.drawable.frame_white
            FrameStyle.RED -> if (isDark) R.drawable.frame_red_dark else R.drawable.frame_red
            FrameStyle.BLUE -> if (isDark) R.drawable.frame_blue_dark else R.drawable.frame_blue
            FrameStyle.GREEN -> if (isDark) R.drawable.frame_green_dark else R.drawable.frame_green
            FrameStyle.THEATER -> if (isDark) R.drawable.frame_theater_dark else R.drawable.frame_theater
        }
    }
}

/**
 * Featured movie post with frame overlay, matching iOS FeaturedMoviePosterView.
 *
 * `post` is nullable so this same composable can render the loading state
 * (frame + shimmer + placeholder metadata) and the loaded state. Keeping the
 * same composable position across the transition preserves view identity, so
 * the frame Image and the poster shimmer don't restart their animations when
 * the post arrives — only the bottom metadata row transitions.
 */
@Composable
fun FeaturedMoviePosterView(
    post: CymbalPost?,
    frameStyle: FrameStyle,
    rainIntensity: RainIntensity = RainIntensity.OFF,
    snowIntensity: SnowIntensity = SnowIntensity.OFF,
    discoIntensity: DiscoIntensity = DiscoIntensity.OFF,
    likeCount: Int = post?.likeCount ?: 0,
    isLiked: Boolean = post?.isLiked ?: false,
    onLikeTap: () -> Unit = {},
    onTrailerTap: () -> Unit = {},
    onPostTap: () -> Unit = {},
    onArtReady: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Pick frame asset explicitly per in-app theme. We can't rely on Android's
    // drawable-night qualifier because the appearance toggle (System/Light/Dark)
    // can force dark while the OS Configuration is still in light uiMode.
    val isDark = LocalCorusDarkTheme.current
    val frameDrawable = frameDrawableRes(frameStyle)

    // Frame dimensions from Figma (585x482 canvas). Poster slot is per-style.
    // Marquee adds extra height below the PNG so the title clearance matches
    // the stock frames.
    val posterXRatio = frameStyle.posterXFrac
    val posterYRatio = frameStyle.posterYFrac
    val posterWRatio = frameStyle.posterWFrac
    val posterHRatio = frameStyle.posterHFrac

    // Double-tap-to-like heart animation state (matches PostCard & iOS FeaturedMoviePosterView)
    val scope = rememberCoroutineScope()
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    val haptics = LocalHapticManager.current

    // Gradient wraps entire featured area (frame + title row), matching iOS .background(LinearGradient)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.36f to CorusColors.FeaturedFilmBackgroundTop,
                        1.0f to CorusColors.FeaturedFilmBackgroundBottom,
                    ),
                ),
            )
            .let { mod ->
                if (post == null) mod else mod.pointerInput(post.id, isLiked) {
                    detectTapGestures(
                        onTap = { onPostTap() },
                        onDoubleTap = {
                            // Mirrors iOS FeaturedMoviePosterView.doubleTapLike haptic.
                            haptics.impact(HapticManager.ImpactStyle.MEDIUM)
                            if (!isLiked) onLikeTap()
                            showDoubleTapHeart = true
                            scope.launch {
                                playDoubleTapLikeHeartAnimation(heartScale, heartAlpha)
                                showDoubleTapHeart = false
                            }
                        },
                    )
                }
            },
    ) {
        val w = maxWidth
        val frameH = w * FrameStyle.CANVAS_HEIGHT / FrameStyle.CANVAS_WIDTH
        val totalH = w / frameStyle.featuredSectionAspect

        // Frame + poster composite. Extra title clearance (if any) hangs
        // below the 585×482 PNG so the metadata row isn't tight on the bulbs.
        val glassOverlay = ImageBitmap.imageResource(R.drawable.frame_glass_overlay)
        val frameBitmap = ImageBitmap.imageResource(frameDrawable)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalH)
                .clipToBounds(),
        ) {
            // Frame
            Image(
                painter = painterResource(frameDrawable),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(frameH)
                    .align(Alignment.TopCenter)
                    .then(
                        if (frameStyle.usesStandingShadow) {
                            Modifier.drawBehind {
                                drawOval(
                                    color = Color.Black.copy(alpha = 0.22f),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        size.width * 0.24f,
                                        size.height * 0.74f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(
                                        size.width * 0.52f,
                                        size.height * 0.16f,
                                    ),
                                )
                                drawOval(
                                    color = Color.Black.copy(alpha = 0.32f),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        size.width * 0.30f,
                                        size.height * 0.70f,
                                    ),
                                    size = androidx.compose.ui.geometry.Size(
                                        size.width * 0.40f,
                                        size.height * 0.12f,
                                    ),
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentScale = ContentScale.FillBounds,
            )

            // Poster opening — the shimmer is *always* rendered (not gated on
            // a URL) so its animation doesn't restart when `post` arrives.
            // The AsyncImage fades in over the shimmer once the bytes load.
            val posterUrl = post?.displayImageLargeURL ?: post?.displayImageURL
            Box(
                modifier = Modifier
                    .offset(x = w * posterXRatio, y = frameH * posterYRatio)
                    .size(width = w * posterWRatio, height = frameH * posterHRatio),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmer()
                        .background(Color.Black.copy(alpha = 0.12f)),
                )
                if (posterUrl != null) {
                    val ctx = LocalContext.current
                    val posterRequest = remember(posterUrl) {
                        ImageRequest.Builder(ctx)
                            .data(posterUrl)
                            .crossfade(false)
                            .build()
                    }
                    var posterLoaded by remember(posterUrl) { mutableStateOf(false) }
                    var posterFromCache by remember(posterUrl) { mutableStateOf(false) }
                    val posterAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (posterLoaded) 1f else 0f,
                        animationSpec = if (posterFromCache) tween(durationMillis = 0) else tween(durationMillis = 220),
                        label = "featuredPosterFade",
                    )
                    AsyncImage(
                        model = posterRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(posterAlpha),
                        contentScale = ContentScale.Crop,
                        onSuccess = { state ->
                            posterFromCache = state.result.dataSource == coil3.decode.DataSource.MEMORY_CACHE
                            posterLoaded = true
                            onArtReady()
                        },
                    )
                }
            }

            // Glass overlay (screen blend — matches iOS .blendMode(.screen)).
            // Skipped in dark mode where .Screen blend overexposes against the near-black
            // backdrop and reads as harsh sparkles.
            if (!isDark && frameStyle.usesGlassOverlay) {
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawImage(
                                image = glassOverlay,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                blendMode = BlendMode.Screen,
                            )
                        },
                )
            }

            // Weather / disco effects overlay — only meaningful with a post.
            if (post != null) {
                if (rainIntensity != RainIntensity.OFF) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = if (rainIntensity == RainIntensity.HEAVY) 0.15f else 0.10f)),
                    )
                    RainEffectView(intensity = rainIntensity, modifier = Modifier.matchParentSize())
                }
                if (snowIntensity != SnowIntensity.OFF) {
                    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.06f)))
                    SnowEffectView(intensity = snowIntensity, modifier = Modifier.matchParentSize())
                }
                if (discoIntensity != DiscoIntensity.OFF) {
                    DiscoScrim(intensity = discoIntensity, modifier = Modifier.matchParentSize())
                    DiscoEffectView(
                        intensity = discoIntensity,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                // Punch frame chrome so additive dots don't
                                // sit on the rails as grey spots. The opening
                                // and surround are transparent in the PNG.
                                drawImage(
                                    image = frameBitmap,
                                    dstSize = IntSize(
                                        size.width.toInt(),
                                        frameH.toPx().toInt(),
                                    ),
                                    blendMode = BlendMode.DstOut,
                                )
                            },
                    )
                }
            }

            // Double-tap-to-like heart overlay
            if (showDoubleTapHeart) {
                DoubleTapLikeHeartIcon(
                    scale = heartScale.value,
                    alpha = heartAlpha.value,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Movie info + engagement row — real metadata when post is loaded,
        // shimmer placeholders sized to match when the post hasn't arrived.
        if (post != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.displayTitle,
                    style = CorusFont.songTitle,
                    color = CorusColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = post.displaySubtitle,
                    style = CorusFont.artistName,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(CorusSpacing.md))

            // Like button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onLikeTap),
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.featured_cd_like),
                    tint = if (isLiked) CorusColors.Like else CorusColors.Secondary,
                    modifier = Modifier.size(20.dp),
                )
                if (likeCount > 0) {
                    Spacer(modifier = Modifier.width(CorusSpacing.xs))
                    Text(
                        text = likeCount.toString(),
                        style = CorusFont.captionMedium,
                        color = if (isLiked) CorusColors.Like else CorusColors.Secondary,
                    )
                }
            }

            // Official YouTube Icon (brand.youtube) — opens the trailer on YouTube.
            if (!post.trailerURL.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(CorusSpacing.md))
                YouTubeIcon(
                    height = 22.dp,
                    contentDescription = stringResource(R.string.featured_cd_watch_trailer),
                    modifier = Modifier.clickable(onClick = onTrailerTap),
                )
            }
        }
        } else {
            // Loading shell — shimmer bars sized to match the real metadata
            // row's heights so the swap is just a content change, not a
            // layout shift.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs)) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(14.dp)
                            .shimmer()
                            .background(CorusColors.Skeleton),
                    )
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(11.dp)
                            .shimmer()
                            .background(CorusColors.Skeleton),
                    )
                }
            }
        }
    }
}
