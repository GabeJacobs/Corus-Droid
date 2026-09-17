package fm.corus.android.ui.screens.messaging

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fm.corus.android.data.model.MessageLinkPreview
import fm.corus.android.ui.components.ShimmerAsyncImage
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun MessageLinkPreviewCard(
    preview: MessageLinkPreview,
    isFromCurrentUser: Boolean,
    hero: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val open = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(preview.openUrl)))
        }
    }
    if (hero) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .clickable(onClick = open)
                .background(
                    if (isFromCurrentUser) Color.White.copy(alpha = 0.16f)
                    else Color.Black.copy(alpha = 0.06f)
                ),
        ) {
            HeroMedia(preview)
            CaptionBar(preview)
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = open)
                .background(
                    if (isFromCurrentUser) Color.White.copy(alpha = 0.14f)
                    else CorusColors.Text.copy(alpha = 0.05f)
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                preview.title?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = CorusFont.bodyMedium,
                        color = if (isFromCurrentUser) Color.White else CorusColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                preview.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = CorusFont.caption,
                        color = if (isFromCurrentUser) Color.White.copy(alpha = 0.8f) else CorusColors.Secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = compactFooter(preview),
                    style = CorusFont.caption,
                    color = if (isFromCurrentUser) Color.White.copy(alpha = 0.7f) else CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val thumb = preview.imageURL
            if (!thumb.isNullOrBlank()) {
                ShimmerAsyncImage(
                    model = thumb,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else if (preview.kind == "file" || preview.kind == "audio") {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isFromCurrentUser) Color.White.copy(alpha = 0.18f)
                            else CorusColors.Text.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (preview.kind == "audio") Icons.Filled.MusicNote else Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = if (isFromCurrentUser) Color.White else CorusColors.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMedia(preview: MessageLinkPreview) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (preview.imageURL.isNullOrBlank()) 88.dp else 220.dp)
            .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        preview.imageURL?.takeIf { it.isNotBlank() }?.let { url ->
            ShimmerAsyncImage(
                model = url,
                contentDescription = preview.title,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentScale = ContentScale.Crop,
            )
        }
        if (preview.showsPlayBadge) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun CaptionBar(preview: MessageLinkPreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        preview.title?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = CorusFont.bodyMedium,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val site = preview.siteName?.takeIf { it.isNotBlank() }
        Text(
            text = if (site != null) "$site  ${preview.displayDomain}" else preview.displayDomain,
            style = CorusFont.caption,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun compactFooter(preview: MessageLinkPreview): String {
    if (preview.kind == "file" || preview.kind == "audio") {
        return preview.fileName?.takeIf { it.isNotBlank() } ?: preview.displayDomain
    }
    val site = preview.siteName?.takeIf { it.isNotBlank() }
    return if (site != null) "$site · ${preview.displayDomain}" else preview.displayDomain
}
