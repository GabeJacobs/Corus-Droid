package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import fm.corus.android.R
import fm.corus.android.data.model.CommentAttachedFilm
import fm.corus.android.data.model.CommentAttachedSong
import fm.corus.android.data.model.CymbalMovie
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Compact attachment card rendered inside a comment when the comment carries a
 * song or film. Tapping the artwork plays a preview (song) or navigates (film).
 * Tapping the title region navigates to the song/film detail page.
 */
@Composable
fun CommentAttachmentCard(
    attachedSong: CommentAttachedSong?,
    attachedFilm: CommentAttachedFilm?,
    nowPlaying: NowPlayingManager,
    onNavigateToSong: (CymbalTrack) -> Unit,
    onNavigateToFilm: (CymbalMovie) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by nowPlaying.state.collectAsState()
    val loadingTrackId by nowPlaying.loadingTrackId.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CorusColors.CardBackground.copy(alpha = 0.7f))
            .border(0.5.dp, CorusColors.Divider.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(CorusSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            attachedSong != null -> {
                val isLoading = loadingTrackId == attachedSong.trackId
                val isPlaying = state.trackId == attachedSong.trackId && state.isPlaying

                // Artwork — tap to play/pause preview
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (state.trackId == attachedSong.trackId) {
                                nowPlaying.togglePlayPause()
                            } else {
                                scope.launch {
                                    nowPlaying.play(
                                        trackId = attachedSong.trackId,
                                        trackName = attachedSong.trackName,
                                        artistName = attachedSong.artistName,
                                        albumArtURL = attachedSong.albumArtURL,
                                        albumArtLargeURL = attachedSong.albumArtLargeURL,
                                        previewUrl = attachedSong.previewUrl,
                                        spotifyURI = attachedSong.spotifyURI,
                                        spotifyWebURL = attachedSong.spotifyWebURL,
                                        isrc = attachedSong.isrc,
                                    )
                                }
                            }
                        },
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(attachedSong.albumArtURL).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isLoading -> CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = Color.White,
                            )
                            isPlaying -> Icon(
                                Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.comment_attachment_pause_preview),
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                            else -> Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.comment_attachment_play_preview),
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(CorusSpacing.sm))

                // Title region — tap to navigate
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToSong(attachedSong.asCymbalTrack()) },
                ) {
                    Text(
                        text = attachedSong.trackName,
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = attachedSong.artistName,
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            attachedFilm != null -> {
                // Poster — tap to navigate
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 54.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onNavigateToFilm(attachedFilm.asCymbalMovie()) },
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(attachedFilm.posterURL).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                Spacer(Modifier.width(CorusSpacing.sm))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToFilm(attachedFilm.asCymbalMovie()) },
                ) {
                    Text(
                        text = attachedFilm.movieTitle,
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val secondary = when {
                        attachedFilm.directorName.isNotEmpty() && attachedFilm.releaseYear.isNotEmpty() ->
                            "${attachedFilm.directorName} · ${attachedFilm.releaseYear}"
                        attachedFilm.directorName.isNotEmpty() -> attachedFilm.directorName
                        else -> attachedFilm.releaseYear
                    }
                    if (secondary.isNotEmpty()) {
                        Text(
                            text = secondary,
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pill-style chip shown in the composer above the text field while an
 * attachment is pending. Hugs content (does not stretch full width).
 */
@Composable
fun CommentAttachmentPendingChip(
    attachedSong: CommentAttachedSong?,
    attachedFilm: CommentAttachedFilm?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    nowPlaying: NowPlayingManager? = null,
    pendingGif: fm.corus.android.data.model.KlipyGif? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = nowPlaying?.state?.collectAsState()?.value
    val loadingTrackId = nowPlaying?.loadingTrackId?.collectAsState()?.value

    // GIF chip is rendered as just the preview image with an X overlay (no pill,
    // no labels) — mirrors the iOS pattern.
    if (pendingGif != null) {
        val maxWidthDp = 160.dp
        val maxHeightDp = 120.dp
        val aspect = if (pendingGif.thumbnailHeight > 0)
            pendingGif.thumbnailWidth.toFloat() / pendingGif.thumbnailHeight.toFloat()
        else 1f
        val safeAspect = if (aspect > 0f) aspect else 1f
        val (gifW, gifH) = run {
            val targetH = minOf(maxHeightDp.value, maxWidthDp.value / safeAspect)
            val targetW = minOf(maxWidthDp.value, targetH * safeAspect)
            targetW.dp to targetH.dp
        }
        Box(modifier = modifier) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(pendingGif.thumbnailURL).build(),
                contentDescription = null,
                modifier = Modifier
                    .size(width = gifW, height = gifH)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { onClear() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.comment_attachment_remove),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        return
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CorusColors.CardBackground)
            .padding(horizontal = CorusSpacing.sm, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            attachedSong != null -> {
                val isLoading = loadingTrackId == attachedSong.trackId
                val isPlaying = state?.trackId == attachedSong.trackId && state?.isPlaying == true

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (nowPlaying != null) {
                                Modifier.clickable {
                                    if (state?.trackId == attachedSong.trackId) {
                                        nowPlaying.togglePlayPause()
                                    } else {
                                        scope.launch {
                                            nowPlaying.play(
                                                trackId = attachedSong.trackId,
                                                trackName = attachedSong.trackName,
                                                artistName = attachedSong.artistName,
                                                albumArtURL = attachedSong.albumArtURL,
                                                albumArtLargeURL = attachedSong.albumArtLargeURL,
                                                previewUrl = attachedSong.previewUrl,
                                                spotifyURI = attachedSong.spotifyURI,
                                                spotifyWebURL = attachedSong.spotifyWebURL,
                                                isrc = attachedSong.isrc,
                                            )
                                        }
                                    }
                                }
                            } else Modifier,
                        ),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(attachedSong.albumArtURL).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (nowPlaying != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isLoading -> CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color.White,
                                )
                                isPlaying -> Icon(
                                    Icons.Filled.Pause,
                                    contentDescription = stringResource(R.string.comment_attachment_pause_preview),
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp),
                                )
                                else -> Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.comment_attachment_play_preview),
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(CorusSpacing.sm))
                Column(modifier = Modifier.widthIn(max = 200.dp)) {
                    Text(
                        text = attachedSong.trackName,
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = attachedSong.artistName,
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            attachedFilm != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(attachedFilm.posterURL).build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 28.dp, height = 42.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(CorusSpacing.sm))
                Column(modifier = Modifier.widthIn(max = 200.dp)) {
                    Text(
                        text = attachedFilm.movieTitle,
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val secondary = when {
                        attachedFilm.directorName.isNotEmpty() && attachedFilm.releaseYear.isNotEmpty() ->
                            "${attachedFilm.directorName} · ${attachedFilm.releaseYear}"
                        attachedFilm.directorName.isNotEmpty() -> attachedFilm.directorName
                        else -> attachedFilm.releaseYear
                    }
                    if (secondary.isNotEmpty()) {
                        Text(
                            text = secondary,
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(CorusSpacing.sm))
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.comment_attachment_remove),
            tint = CorusColors.Tertiary,
            modifier = Modifier
                .size(18.dp)
                .clickable { onClear() },
        )
    }
}
