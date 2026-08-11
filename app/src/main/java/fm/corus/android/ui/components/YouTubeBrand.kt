package fm.corus.android.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusSpacing

/** Official YouTube watch URL for an embed / Data API video id. */
fun youTubeWatchUrl(youtubeId: String): String =
    "https://www.youtube.com/watch?v=${Uri.encode(youtubeId)}"

/** Opens the YouTube app or browser for [youtubeId]. */
fun openYouTubeWatch(context: Context, youtubeId: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(youTubeWatchUrl(youtubeId)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Official YouTube Icon (brand.youtube Full-color Icon — yt_icon_red_digital).
 * Branding guidelines require height ≥ 20dp and no redraws of the mark.
 */
@Composable
fun YouTubeIcon(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    contentDescription: String? = null,
) {
    val h = maxOf(height, 20.dp)
    Image(
        painter = painterResource(R.drawable.yt_icon_red),
        contentDescription = contentDescription,
        modifier = modifier.height(h),
        contentScale = ContentScale.Fit,
    )
}

/**
 * Dismiss control for an inline YouTube player. Must sit outside the embed
 * iframe (YouTube RMF / III.C.1) — e.g. in letterbox above a 16:9 player, or
 * in a chrome row beside the player.
 *
 * Styled like iOS: plain xmark, no circle chrome, inset padding.
 *
 * @param onDark true when drawn on a black letterbox (white glyph); false when
 * drawn on the light feed chrome (dark glyph).
 */
@Composable
fun YouTubePlayerDismiss(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = true,
    contentDescription: String? = null,
) {
    val label = contentDescription ?: stringResource(R.string.post_card_cd_close_trailer)
    Box(
        modifier = modifier
            // Match iOS: inset from the corner, then a tappable area around a
            // small plain xmark (no circle chrome).
            .padding(CorusSpacing.md)
            .size(30.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = label,
            tint = if (onDark) Color.White else Color.Black,
            modifier = Modifier.size(14.dp),
        )
    }
}
