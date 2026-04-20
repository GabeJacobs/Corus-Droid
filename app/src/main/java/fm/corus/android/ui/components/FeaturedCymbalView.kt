package fm.corus.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import fm.corus.android.data.model.RainIntensity
import fm.corus.android.data.model.SnowIntensity
import fm.corus.android.data.model.VinylStyle
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Featured music post with vinyl overlay, matching iOS FeaturedCymbalView.
 */
@Composable
fun FeaturedCymbalView(
    post: CymbalPost,
    vinylStyle: VinylStyle,
    rainIntensity: RainIntensity = RainIntensity.OFF,
    snowIntensity: SnowIntensity = SnowIntensity.OFF,
    discoIntensity: DiscoIntensity = DiscoIntensity.OFF,
    likeCount: Int = post.likeCount,
    isLiked: Boolean = post.isLiked,
    onLikeTap: () -> Unit = {},
    onSpotifyTap: () -> Unit = {},
    onPostTap: () -> Unit = {},
    onArtReady: () -> Unit = {},
    staggerVinyl: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Match iOS: when staggering, album art appears first, then vinyl fades in
    // ~5ms later with a 250ms easeIn. See FeaturedCymbalView.swift:236-247.
    var showVinyl by remember { mutableStateOf(!staggerVinyl) }
    val vinylAlpha by animateFloatAsState(
        targetValue = if (showVinyl) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = EaseIn),
        label = "vinylAlpha",
    )
    LaunchedEffect(Unit) {
        if (staggerVinyl && !showVinyl) {
            delay(3)
            showVinyl = true
        }
    }

    // Double-tap-to-like heart animation state (matches PostCard & iOS FeaturedCymbalView)
    val scope = rememberCoroutineScope()
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }

    val vinylDrawable = remember(vinylStyle) {
        when (vinylStyle) {
            VinylStyle.BLACK -> R.drawable.vinyl_black
            VinylStyle.CLEAR -> R.drawable.vinyl_clear
            VinylStyle.RED_MATTE -> R.drawable.vinyl_red_matte
            VinylStyle.PURPLE -> R.drawable.vinyl_purple
            VinylStyle.WHITE -> R.drawable.vinyl_white
            VinylStyle.GOLD -> R.drawable.vinyl_gold
            VinylStyle.RED -> R.drawable.vinyl_red
            VinylStyle.BLUE -> R.drawable.vinyl_blue
            VinylStyle.GREEN -> R.drawable.vinyl_green
        }
    }

    // Big album art position fractions (same across all vinyls)
    val artXFrac = 106f / 585f
    val artYFrac = 64f / 447f
    val artSizeFrac = 270f / 585f

    // Gradient wraps entire featured area (vinyl + title row), matching iOS .background(LinearGradient)
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
        val h = w * vinylStyle.canvasRatio

        // Vinyl + album art composite
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h),
        ) {
            // Shadow
            Image(
                painter = painterResource(R.drawable.featured_shadow),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )

            // Vinyl record
            Image(
                painter = painterResource(vinylDrawable),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(vinylAlpha),
                contentScale = ContentScale.FillBounds,
            )

            // Album art on label (circular)
            val artUrl = post.displayImageLargeURL ?: post.displayImageURL
            if (artUrl != null) {
                // Disable crossfade so returning to the profile (back-nav)
                // doesn't replay a fade-in when Coil serves from disk cache at
                // a fresh size — the featured view should restore instantly.
                val ctx = LocalContext.current
                val artRequest = remember(artUrl) {
                    ImageRequest.Builder(ctx)
                        .data(artUrl)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = artRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(
                            x = w * vinylStyle.labelXFrac,
                            y = h * vinylStyle.labelYFrac,
                        )
                        .size(
                            width = w * vinylStyle.labelWFrac,
                            height = h * vinylStyle.labelHFrac,
                        )
                        .clip(CircleShape)
                        .alpha(vinylAlpha),
                    contentScale = ContentScale.Crop,
                )

                // Big album art
                AsyncImage(
                    model = artRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = w * artXFrac, y = h * artYFrac)
                        .size(w * artSizeFrac),
                    contentScale = ContentScale.Crop,
                    onSuccess = { onArtReady() },
                )
            }

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

        // Track info + engagement row — overlaid at the bottom of the gradient
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

            Spacer(modifier = Modifier.width(CorusSpacing.md))

            // Spotify button
            Image(
                painter = painterResource(R.drawable.spotify_logo),
                contentDescription = "Play on Spotify",
                modifier = Modifier
                    .size(21.dp)
                    .clickable(onClick = onSpotifyTap),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
