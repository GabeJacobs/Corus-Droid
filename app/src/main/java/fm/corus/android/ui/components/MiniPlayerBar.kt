package fm.corus.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import fm.corus.android.domain.HapticManager
import fm.corus.android.ui.LocalHapticManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fm.corus.android.R
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.domain.PostPlaybackHighlight
import fm.corus.android.domain.ScrubberClock
import fm.corus.android.domain.SongPlayRouting
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerBar(
    nowPlayingManager: NowPlayingManager,
    onTrackTap: (() -> Unit)? = null,
    engagementManager: PostEngagementManager? = null,
    onLikeTap: (() -> Unit)? = null,
    musicService: fm.corus.android.data.model.MusicService = fm.corus.android.data.model.MusicService.SPOTIFY,
    playFullSongs: Boolean = false,
    alwaysPlayFullSongs: Boolean = false,
    onPlaybackModeChange: (Boolean) -> Unit = {},
    remoteConfig: fm.corus.android.service.RemoteConfigService? = null,
    resolveLinkOut: (suspend () -> String?)? = null,
    /** When true, hide hairline dividers — the expanding shell owns the surface. */
    embeddedInExpandingShell: Boolean = false,
    /** When false, chrome is visible but taps are ignored (mid expand-drag). */
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val state by nowPlayingManager.state.collectAsState()
    val isHydratingExternalSpotify by nowPlayingManager.isHydratingExternalSpotify.collectAsState()
    val isExternalSpotifyListening by nowPlayingManager.isExternalSpotifyListeningFlow.collectAsState()
    val showsMiniPlayer = state.hasActiveTrack && !isHydratingExternalSpotify &&
        (!isExternalSpotifyListening ||
            (state.trackName.isNotBlank() && state.trackName != "Unknown Track"))
    val engagementStates = engagementManager?.states?.collectAsState()?.value ?: emptyMap()
    val isCurrentTrackLiked = state.sourcePostId
        ?.let { engagementStates[it]?.isLiked }
        ?: false
    val context = LocalContext.current
    val haptics = LocalHapticManager.current
    val linkOutScope = androidx.compose.runtime.rememberCoroutineScope()
    // 30s/Full when Always Play Full Songs is off, in-app full is available, and
    // we aren't mirroring external Spotify. Otherwise the service link-out logo.
    val showsPlaybackModeToggle = remoteConfig != null &&
        SongPlayRouting.showsMiniPlayerPlaybackModeToggle(
            context = context,
            source = state.source,
            service = musicService,
            remoteConfig = remoteConfig,
            isExternalSpotifyListening = isExternalSpotifyListening,
            alwaysPlayFullSongs = alwaysPlayFullSongs,
        )
    val playbackModeToggleShowsFull = playFullSongs

    val stagedFeedSkipLoading by nowPlayingManager.stagedFeedSkipLoading.collectAsState()
    val loadingTrackId by nowPlayingManager.loadingTrackId.collectAsState()
    val isResolvingSpotify by nowPlayingManager.isResolvingSpotifyFlow.collectAsState()
    val showsTransportSpinner = PostPlaybackHighlight.showsTransportSpinner(
        stagedFeedSkipLoading = stagedFeedSkipLoading,
        isPlaying = state.isPlaying,
        isPreviewMode = nowPlayingManager.isPreviewMode,
        loadingTrackId = loadingTrackId,
        currentTrackId = state.trackId,
        isResolvingFullSong = isResolvingSpotify,
    )

    val isSoundCloud = state.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD
    val isAudiomack = state.source == fm.corus.android.data.model.TrackSource.AUDIOMACK
    val isTidal = state.source == fm.corus.android.data.model.TrackSource.TIDAL
    val isDeezer = state.source == fm.corus.android.data.model.TrackSource.DEEZER
    val displayedService = SongPlayRouting.displayedLinkOutService(
        source = state.source,
        viewer = musicService,
        knownNotOnSpotify = fm.corus.android.domain.MusicServiceLinkOut.knownNotOnSpotify(state.trackId),
    )

    // Same destination as the service glyph (and iOS viewCurrentInMusicService).
    // Long-press on art/title uses this when the 30s/Full toggle displaced the glyph.
    fun openCurrentInMusicService() {
        fm.corus.android.domain.MusicServiceLinkOut.openNowPlayingInPreferredService(
            context = context,
            scope = linkOutScope,
            state = state,
            musicService = musicService,
            resolveLinkOut = resolveLinkOut,
        )
    }

    AnimatedVisibility(
        visible = showsMiniPlayer,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
      // Keep edge-swipes on the bar from firing Android's back gesture
      // (scrubbing from 0% / the Next button live in the back-gesture zone).
      Box(Modifier.systemGestureExclusion()) {
        Column {
            if (!embeddedInExpandingShell) {
                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Scrubber's expanded touch strip straddles this row's top edge;
                    // win hit tests here so transport controls stay tappable.
                    .zIndex(2f)
                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                // 11dp gap between controls — a hair more than `sm` (8dp), tuned
                // to feel close to the iOS mini-player spacing.
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                // Album art + track info: tap → expand / detail; long-press → open in service.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            enabled = interactive,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTrackTap?.invoke() },
                            onLongClick = {
                                // iOS mini long-press → service — medium.
                                haptics.impact(HapticManager.ImpactStyle.MEDIUM)
                                openCurrentInMusicService()
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                ) {
                    // Prefer the large feed URL so Coil hits the already-warmed
                    // memory cache — avoids a blank art tile on first mini paint.
                    val artUrl = state.albumArtLargeURL ?: state.albumArtURL
                    if (artUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artUrl)
                                .crossfade(false)
                                .build(),
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

                // Mode / service control sits left of transport so skip stays at the far edge.
                if (showsPlaybackModeToggle) {
                    MiniPlayerPlaybackModeToggle(
                        playFullSongs = playbackModeToggleShowsFull,
                        onSelect = onPlaybackModeChange,
                        modifier = Modifier.width(miniPlayerServiceButtonWidth + 40.dp),
                    )
                } else if (isSoundCloud) {
                    MiniPlayerIconButton(
                        onClick = { openCurrentInMusicService() },
                        contentDescription = stringResource(R.string.mini_player_cd_open_spotify),
                        width = miniPlayerServiceButtonWidth,
                    ) {
                        SoundCloudAdaptiveLogo(size = 22.dp)
                    }
                } else if (isAudiomack) {
                    // Wave mark is wider than other service tiles — give it room
                    // so ContentScale.Fit doesn't clip the right tip.
                    MiniPlayerIconButton(
                        onClick = { openCurrentInMusicService() },
                        contentDescription = stringResource(R.string.mini_player_cd_open_spotify),
                        width = miniPlayerAudiomackButtonWidth,
                    ) {
                        AudiomackLogo(height = 22.dp)
                    }
                } else if (isTidal) {
                    MiniPlayerIconButton(
                        onClick = { openCurrentInMusicService() },
                        contentDescription = stringResource(R.string.mini_player_cd_open_spotify),
                        width = miniPlayerServiceButtonWidth,
                    ) {
                        Image(
                            painter = painterResource(fm.corus.android.domain.MusicServiceLinkOut.logoRes(fm.corus.android.data.model.MusicService.TIDAL)),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else if (isDeezer) {
                    MiniPlayerIconButton(
                        onClick = { openCurrentInMusicService() },
                        contentDescription = stringResource(R.string.mini_player_cd_open_spotify),
                        width = miniPlayerServiceButtonWidth,
                    ) {
                        Image(
                            painter = painterResource(fm.corus.android.domain.MusicServiceLinkOut.logoRes(fm.corus.android.data.model.MusicService.DEEZER)),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    MiniPlayerIconButton(
                        onClick = { openCurrentInMusicService() },
                        contentDescription = stringResource(R.string.mini_player_cd_open_spotify),
                        width = miniPlayerServiceButtonWidth,
                    ) {
                        Image(
                            painter = painterResource(fm.corus.android.domain.MusicServiceLinkOut.logoRes(displayedService)),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Like (heart) — only when the current track has a source post.
                if (state.sourcePostId != null && onLikeTap != null) {
                    MiniPlayerIconButton(
                        onClick = onLikeTap,
                        contentDescription = stringResource(R.string.post_card_cd_like),
                    ) {
                        Icon(
                            imageVector = if (isCurrentTrackLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (isCurrentTrackLiked) CorusColors.Like else CorusColors.Text,
                        )
                    }
                }

                // Play/Pause — spinner from Next tap until audio is live (iOS
                // MiniPlayerBar.showsTransportSpinner). Tappable to cancel a hung load.
                MiniPlayerIconButton(
                    onClick = {
                        if (showsTransportSpinner) {
                            nowPlayingManager.cancelLoading()
                        } else {
                            nowPlayingManager.togglePlayPause()
                        }
                    },
                    contentDescription = when {
                        showsTransportSpinner -> stringResource(R.string.mini_player_cd_cancel_loading)
                        state.isPlaying -> stringResource(R.string.mini_player_cd_pause)
                        else -> stringResource(R.string.mini_player_cd_play)
                    },
                ) {
                    if (showsTransportSpinner) {
                        CircularProgressIndicator(
                            color = CorusColors.Text,
                            trackColor = CorusColors.Text.copy(alpha = 0.22f),
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = CorusColors.Text,
                        )
                    }
                }

                // Next — always visible so the mini player has a consistent layout;
                // disabled (grayed) when the current queue has no next track.
                MiniPlayerIconButton(
                    onClick = if (state.hasNext) ({ nowPlayingManager.skipToNext(preferPreviewOnNext = nowPlayingManager.preferPreviewOnInAppSkip) }) else null,
                    contentDescription = stringResource(R.string.mini_player_cd_next),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (state.hasNext) CorusColors.Text else CorusColors.Tertiary,
                    )
                }
            }
            if (!embeddedInExpandingShell) {
                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            }
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
/** Invisible touch target — visual strip stays [knobSize] tall. Mirrors iOS. */
private val scrubTouchHeight = 32.dp
private val scrubDragMin = 6.dp
private val scrubLongPressHapticMs = 200L

/** Transport-control hit width — matches iOS MiniPlayerBar button frames. */
private val miniPlayerControlWidth = 36.dp
/** Service-logo hit width — matches iOS MiniPlayerBar link-out frames. */
private val miniPlayerServiceButtonWidth = 32.dp
/** Audiomack wave @ 22dp tall ≈ 30dp wide; keep a little horizontal slack. */
private val miniPlayerAudiomackButtonWidth = 40.dp

@Composable
private fun MiniPlayerIconButton(
    onClick: (() -> Unit)?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    width: Dp = miniPlayerControlWidth,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .width(width)
            .defaultMinSize(minHeight = CorusSpacing.touchTarget)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (onClick == null) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ScrubberOverlay(
    nowPlayingManager: NowPlayingManager,
    hasActiveTrack: Boolean,
    trackId: String?,
    modifier: Modifier = Modifier,
) {
    val time by ScrubberClock.time.collectAsState()
    val duration by ScrubberClock.duration.collectAsState()
    val snapCounter by ScrubberClock.snapCounter.collectAsState()
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

    // Smooth tween between 250ms polling ticks; immediate snap when the
    // target moves backward (track change / scrubber reset / cross-source
    // switch). Snap-on-decrease handles the case where ScrubberClock's
    // reset() emits (0, 0) but Compose coalesces and only ever observes
    // the transition from the outgoing fraction to the next track's first
    // polled fraction — never the intermediate 0 — so an equality check
    // against 0 wouldn't fire.
    val animatable = remember { Animatable(displayedFraction) }
    var lastObservedSnapCounter by remember { mutableIntStateOf(snapCounter) }
    LaunchedEffect(displayedFraction, isScrubbing, snapCounter) {
        when {
            isScrubbing ||
                displayedFraction < animatable.value ||
                snapCounter != lastObservedSnapCounter ->
                animatable.snapTo(displayedFraction)
            else ->
                animatable.animateTo(
                    targetValue = displayedFraction,
                    animationSpec = tween(durationMillis = 250, easing = LinearEasing),
                )
        }
    }
    val animatedFraction = animatable.value

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
        lastObservedSnapCounter = snapCounter
    }

    LaunchedEffect(snapCounter) {
        lastObservedSnapCounter = snapCounter
    }

    // Reset cleanup: ScrubberClock.reset() lands before trackId updates
    // (skipToNext resets synchronously; the new state flow only fires after
    // the loading coroutine sets _state). Without this, a user-seek pending
    // hold would survive into the gap and freeze displayedFraction at the
    // previous track's drag position until the new track started playing.
    LaunchedEffect(duration) {
        if (duration == 0L) {
            pendingFraction = null
            isScrubbing = false
        }
    }

    val haptics = LocalHapticManager.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemGestures = WindowInsets.systemGestures
    val backGestureInsetPx = maxOf(
        systemGestures.getLeft(density, layoutDirection),
        systemGestures.getRight(density, layoutDirection),
        with(density) { 32.dp.roundToPx() },
    )
    val scope = rememberCoroutineScope()
    val knobSizePx = with(density) { knobSize.toPx() }

    fun commitScrubRelease(scrubbing: Boolean, fraction: Float) {
        scope.launch {
            if (scrubbing && duration > 0L) {
                pendingFraction = fraction
                nowPlayingManager.seek((fraction * duration).toLong())
                // iOS shell scrub end — light.
                haptics.impact(HapticManager.ImpactStyle.LIGHT)
            }
            isScrubbing = false
        }
    }

    Box(
        modifier = modifier
            .height(scrubTouchHeight)
            // Center the touch target on the top edge of the mini player.
            .offset(y = -scrubTouchHeight / 2)
            // After offset so the exclusion matches the on-screen strip
            // (including the half that sits above the bar).
            .systemGestureExclusion()
            .alpha(if (canScrub && hasActiveTrack) 1f else 0f)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(canScrub, widthPx, duration, backGestureInsetPx) {
                val minDragPx = with(density) { scrubDragMin.toPx() }
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!canScrub) {
                            // Invisible strip still overlaps feed content above the
                            // bar — consume so taps don't open posts underneath.
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        // Starts in the system back-gesture zone (left/right
                        // edge): claim immediately so Android doesn't steal
                        // the drag. Exclusion rects above are the main fix;
                        // this covers the 6dp race before we normally consume.
                        val nearBackEdge = widthPx > 0 && (
                            down.position.x <= backGestureInsetPx ||
                                down.position.x >= widthPx - backGestureInsetPx
                            )
                        var pressHapticFired = false
                        val longPressJob = launch {
                            delay(scrubLongPressHapticMs)
                            // iOS shell scrub start — light.
                            haptics.impact(HapticManager.ImpactStyle.LIGHT)
                            scope.launch { isKnobVisible = true }
                            pressHapticFired = true
                        }
                        var scrubbing = false
                        var totalX = 0f
                        var totalY = 0f
                        if (nearBackEdge) {
                            longPressJob.cancel()
                            down.consume()
                            scrubbing = true
                            scrubFraction = (down.position.x / widthPx).coerceIn(0f, 1f)
                            scope.launch {
                                isScrubbing = true
                                isKnobVisible = true
                            }
                            haptics.impact(HapticManager.ImpactStyle.LIGHT)
                            pressHapticFired = false
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) {
                                    if (!scrubbing && abs(totalX) <= minDragPx && abs(totalY) <= minDragPx) {
                                        // Tap on the strip — block feed posts below.
                                        event.changes.forEach { it.consume() }
                                    }
                                    commitScrubRelease(scrubbing, scrubFraction)
                                    break
                                }
                                val change = event.changes.firstOrNull() ?: break
                                val delta = change.positionChange()
                                if (!scrubbing) {
                                    totalX += delta.x
                                    totalY += delta.y
                                    val dx = abs(totalX)
                                    val dy = abs(totalY)
                                    // More horizontal than vertical, and past a
                                    // small threshold — lets feed scrolls pass through.
                                    if (dx > minDragPx && dx > dy) {
                                        longPressJob.cancel()
                                        scrubbing = true
                                        scope.launch {
                                            isScrubbing = true
                                            isKnobVisible = true
                                        }
                                        if (!pressHapticFired) {
                                            haptics.impact(HapticManager.ImpactStyle.LIGHT)
                                        }
                                        pressHapticFired = false
                                    }
                                }
                                if (scrubbing && widthPx > 0) {
                                    scrubFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                                    change.consume()
                                }
                            }
                        } finally {
                            longPressJob.cancel()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(knobSize),
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
}

@Composable
internal fun MiniPlayerPlaybackModeToggle(
    playFullSongs: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewLabel = stringResource(R.string.mini_player_playback_mode_preview)
    val fullLabel = stringResource(R.string.mini_player_playback_mode_full)
    val cd = stringResource(R.string.mini_player_cd_playback_mode)
    var displayedFull by remember { mutableStateOf(playFullSongs) }

    LaunchedEffect(playFullSongs) {
        if (displayedFull != playFullSongs) {
            displayedFull = playFullSongs
        }
    }

    Row(
        modifier = modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(CorusColors.Text.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    displayedFull = !displayedFull
                    onSelect(displayedFull)
                },
            )
            .padding(2.dp)
            .semantics {
                contentDescription = cd
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaybackModeSegment(
            label = previewLabel,
            selected = !displayedFull,
            modifier = Modifier.weight(1f),
        )
        PlaybackModeSegment(
            label = fullLabel,
            selected = displayedFull,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlaybackModeSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val segmentBackground by animateColorAsState(
        targetValue = if (selected) CorusColors.Background else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "playbackModeSegmentBackground",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(segmentBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CorusFont.caption.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) CorusColors.Text else CorusColors.Text.copy(alpha = 0.55f),
            maxLines = 1,
        )
    }
}
