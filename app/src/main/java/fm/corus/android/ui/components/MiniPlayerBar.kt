package fm.corus.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@Composable
fun MiniPlayerBar(
    nowPlayingManager: NowPlayingManager,
    onTrackTap: (() -> Unit)? = null,
    engagementManager: PostEngagementManager? = null,
    onLikeTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by nowPlayingManager.state.collectAsState()
    val engagementStates = engagementManager?.states?.collectAsState()?.value ?: emptyMap()
    val isCurrentTrackLiked = state.sourcePostId
        ?.let { engagementStates[it]?.isLiked }
        ?: false
    val context = LocalContext.current

    AnimatedVisibility(
        visible = state.hasActiveTrack,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column {
            HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
            ) {
                // Album art + track info (tappable)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onTrackTap != null) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onTrackTap,
                            ) else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                ) {
                    state.albumArtURL?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = state.trackName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(CorusSpacing.cornerRadius)),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(CorusSpacing.xxs),
                    ) {
                        MarqueeText(
                            text = state.trackName,
                            style = CorusFont.username,
                            color = CorusColors.Text,
                        )
                        Text(
                            text = state.artistName,
                            style = CorusFont.caption,
                            color = CorusColors.Secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Like (heart) — only when the current track has a source post.
                if (state.sourcePostId != null && onLikeTap != null) {
                    Icon(
                        imageVector = if (isCurrentTrackLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.post_card_cd_like),
                        modifier = Modifier
                            // Extra breathing room before the heart so the
                            // title/artist text doesn't crowd it. Matches the
                            // larger visual gap on iOS.
                            .padding(start = CorusSpacing.sm)
                            .size(22.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onLikeTap,
                            ),
                        tint = if (isCurrentTrackLiked) CorusColors.Like else CorusColors.Text,
                    )
                }

                // Play/Pause
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) stringResource(R.string.mini_player_cd_pause) else stringResource(R.string.mini_player_cd_play),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { nowPlayingManager.togglePlayPause() },
                        ),
                    tint = CorusColors.Text,
                )

                // Next — always visible so the mini player has a consistent layout;
                // disabled (grayed) when the current queue has no next track.
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.mini_player_cd_next),
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (state.hasNext) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { nowPlayingManager.skipToNext() },
                            ) else Modifier
                        ),
                    tint = if (state.hasNext) CorusColors.Text else CorusColors.Tertiary,
                )

                // Spotify / SoundCloud button (matches the source of the playing track)
                val isSoundCloud = state.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD
                if (isSoundCloud) {
                    SoundCloudAdaptiveLogo(
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                val permalink = state.soundcloudPermalinkUrl
                                if (!permalink.isNullOrBlank()) {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(permalink))) }
                                }
                            },
                        size = 22.dp,
                    )
                } else {
                    Image(
                        painter = painterResource(fm.corus.android.R.drawable.spotify_logo),
                        contentDescription = stringResource(R.string.mini_player_cd_open_spotify),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                val uri = state.spotifyURI
                                val webUrl = state.spotifyWebURL
                                val opened = if (!uri.isNullOrBlank()) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                                        true
                                    } catch (_: Exception) { false }
                                } else false
                                if (!opened && !webUrl.isNullOrBlank()) {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) } catch (_: Exception) { }
                                }
                            },
                    )
                }
            }
            HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
        }
    }
}
