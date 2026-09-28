package fm.corus.android.ui.screens.messaging

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.theme.CorusSpacing

/** Matches the fixed photo/video/GIF frame in the iOS message thread. */
internal val MessageMediaPreviewSize = 180.dp

/** Keep loading and loaded media in the same compact, center-cropped square. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MessageMediaImage(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    ShimmerAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = Modifier.size(MessageMediaPreviewSize)
            .clip(RoundedCornerShape(CorusSpacing.cornerRadius))
            .then(
                if (onClick != null || onDoubleClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick ?: {},
                        onDoubleClick = onDoubleClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            )
            .then(modifier),
        contentScale = ContentScale.Crop,
    )
}
