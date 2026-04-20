package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.DiscoIntensity
import fm.corus.android.data.model.FrameStyle
import fm.corus.android.data.model.RainIntensity
import fm.corus.android.data.model.SnowIntensity
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Featured movie post with frame overlay, matching iOS FeaturedMoviePosterView.
 */
@Composable
fun FeaturedMoviePosterView(
    post: CymbalPost,
    frameStyle: FrameStyle,
    rainIntensity: RainIntensity = RainIntensity.OFF,
    snowIntensity: SnowIntensity = SnowIntensity.OFF,
    discoIntensity: DiscoIntensity = DiscoIntensity.OFF,
    likeCount: Int = post.likeCount,
    isLiked: Boolean = post.isLiked,
    onLikeTap: () -> Unit = {},
    onTrailerTap: () -> Unit = {},
    onPostTap: () -> Unit = {},
    onArtReady: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val frameDrawable = remember(frameStyle) {
        when (frameStyle) {
            FrameStyle.BLACK -> R.drawable.frame_black
            FrameStyle.WHITE -> R.drawable.frame_white
            FrameStyle.RED -> R.drawable.frame_red
            FrameStyle.BLUE -> R.drawable.frame_blue
            FrameStyle.GREEN -> R.drawable.frame_green
        }
    }

    // Frame dimensions from Figma (585x482 canvas)
    val sectionAspect = 585f / 482f
    val posterXRatio = 207.28f / 585f
    val posterYRatio = 84.85f / 482f
    val posterWRatio = 184.98f / 585f
    val posterHRatio = 269.33f / 482f

    // Double-tap-to-like heart animation state (matches PostCard & iOS FeaturedMoviePosterView)
    val scope = rememberCoroutineScope()
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }

    // Gradient wraps entire featured area (frame + title row), matching iOS .background(LinearGradient)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.36f to Color(0xFFF3F3F3),
                        1.0f to Color(0xFFBFBFBF),
                    ),
                ),
            )
            .pointerInput(post.id, isLiked) {
                detectTapGestures(
                    onTap = { onPostTap() },
                    onDoubleTap = {
                        if (!isLiked) onLikeTap()
                        showDoubleTapHeart = true
                        scope.launch {
                            heartScale.snapTo(0f)
                            heartAlpha.snapTo(1f)
                            heartScale.animateTo(1f, animationSpec = tween(300))
                            delay(400)
                            heartAlpha.animateTo(0f, animationSpec = tween(300))
                            showDoubleTapHeart = false
                        }
                    },
                )
            },
    ) {
        val w = maxWidth
        val h = w / sectionAspect

        // Frame + poster composite
        val glassOverlay = ImageBitmap.imageResource(R.drawable.frame_glass_overlay)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h),
        ) {
            // Frame
            Image(
                painter = painterResource(frameDrawable),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )

            // Poster
            val posterUrl = post.displayImageLargeURL ?: post.displayImageURL
            if (posterUrl != null) {
                val ctx = LocalContext.current
                val posterRequest = remember(posterUrl) {
                    ImageRequest.Builder(ctx)
                        .data(posterUrl)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = posterRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = w * posterXRatio, y = h * posterYRatio)
                        .size(width = w * posterWRatio, height = h * posterHRatio),
                    contentScale = ContentScale.Crop,
                    onSuccess = { onArtReady() },
                )
            }

            // Glass overlay (screen blend — matches iOS .blendMode(.screen))
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

            // Weather / disco effects overlay
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
                DiscoEffectView(intensity = discoIntensity, modifier = Modifier.matchParentSize())
            }

            // Double-tap-to-like heart overlay
            if (showDoubleTapHeart) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(80.dp)
                        .scale(heartScale.value)
                        .alpha(heartAlpha.value),
                )
            }
        }

        // Movie info + engagement row — overlaid at the bottom of the gradient
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
                    contentDescription = "Like",
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

            // Trailer button (if available) — YouTube red play icon, matching iOS
            if (post.trailerURL != null) {
                Spacer(modifier = Modifier.width(CorusSpacing.md))
                Image(
                    painter = painterResource(R.drawable.ic_play_rectangle_fill),
                    contentDescription = "Watch Trailer",
                    modifier = Modifier
                        .height(22.dp)
                        .clickable(onClick = onTrailerTap),
                )
            }
        }
    }
}
