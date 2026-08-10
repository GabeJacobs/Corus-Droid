package fm.corus.android.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.MusicServiceLinkOut
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.domain.ScrubberClock
import fm.corus.android.domain.SongPlayRouting
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.components.AudiomackLogo
import fm.corus.android.ui.components.MiniPlayerPlaybackModeToggle
import fm.corus.android.ui.components.SoundCloudAdaptiveLogo
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlin.math.abs

@Composable
fun FullPlayerScreen(
    nowPlayingManager: NowPlayingManager,
    engagementManager: PostEngagementManager?,
    musicService: MusicService,
    playFullSongs: Boolean,
    alwaysPlayFullSongs: Boolean = false,
    onPlaybackModeChange: (Boolean) -> Unit,
    remoteConfig: RemoteConfigService?,
    interactive: Boolean,
    onDismiss: () -> Unit,
    onLikeTap: () -> Unit,
    onOpenInService: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenComments: (postId: String, replyToCommentId: String?) -> Unit = { _, _ -> },
    onOpenUser: (userId: String) -> Unit = {},
    onOpenPost: (postId: String) -> Unit = {},
    onRepost: (fm.corus.android.data.model.CymbalPost) -> Unit = {},
    onSharePost: (fm.corus.android.data.model.CymbalPost) -> Unit = {},
    onSavePost: (postId: String) -> Unit = {},
    onOpenSongDetail: (fm.corus.android.data.model.CymbalTrack) -> Unit = {},
    onOpenFilmDetail: (fm.corus.android.data.model.CymbalPost) -> Unit = {},
    onComposeTrack: () -> Unit = {},
    onMentionTap: (String) -> Unit = {},
    onHashtagTap: (String) -> Unit = {},
    onLikeLongPress: ((String) -> Unit)? = null,
    commentsRefreshSignal: Int = 0,
    modifier: Modifier = Modifier,
    fullPlayerViewModel: FullPlayerViewModel = hiltViewModel(),
) {
    val state by nowPlayingManager.state.collectAsState()
    val context = LocalContext.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scroll = rememberScrollState()
    val isExternalSpotifyListening by nowPlayingManager.isExternalSpotifyListeningFlow.collectAsState()
    LaunchedEffect(commentsRefreshSignal) {
        if (commentsRefreshSignal > 0) fullPlayerViewModel.refreshComments()
    }

    val showsPlaybackModeToggle = remoteConfig != null &&
        SongPlayRouting.showsMiniPlayerPlaybackModeToggle(
            context = context,
            source = state.source,
            service = musicService,
            remoteConfig = remoteConfig,
            isExternalSpotifyListening = isExternalSpotifyListening,
            alwaysPlayFullSongs = alwaysPlayFullSongs,
        )

    val artUrl = state.albumArtLargeURL ?: state.albumArtURL
    val title = state.trackName.ifBlank { "Unknown" }
    val artist = state.artistName.ifBlank { "Unknown" }
    val likeable = !state.sourcePostId.isNullOrBlank()
    val engagementStates = engagementManager?.states?.collectAsState()?.value ?: emptyMap()
    val isLiked = state.sourcePostId?.let { engagementStates[it]?.isLiked } ?: false
    val isPlaying = state.isPlaying
    val hasNext = state.hasNext

    BoxWithWidth(modifier = modifier.fillMaxSize()) { widthDp ->
        val artSide = minOf(maxOf(widthDp - 48.dp, 0.dp), 320.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll, enabled = interactive)
                .padding(top = statusTop + 4.dp),
        ) {
            FullPlayerTopChrome(
                showsPlaybackModeToggle = showsPlaybackModeToggle,
                playFullSongs = playFullSongs,
                onDismiss = onDismiss,
                onPlaybackModeSelect = onPlaybackModeChange,
                onOpenInService = onOpenInService,
                onOpenQueue = onOpenQueue,
                interactive = interactive,
            )

            Spacer(modifier = Modifier.height(8.dp))

            AsyncImage(
                model = artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(artSide)
                    .clip(RoundedCornerShape(10.dp)),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            ) {
                Text(
                    text = title,
                    style = CorusFont.songTitleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                    ),
                    color = CorusColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artist,
                    style = CorusFont.artistNameLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                    ),
                    color = CorusColors.Text.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            FullPlayerScrubber(
                nowPlayingManager = nowPlayingManager,
                interactive = interactive,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            FullPlayerTransport(
                isPlaying = isPlaying,
                hasNext = hasNext,
                isLiked = isLiked,
                likeable = likeable,
                trackSource = state.source,
                musicService = musicService,
                interactive = interactive,
                onPrevious = { nowPlayingManager.seek(0L) },
                onPlayPause = { nowPlayingManager.togglePlayPause() },
                onNext = {
                    nowPlayingManager.skipToNext(
                        preferPreviewOnNext = nowPlayingManager.preferPreviewOnInAppSkip,
                    )
                },
                onLike = onLikeTap,
                onOpenInService = onOpenInService,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            FullPlayerSocialSection(
                viewModel = fullPlayerViewModel,
                engagementManager = engagementManager,
                nowPlayingManager = nowPlayingManager,
                sourcePostId = state.sourcePostId,
                trackId = state.trackId,
                spotifyURI = state.spotifyURI,
                isrc = state.isrc,
                trackName = title,
                artistName = artist,
                interactive = interactive,
                saveCountEnabled = remoteConfig?.saveCountEnabled == true,
                onOpenPost = onOpenPost,
                onOpenUser = onOpenUser,
                onOpenComments = onOpenComments,
                onLikeTap = onLikeTap,
                onRepostTap = onRepost,
                onShareTap = onSharePost,
                onSaveTap = onSavePost,
                onOpenSongDetail = onOpenSongDetail,
                onOpenFilmDetail = onOpenFilmDetail,
                onComposeTrack = onComposeTrack,
                onMentionTap = onMentionTap,
                onHashtagTap = onHashtagTap,
                onLikeLongPress = onLikeLongPress,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun BoxWithWidth(
    modifier: Modifier = Modifier,
    content: @Composable (width: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var widthDp by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier.onSizeChanged {
            widthDp = with(density) { it.width.toDp() }
        },
    ) {
        if (widthDp > 0.dp) content(widthDp)
    }
}

@Composable
private fun FullPlayerTopChrome(
    showsPlaybackModeToggle: Boolean,
    playFullSongs: Boolean,
    onDismiss: () -> Unit,
    onPlaybackModeSelect: (Boolean) -> Unit,
    onOpenInService: () -> Unit,
    onOpenQueue: () -> Unit,
    interactive: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onDismiss,
            enabled = interactive,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Close player",
                tint = CorusColors.Text,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (showsPlaybackModeToggle) {
            MiniPlayerPlaybackModeToggle(
                playFullSongs = playFullSongs,
                onSelect = onPlaybackModeSelect,
                modifier = Modifier
                    .width(76.dp)
                    .height(32.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box {
            IconButton(
                onClick = { if (interactive) menuOpen = true },
                enabled = interactive,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = CorusColors.Text,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Open in music service") },
                    onClick = {
                        menuOpen = false
                        onOpenInService()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Queue") },
                    onClick = {
                        menuOpen = false
                        onOpenQueue()
                    },
                )
            }
        }
    }
}

@Composable
private fun FullPlayerScrubber(
    nowPlayingManager: NowPlayingManager,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val clockTimeMs by ScrubberClock.time.collectAsState()
    val clockDurationMs by ScrubberClock.duration.collectAsState()
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val duration = clockDurationMs.coerceAtLeast(0L)
    val canScrub = duration > 0L && interactive
    val playbackFraction = if (duration > 0L) {
        (clockTimeMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayed = if (isScrubbing) scrubFraction else playbackFraction

    val elapsedMs = if (isScrubbing) (displayed * duration).toLong() else clockTimeMs
    val remainingMs = (duration - elapsedMs).coerceAtLeast(0L)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(canScrub, trackWidthPx) {
                    if (!canScrub || trackWidthPx <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        horizontalDrag(down.id) { change ->
                            val dx = change.positionChange().x
                            val dy = change.positionChange().y
                            if (!dragging) {
                                if (abs(dx) <= abs(dy)) return@horizontalDrag
                                dragging = true
                                isScrubbing = true
                            }
                            change.consume()
                            scrubFraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                        }
                        if (dragging) {
                            nowPlayingManager.seek((scrubFraction * duration).toLong())
                            isScrubbing = false
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Text.copy(alpha = 0.2f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayed.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Text.copy(alpha = 0.95f)),
            )
            val knobOffset = with(density) {
                ((trackWidthPx * displayed) - 6f).coerceAtLeast(0f).toDp()
            }
            Box(
                modifier = Modifier
                    .padding(start = knobOffset)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Text.copy(alpha = if (canScrub) 1f else 0.35f)),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatPlayerTimeMs(elapsedMs),
                style = CorusFont.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = CorusColors.Text.copy(alpha = 0.55f),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "-${formatPlayerTimeMs(remainingMs)}",
                style = CorusFont.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = CorusColors.Text.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun FullPlayerTransport(
    isPlaying: Boolean,
    hasNext: Boolean,
    isLiked: Boolean,
    likeable: Boolean,
    trackSource: TrackSource,
    musicService: MusicService,
    interactive: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onOpenInService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (likeable) {
            IconButton(onClick = onLike, enabled = interactive, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) CorusColors.Like else CorusColors.Text,
                    modifier = Modifier.size(26.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = interactive, modifier = Modifier.size(56.dp)) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = CorusColors.Text,
                    modifier = Modifier.size(28.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CorusColors.Text.copy(alpha = 0.12f))
                    .clickable(
                        enabled = interactive,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pause",
                        tint = CorusColors.Text,
                        modifier = Modifier.size(32.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = CorusColors.Text,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            IconButton(
                onClick = onNext,
                enabled = interactive && hasNext,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = if (hasNext) CorusColors.Text else CorusColors.Text.copy(alpha = 0.3f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        IconButton(
            onClick = onOpenInService,
            enabled = interactive,
            modifier = Modifier.size(44.dp),
        ) {
            TransportServiceLogo(trackSource = trackSource, musicService = musicService)
        }
    }
}

@Composable
private fun TransportServiceLogo(
    trackSource: TrackSource,
    musicService: MusicService,
) {
    when (trackSource) {
        TrackSource.SOUNDCLOUD -> SoundCloudAdaptiveLogo(size = 26.dp)
        TrackSource.AUDIOMACK -> AudiomackLogo(height = 22.dp)
        TrackSource.TIDAL -> Image(
            painter = painterResource(MusicServiceLinkOut.logoRes(MusicService.TIDAL)),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
        TrackSource.DEEZER -> Image(
            painter = painterResource(MusicServiceLinkOut.logoRes(MusicService.DEEZER)),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
        TrackSource.APPLEMUSIC -> Image(
            painter = painterResource(MusicServiceLinkOut.logoRes(MusicService.APPLE_MUSIC)),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        else -> Image(
            painter = painterResource(MusicServiceLinkOut.logoRes(musicService)),
            contentDescription = null,
            modifier = Modifier.size(26.dp),
        )
    }
}

private fun formatPlayerTimeMs(ms: Long): String {
    val total = ((ms / 1000.0).toInt()).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
