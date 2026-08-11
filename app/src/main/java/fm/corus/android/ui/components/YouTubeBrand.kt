package fm.corus.android.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fm.corus.android.R

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
 * Dismiss control for an inline YouTube player. Must be rendered *outside*
 * the player frame — YouTube RMF forbids overlays on the embedded player or
 * its controls (Developer Policies III.C.1).
 */
@Composable
fun YouTubePlayerDismiss(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val label = contentDescription ?: stringResource(R.string.post_card_cd_close_trailer)
    Box(
        modifier = modifier
            .size(32.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}
