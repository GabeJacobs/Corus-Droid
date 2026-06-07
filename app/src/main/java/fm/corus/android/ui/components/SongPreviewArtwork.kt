package fm.corus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.domain.NowPlayingManager
import kotlinx.coroutines.launch

/**
 * Album art with a tap-to-preview play/pause overlay — same UX as
 * [CommentAttachmentCard]'s artwork. Tap the artwork to toggle the preview.
 */
@Composable
fun SongPreviewArtwork(
    track: CymbalTrack,
    nowPlaying: NowPlayingManager,
    size: Dp,
    cornerRadius: Dp,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) {
    val state by nowPlaying.state.collectAsState()
    val loadingTrackId by nowPlaying.loadingTrackId.collectAsState()
    val isLoading = loadingTrackId == track.id
    val isPlaying = state.trackId == track.id && state.isPlaying
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable {
                if (state.trackId == track.id) {
                    nowPlaying.togglePlayPause()
                } else {
                    scope.launch {
                        nowPlaying.play(
                            trackId = track.id,
                            trackName = track.name,
                            artistName = track.artistName,
                            albumArtURL = track.albumArtURL,
                            albumArtLargeURL = track.albumArtLargeURL,
                            previewUrl = track.previewUrl,
                            spotifyURI = track.spotifyURI,
                            spotifyWebURL = track.spotifyWebURL,
                            isrc = track.isrc,
                            source = track.source,
                            soundcloudId = track.soundcloudId,
                            soundcloudPermalinkUrl = track.soundcloudPermalinkUrl,
                        )
                    }
                }
            },
    ) {
        AsyncImage(
            model = track.albumArtURL ?: track.albumArtLargeURL,
            contentDescription = contentDescription,
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
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
                else -> Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
