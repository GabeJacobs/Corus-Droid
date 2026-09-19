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
    preview: MessageLinkPreview?,
    isFromCurrentUser: Boolean,
    hero: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (hero && preview == null) {
        HeroLinkPreviewSkeleton(isFromCurrentUser = isFromCurrentUser, modifier = modifier)
        return
    }
    val resolved = preview ?: return
    val open = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resolved.openUrl)))
        }
        Unit
    }
    if (hero) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .clickable(onClick = open)
                .background(
                    if (isFromCurrentUser) CorusColors.Accent
                    else CorusColors.CardBackground
                ),
        ) {
            HeroMedia(preview)
            CaptionBar(preview, isFromCurrentUser)
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
            .height(if (preview.imageURL.isNullOrBlank()) 72.dp else 160.dp)
            .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        preview.imageURL?.takeIf { it.isNotBlank() }?.let { url ->
            ShimmerAsyncImage(
                model = url,
                contentDescription = preview.title,
                modifier = Modifier.fillMaxWidth().height(160.dp),
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
private fun CaptionBar(preview: MessageLinkPreview, isFromCurrentUser: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFromCurrentUser) CorusColors.Accent else CorusColors.CardBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        preview.title?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = CorusFont.bodyMedium,
                color = if (isFromCurrentUser) Color.White else CorusColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val site = preview.siteName?.takeIf { it.isNotBlank() }
        Text(
            text = if (site != null) "$site  ${preview.displayDomain}" else preview.displayDomain,
            style = CorusFont.caption,
            color = if (isFromCurrentUser) Color.White.copy(alpha = 0.8f) else CorusColors.Secondary,
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

@Composable
private fun HeroLinkPreviewSkeleton(
    isFromCurrentUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val fill = if (isFromCurrentUser) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.08f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(if (isFromCurrentUser) CorusColors.Accent else CorusColors.CardBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(fill),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(fill),
            )
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.35f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(fill.copy(alpha = fill.alpha * 0.7f)),
            )
        }
    }
}
