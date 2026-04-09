package fm.corus.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPostTap),
    ) {
        // Frame + poster composite
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val w = maxWidth
            val h = w / sectionAspect

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
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = w * posterXRatio, y = h * posterYRatio)
                            .size(width = w * posterWRatio, height = h * posterHRatio),
                        contentScale = ContentScale.Crop,
                    )
                }

                // Glass overlay (screen blend)
                Image(
                    painter = painterResource(R.drawable.frame_glass_overlay),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f),
                        blendMode = BlendMode.Screen,
                    ),
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
            }
        }

        // Movie info + engagement row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.md),
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
                    tint = if (isLiked) CorusColors.Like else CorusColors.Tertiary,
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

            // Trailer button (if available)
            if (post.trailerURL != null) {
                Spacer(modifier = Modifier.width(CorusSpacing.md))
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Watch Trailer",
                    tint = CorusColors.Accent,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onTrailerTap),
                )
            }
        }
    }
}
