package fm.corus.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import fm.corus.android.R
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
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

@Composable
fun MiniPlayerBar(
    nowPlayingManager: NowPlayingManager,
    onTrackTap: (() -> Unit)? = null,
    engagementManager: PostEngagementManager? = null,
    onLikeTap: (() -> Unit)? = null,
    musicService: fm.corus.android.data.model.MusicService = fm.corus.android.data.model.MusicService.SPOTIFY,
    playFullSongs: Boolean = false,
    onPlaybackModeChange: (Boolean) -> Unit = {},
    remoteConfig: fm.corus.android.service.RemoteConfigService? = null,
    resolveLinkOut: (suspend () -> String?)? = null,
    resolveSpotifyFromApple: (suspend () -> String?)? = null,
    modifier: Modifier = Modifier,
) {
    val state by nowPlayingManager.state.collectAsState()
    val isHydratingExternalSpotify by nowPlayingManager.isHydratingExternalSpotify.collectAsState()
    val isExternalSpotifyListening by nowPlayingManager.isExternalSpotifyListeningFlow.collectAsState()
    val showsMiniPlayer = state.hasActiveTrack && !isHydratingExternalSpotify &&
        (!isExternalSpotifyListening ||
            (state.trackName.isNotBlank() && state.trackName != "Unknown Track"))
    val absentFromSpotify by fm.corus.android.domain.MusicServiceLinkOut.absentFromSpotify.collectAsState()
    val engagementStates = engagementManager?.states?.collectAsState()?.value ?: emptyMap()
    val isCurrentTrackLiked = state.sourcePostId
        ?.let { engagementStates[it]?.isLiked }
        ?: false
    val context = LocalContext.current
    val linkOutScope = androidx.compose.runtime.rememberCoroutineScope()
    // Preview/full toggle when in-app full songs are available for the viewer's
    // service; otherwise the streaming-service link-out (SoundCloud, Audiomack,
    // TIDAL/Deezer exclusives, Apple Music on Android, etc.).
    val showsPlaybackModeToggle = remoteConfig != null &&
        SongPlayRouting.supportsInAppFullSong(
            context = context,
            source = state.source,
            service = musicService,
            remoteConfig = remoteConfig,
        )
    val playbackModeToggleShowsFull = playFullSongs

    AnimatedVisibility(
        visible = showsMiniPlayer,
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
                    // Scrubber's expanded touch strip straddles this row's top edge;
                    // win hit tests here so transport controls stay tappable.
                    .zIndex(2f)
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

                // Mode / service control sits left of transport so skip stays at the far edge.
                if (showsPlaybackModeToggle) {
                    MiniPlayerPlaybackModeToggle(
                        playFullSongs = playbackModeToggleShowsFull,
                        onSelect = onPlaybackModeChange,
                        modifier = Modifier.width(miniPlayerServiceButtonWidth + 40.dp),
                    )
                } else {
                // Spotify / SoundCloud / Apple Music button (matches the
                // source of the playing track). Apple-only and SoundCloud
                // tracks lock to their respective brands regardless of the
                // viewer's preferred service — they aren't in Spotify's
                // catalog so "Open in Spotify" would 404.
                val isSoundCloud = state.source == fm.corus.android.data.model.TrackSource.SOUNDCLOUD
                val isAudiomack = state.source == fm.corus.android.data.model.TrackSource.AUDIOMACK
                val isTidal = state.source == fm.corus.android.data.model.TrackSource.TIDAL
                val isDeezer = state.source == fm.corus.android.data.model.TrackSource.DEEZER
                val isAppleMusic = state.source == fm.corus.android.data.model.TrackSource.APPLEMUSIC
                fun resolveAndOpenLinkOut() {
                    val resolve = resolveLinkOut
                    if (resolve != null) {
                        linkOutScope.launch {
                            val url = resolve()
                            if (!url.isNullOrBlank()) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }
                        }
                    }
                }
                if (isSoundCloud) {
                    MiniPlayerIconButton(
                        onClick = {
                            val permalink = state.soundcloudPermalinkUrl
                            if (!permalink.isNullOrBlank()) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(permalink))) }
                            }
                        },
                        contentDescription = stringResource(R.string.mini_player_cd_open_spotify),
                        width = miniPlayerServiceButtonWidth,
                    ) {
                        SoundCloudAdaptiveLogo(size = 22.dp)
                    }
                } else if (isAudiomack) {
                    // Audiomack is source-locked (link-out only; not on Spotify/Apple)
                    // — always show its mark and open audiomack.com, regardless of the
                    // viewer's preferred service. Mirrors SoundCloud + iOS/web.
                    MiniPlayerIconButton(
                        onClick = {
                            val url = state.audiomackUrl
                            if (!url.isNullOrBlank()) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }
                        },
                        contentDescription = stringResource(R.string.mini_player_cd_open_spotify),
                        width = miniPlayerServiceButtonWidth,
                    ) {
                        AudiomackLogo(height = 22.dp)
                    }
                } else if (isTidal) {
                    MiniPlayerIconButton(
                        onClick = { resolveAndOpenLinkOut() },
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
                        onClick = { resolveAndOpenLinkOut() },
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
                    // Glyph reflects the service the tap opens. Under Apple-primary
                    // search an `applemusic`-sourced preview is usually ALSO on
                    // Spotify, so a Spotify viewer sees their own glyph and the tap
                    // resolves the Spotify target on demand. We fall back to the
                    // Apple glyph only once a tap CONFIRMED the track isn't on
                    // Spotify. TIDAL/Deezer viewers keep their own glyph. Mirrors iOS.
                    val knownNotOnSpotify = isAppleMusic && state.trackId in absentFromSpotify
                    val displayedService = if (isAppleMusic && musicService == fm.corus.android.data.model.MusicService.SPOTIFY && knownNotOnSpotify) {
                        fm.corus.android.data.model.MusicService.APPLE_MUSIC
                    } else {
                        musicService
                    }
                    MiniPlayerIconButton(
                        onClick = {
                            fun resolveAndOpen() = resolveAndOpenLinkOut()
                            fun openAppleSong() {
                                val tid = state.trackId
                                val amid = if (!tid.isNullOrBlank() && tid.startsWith("am:")) tid.removePrefix("am:") else null
                                if (!amid.isNullOrEmpty()) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.apple.com/us/song/$amid")))
                                    }
                                } else {
                                    resolveAndOpen()
                                }
                            }
                            when {
                                // Apple-SOURCED + Spotify viewer, not yet confirmed
                                // absent: resolve the Spotify target on tap (server
                                // ISRC-cache-first → usually zero Spotify calls). A
                                // confirmed miss marks it absent (glyph flips to
                                // Apple) and opens Apple instead of a dead link.
                                isAppleMusic && musicService == fm.corus.android.data.model.MusicService.SPOTIFY && !knownNotOnSpotify -> {
                                    val resolve = resolveSpotifyFromApple
                                    if (resolve != null) {
                                        linkOutScope.launch {
                                            val url = resolve()
                                            when {
                                                !url.isNullOrBlank() ->
                                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                                // CONFIRMED not on Spotify → Apple, which has it.
                                                fm.corus.android.domain.MusicServiceLinkOut.knownNotOnSpotify(state.trackId) ->
                                                    openAppleSong()
                                                // Transient / not-yet-deployed error: the tap promised
                                                // Spotify, so stay in Spotify (search), don't open Apple.
                                                else -> runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                                                        fm.corus.android.domain.MusicServiceLinkOut.spotifySearchUrl(state.trackName, state.artistName))))
                                                }
                                            }
                                        }
                                    } else {
                                        openAppleSong()
                                    }
                                }
                                // Confirmed Apple-only, or an Apple-Music viewer →
                                // the Apple Music song page.
                                isAppleMusic && (musicService == fm.corus.android.data.model.MusicService.SPOTIFY ||
                                    musicService == fm.corus.android.data.model.MusicService.APPLE_MUSIC) -> {
                                    openAppleSong()
                                }
                                musicService == fm.corus.android.data.model.MusicService.SPOTIFY -> {
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
                                }
                                else -> {
                                    // Apple Music / TIDAL / Deezer preference
                                    // (spotify-source post, or Apple-only + TIDAL/Deezer):
                                    // resolve via host (network, cached) then open.
                                    resolveAndOpen()
                                }
                            }
                        },
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

                // Play/Pause
                MiniPlayerIconButton(
                    onClick = { nowPlayingManager.togglePlayPause() },
                    contentDescription = if (state.isPlaying) {
                        stringResource(R.string.mini_player_cd_pause)
                    } else {
                        stringResource(R.string.mini_player_cd_play)
                    },
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = CorusColors.Text,
                    )
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
/** Invisible touch target — visual strip stays [knobSize] tall. Mirrors iOS. */
private val scrubTouchHeight = 32.dp
private val scrubDragMin = 6.dp
private val scrubLongPressHapticMs = 200L

/** Transport-control hit width — matches iOS MiniPlayerBar button frames. */
private val miniPlayerControlWidth = 36.dp
/** Service-logo hit width — matches iOS MiniPlayerBar link-out frames. */
private val miniPlayerServiceButtonWidth = 32.dp

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

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val knobSizePx = with(density) { knobSize.toPx() }

    fun commitScrubRelease(scrubbing: Boolean, fraction: Float) {
        scope.launch {
            if (scrubbing && duration > 0L) {
                pendingFraction = fraction
                nowPlayingManager.seek((fraction * duration).toLong())
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            isScrubbing = false
        }
    }

    Box(
        modifier = modifier
            .height(scrubTouchHeight)
            // Center the touch target on the top edge of the mini player.
            .offset(y = -scrubTouchHeight / 2)
            .alpha(if (canScrub && hasActiveTrack) 1f else 0f)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(canScrub, widthPx, duration) {
                val minDragPx = with(density) { scrubDragMin.toPx() }
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (!canScrub) {
                            // Invisible strip still overlaps feed content above the
                            // bar — consume so taps don't open posts underneath.
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        var pressHapticFired = false
                        val longPressJob = launch {
                            delay(scrubLongPressHapticMs)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { isKnobVisible = true }
                            pressHapticFired = true
                        }
                        var scrubbing = false
                        var totalX = 0f
                        var totalY = 0f
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
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
private fun MiniPlayerPlaybackModeToggle(
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
            .background(CorusColors.Divider.copy(alpha = 0.45f))
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
            color = if (selected) CorusColors.Text else CorusColors.Secondary,
            maxLines = 1,
        )
    }
}
