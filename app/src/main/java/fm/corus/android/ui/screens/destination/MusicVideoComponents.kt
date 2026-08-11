package fm.corus.android.ui.screens.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.MusicVideo
import fm.corus.android.ui.components.InlineYouTubePlayer
import fm.corus.android.ui.components.YouTubePlayerDismiss
import fm.corus.android.ui.components.openYouTubeWatch
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * "Music videos" rail for the artist page — YouTube-matched official videos
 * only, each playing the FULL video inline. Shows up to 12 with a "See all"
 * into the newest-first grid. Mirrors web + iOS.
 */
@Composable
fun MusicVideoRail(
    videos: List<MusicVideo>,
    activeVideo: MusicVideo?,
    onPlay: (MusicVideo) -> Unit,
    onClosePlayer: () -> Unit,
    onSeeAll: (() -> Unit)?,
    title: String = stringResource(R.string.destination_music_videos),
) {
    Column {
        DestinationSectionHeader(
            title = title,
            onSeeAll = onSeeAll,
        )

        // Inline player panel above the rail (mirrors web): the tapped card's
        // FULL video plays here, session never leaves the app.
        val active = activeVideo
        val activeYouTubeId = active?.youtubeId
        if (active != null && activeYouTubeId != null) {
            MusicVideoPlayerPanel(
                youtubeId = activeYouTubeId,
                title = active.title,
                onClose = onClosePlayer,
                modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
        ) {
            val shown = videos.take(12)
            items(shown.size) { index ->
                val video = shown[index]
                MusicVideoCard(
                    video = video,
                    isActive = activeVideo?.id == video.id,
                    onClick = { onPlay(video) },
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }
}

/**
 * One music-video card (16:9 thumbnail with a play badge + title +
 * year·duration). Shared by the artist-page rail and the "See all" grid.
 * Titles for YouTube-matched videos open the YouTube watch page (III.I.4).
 */
@Composable
fun MusicVideoCard(
    video: MusicVideo,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .background(CorusColors.CardBackground)
                .clickable(onClick = onClick)
                .then(
                    if (isActive) {
                        Modifier.border(
                            2.dp,
                            CorusColors.Accent,
                            RoundedCornerShape(CorusSpacing.cornerRadiusMedium),
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (video.thumbnailUrl != null) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (!isActive) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        val youtubeId = video.youtubeId
        Text(
            text = video.title,
            style = CorusFont.captionMedium,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (youtubeId != null) TextDecoration.Underline else TextDecoration.None,
            modifier = if (youtubeId != null) {
                Modifier.clickable { openYouTubeWatch(context, youtubeId) }
            } else {
                Modifier
            },
        )
        Text(
            text = video.yearAndDurationLabel,
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The inline 16:9 YouTube panel shared by the rail and grid. Plays the FULL
 * official video via the bare-embed player (never `new YT.Player`). Dismiss
 * and title sit outside the player frame (YouTube RMF / III.C.1 / III.I.4).
 */
@Composable
fun MusicVideoPlayerPanel(
    youtubeId: String,
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CorusSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = CorusFont.captionMedium,
                color = CorusColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .weight(1f)
                    .clickable { openYouTubeWatch(context, youtubeId) },
            )
            YouTubePlayerDismiss(onClose = onClose, onDark = false)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .background(Color.Black),
        ) {
            // key() on the id so tapping a DIFFERENT video disposes the old WebView
            // and creates a fresh one for the new id (mirrors web's keyed iframe).
            // InlineYouTubePlayer loads the embed once in its factory and never
            // reloads on a videoID change, so without this the first video keeps
            // playing.
            key(youtubeId) {
                InlineYouTubePlayer(
                    videoID = youtubeId,
                    modifier = Modifier.fillMaxSize(),
                    showControls = true,
                    onEnded = onClose,
                )
            }
        }
    }
}
