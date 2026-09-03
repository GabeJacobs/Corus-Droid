package fm.corus.android.ui.screens.messaging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Width/height that keeps [naturalWidth]×[naturalHeight] inside [maxWidth]×[maxHeight]
 * without letterboxing. Same math as iOS `AnimatedGifView.aspectFittedSize` — size the
 * frame to the media, then fill it, instead of fitting media into a fixed box.
 */
internal data class FittedDpSize(val width: Dp, val height: Dp)

internal fun aspectFittedDp(
    naturalWidth: Float,
    naturalHeight: Float,
    maxWidth: Dp,
    maxHeight: Dp,
): FittedDpSize? {
    if (naturalWidth <= 0f || naturalHeight <= 0f) return null
    var width = maxWidth
    var height = maxWidth * (naturalHeight / naturalWidth)
    if (height > maxHeight) {
        height = maxHeight
        width = maxHeight * (naturalWidth / naturalHeight)
    }
    return FittedDpSize(width, height)
}

/**
 * Photo / GIF bubble media. The frame grows to the content's aspect ratio (capped
 * to [maxWidth]×[maxHeight]) so landscape GIFs no longer sit in a tall gray box
 * with letterbox bars — matching how iOS sizes comment / wrap-to-content media.
 */
@Composable
internal fun MessageMediaImage(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 240.dp,
    maxHeight: Dp = 300.dp,
    placeholderHeight: Dp = 150.dp,
    onClick: (() -> Unit)? = null,
) {
    var fitted by remember(url) { mutableStateOf<FittedDpSize?>(null) }
    val sizeModifier = fitted?.let { Modifier.size(it.width, it.height) }
        ?: Modifier.width(maxWidth).height(placeholderHeight)

    ShimmerAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = sizeModifier
            .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(modifier),
        // Frame already matches the media ratio, so Crop fills edge-to-edge
        // without the Fit+Skeleton letterboxing the old max-size box produced.
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            if (fitted != null) return@ShimmerAsyncImage
            val intrinsic = state.painter.intrinsicSize
            if (!intrinsic.width.isFinite() || !intrinsic.height.isFinite()) return@ShimmerAsyncImage
            aspectFittedDp(intrinsic.width, intrinsic.height, maxWidth, maxHeight)?.let {
                fitted = it
            }
        },
    )
}
