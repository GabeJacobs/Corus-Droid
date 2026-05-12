package fm.corus.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.domain.ScrubberClock
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

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
      Box {
        Column {
            HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                // 11dp gap between controls — a hair more than `sm` (8dp), tuned
                // to feel close to the iOS mini-player spacing.
                horizontalArrangement = Arrangement.spacedBy(11.dp),
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
        // Scrubber overlay sits straddling the top edge of the bar (knob
        // half above, half below). Drag gesture is attached to the strip
        // itself, not the parent — so it never competes with the buttons in
        // the row below. Mirrors the iOS MiniPlayerBar scrubber.
        ScrubberOverlay(
            nowPlayingManager = nowPlayingManager,
            hasActiveTrack = state.hasActiveTrack,
            trackId = state.trackId,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )
      }
    }
}

private val knobSize = 10.dp
private val lineHeight = 2.5.dp

@Composable
private fun ScrubberOverlay(
    nowPlayingManager: NowPlayingManager,
    hasActiveTrack: Boolean,
    trackId: String?,
    modifier: Modifier = Modifier,
) {
    val time by ScrubberClock.time.collectAsState()
    val duration by ScrubberClock.duration.collectAsState()
    val canScrub = duration > 0L && hasActiveTrack
    val playbackFraction = if (duration > 0L) {
        (time.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf(0f) }
    var pendingFraction by remember { mutableStateOf<Float?>(null) }
    var isKnobVisible by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }

    val displayedFraction = when {
        isScrubbing -> scrubFraction
        pendingFraction != null -> pendingFraction!!
        else -> playbackFraction
    }

    // Smooth tween between 250ms polling ticks; immediate while dragging.
    // Keyed on trackId so a track change re-initializes the animation at the
    // new fraction (0) instead of tweening from the previous track's position.
    val animatedFraction by key(trackId) {
        animateFloatAsState(
            targetValue = displayedFraction,
            animationSpec = if (isScrubbing) snap() else tween(
                durationMillis = 250,
                easing = LinearEasing,
            ),
            label = "scrubber-fraction",
        )
    }

    val knobAlpha by animateFloatAsState(
        targetValue = if (isKnobVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "scrubber-knob",
    )

    // Knob visibility: appears on touch, lingers ~1s after release.
    LaunchedEffect(isScrubbing) {
        if (isScrubbing) {
            isKnobVisible = true
        } else if (isKnobVisible) {
            delay(1000L)
            isKnobVisible = false
        }
    }

    // Pre-seek hold release: when live time catches up to within ~2% of the
    // pending scrub target, drop the hold so the scrubber resumes following
    // live playback. Mirrors iOS pendingSeekFraction onChange watcher.
    LaunchedEffect(time, pendingFraction) {
        val pending = pendingFraction ?: return@LaunchedEffect
        if (duration > 0L && abs(playbackFraction - pending) < 0.02f) {
            pendingFraction = null
        }
    }

    // Safety: drop the pre-seek hold after 2s in case live time never catches
    // up (e.g., seek completion didn't update the clock).
    LaunchedEffect(pendingFraction) {
        if (pendingFraction == null) return@LaunchedEffect
        delay(2000L)
        pendingFraction = null
    }

    // Track-change cleanup: drop any held scrub state so the scrubber
    // immediately follows the new track instead of staying frozen.
    LaunchedEffect(trackId) {
        pendingFraction = null
        isScrubbing = false
    }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val knobSizePx = with(density) { knobSize.toPx() }

    Box(
        modifier = modifier
            .height(knobSize)
            // Center the knob on the top edge of the mini player by lifting
            // the strip up by knobSize/2.
            .offset(y = -knobSize / 2)
            .alpha(if (hasActiveTrack) 1f else 0f)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(canScrub) {
                if (!canScrub) return@pointerInput
                // Horizontal-only drag detection: the gesture only engages once
                // the user has moved past the platform's horizontal touch slop,
                // so vertical "scroll the feed" drags on the strip are
                // ignored, and pure taps don't commit a phantom seek.
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isScrubbing = true
                        scrubFraction = if (widthPx > 0) {
                            (offset.x / widthPx).coerceIn(0f, 1f)
                        } else 0f
                    },
                    onDragEnd = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (duration > 0L) {
                            pendingFraction = scrubFraction
                            nowPlayingManager.seek((scrubFraction * duration).toLong())
                        }
                        isScrubbing = false
                    },
                    onDragCancel = { isScrubbing = false },
                    onHorizontalDrag = { change, _ ->
                        if (widthPx > 0) {
                            scrubFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        }
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track line (full width, behind everything)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(lineHeight)
                .clip(CircleShape)
                .background(CorusColors.Divider),
        )
        // Filled progress
        val fillWidthPx = (animatedFraction * widthPx).coerceAtLeast(0f)
        Box(
            modifier = Modifier
                .offset { IntOffset(0, 0) }
                .width(with(density) { fillWidthPx.toDp() })
                .height(lineHeight)
                .clip(CircleShape)
                .background(CorusColors.Secondary),
        )
        // Knob
        val knobX = (animatedFraction * widthPx - knobSizePx / 2f).coerceIn(
            -knobSizePx / 2f,
            (widthPx - knobSizePx / 2f).coerceAtLeast(-knobSizePx / 2f),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(knobX.roundToInt(), 0) }
                .size(knobSize)
                .alpha(knobAlpha)
                .clip(CircleShape)
                .background(CorusColors.Secondary)
                .zIndex(1f),
        )
    }
}
