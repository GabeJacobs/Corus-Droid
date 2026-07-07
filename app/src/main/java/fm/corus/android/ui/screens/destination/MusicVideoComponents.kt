package fm.corus.android.ui.screens.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.MusicVideo
import fm.corus.android.ui.components.InlineYouTubePlayer
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
) {
    Column {
        DestinationSectionHeader(
            title = stringResource(R.string.destination_music_videos),
            onSeeAll = onSeeAll,
        )

        // Inline player panel above the rail (mirrors web): the tapped card's
        // FULL video plays here, session never leaves the app.
        val activeYouTubeId = activeVideo?.youtubeId
        if (activeYouTubeId != null) {
            MusicVideoPlayerPanel(
                youtubeId = activeYouTubeId,
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
 */
@Composable
fun MusicVideoCard(
    video: MusicVideo,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .background(CorusColors.CardBackground)
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
        Text(
            text = video.title,
            style = CorusFont.captionMedium,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
 * official video via the bare-embed player (never `new YT.Player`), with a
 * close affordance.
 */
@Composable
fun MusicVideoPlayerPanel(
    youtubeId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
            .background(Color.Black),
    ) {
        InlineYouTubePlayer(
            videoID = youtubeId,
            modifier = Modifier.fillMaxSize(),
            showControls = true,
            onEnded = onClose,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CorusSpacing.sm)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.feed_cd_back),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
