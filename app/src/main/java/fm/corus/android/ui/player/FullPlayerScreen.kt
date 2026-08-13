package fm.corus.android.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.corus.android.R
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.domain.HapticManager
import fm.corus.android.domain.MusicServiceLinkOut
import fm.corus.android.domain.NowPlayingManager
import fm.corus.android.domain.PostEngagementManager
import fm.corus.android.domain.PostPlaybackHighlight
import fm.corus.android.domain.ScrubberClock
import fm.corus.android.domain.SongPlayRouting
import fm.corus.android.service.RemoteConfigService
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.components.AudiomackLogo
import fm.corus.android.ui.components.DoubleTapLikeHeartIcon
import fm.corus.android.ui.components.MiniPlayerPlaybackModeToggle
import fm.corus.android.ui.components.SoundCloudAdaptiveLogo
import fm.corus.android.ui.components.playDoubleTapLikeHeartAnimation
import fm.corus.android.ui.components.resolveMenuGoToAlbumTap
import fm.corus.android.ui.components.resolveMenuGoToArtistTap
import fm.corus.android.ui.navigation.AlbumPageRoute
import fm.corus.android.ui.navigation.ArtistPageRoute
import fm.corus.android.ui.navigation.rememberArtistPagesEnabled
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
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
    onContentAtTopChange: (Boolean) -> Unit = {},
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
    /** Comment song/film attachments (inline full-player comments). */
    onOpenFilmMovie: (fm.corus.android.data.model.CymbalMovie) -> Unit = {},
    onOpenArtist: ((ArtistPageRoute) -> Unit)? = null,
    onOpenAlbum: ((AlbumPageRoute) -> Unit)? = null,
    onOpenDirector: ((fm.corus.android.ui.navigation.DirectorPageRoute) -> Unit)? = null,
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
    val scope = rememberCoroutineScope()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val buttonNav = rememberIsButtonStyleNavigation(navBottom)
    val scroll = rememberScrollState()
    val isExternalSpotifyListening by nowPlayingManager.isExternalSpotifyListeningFlow.collectAsState()
    val artistPagesEnabled = rememberArtistPagesEnabled()
    var isResolvingDestination by remember { mutableStateOf(false) }
    var destinationMissMessage by remember { mutableStateOf<String?>(null) }
    // Drive sheet pull-down claim (ExpandingPlayerHost nested scroll).
    val contentAtTop = !scroll.canScrollBackward
    LaunchedEffect(contentAtTop) {
        onContentAtTopChange(contentAtTop)
    }
    LaunchedEffect(commentsRefreshSignal) {
        if (commentsRefreshSignal > 0) fullPlayerViewModel.refreshComments()
    }
    LaunchedEffect(destinationMissMessage) {
        if (destinationMissMessage != null) {
            kotlinx.coroutines.delay(2_200)
            destinationMissMessage = null
        }
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

    val sourcePost by fullPlayerViewModel.sourcePost.collectAsState()
    val menuPost = remember(sourcePost, state) { fullPlayerMenuPost(sourcePost, state) }
    val menuSource = remember(sourcePost, state) { fullPlayerMenuTrackSource(sourcePost, state) }
    val openInLabel = fullPlayerOpenInServiceLabelKey(state.source, musicService)
    val openInServiceTitle = when (openInLabel) {
        FullPlayerOpenInLabel.OpenSoundCloud -> stringResource(R.string.post_menu_open_soundcloud)
        FullPlayerOpenInLabel.OpenAudiomack -> stringResource(R.string.post_menu_open_audiomack)
        FullPlayerOpenInLabel.OpenTidal -> stringResource(R.string.post_menu_open_tidal)
        FullPlayerOpenInLabel.OpenDeezer -> stringResource(R.string.post_menu_open_deezer)
        is FullPlayerOpenInLabel.PlayIn ->
            stringResource(R.string.post_menu_play_in_service, openInLabel.serviceLabel)
    }
    val artistNotFoundMsg = stringResource(R.string.song_detail_artist_not_found)
    val albumNotFoundMsg = stringResource(R.string.song_detail_album_not_found)
    val onGoToArtist = menuPost?.let { post ->
        resolveMenuGoToArtistTap(
            context = context,
            post = post,
            artistPagesEnabled = artistPagesEnabled,
            onNavigateToArtist = onOpenArtist,
            scope = scope,
            resolveArtistId = fullPlayerViewModel::resolveArtistIdForTrack,
            onArtistNotFound = { destinationMissMessage = artistNotFoundMsg },
            onResolvingChange = { isResolvingDestination = it },
        )
    }
    val onGoToAlbum = menuPost?.let { post ->
        resolveMenuGoToAlbumTap(
            context = context,
            post = post,
            artistPagesEnabled = artistPagesEnabled,
            onNavigateToAlbum = onOpenAlbum,
            onNavigateToSong = { onOpenSongDetail(post.track) },
            prereleaseAlbumPagesEnabled = remoteConfig?.prereleaseAlbumPagesEnabled == true,
            scope = scope,
            resolveDestinations = fullPlayerViewModel::resolveTrackDestinationsForTrack,
            onAlbumNotFound = { destinationMissMessage = albumNotFoundMsg },
            onResolvingChange = { isResolvingDestination = it },
        )
    }
    // Prefer large art from now-playing, then the loaded source post (covers
    // older feed plays that only queued the thumbnail URL).
    val artUrl = state.albumArtLargeURL
        ?: sourcePost?.track?.albumArtLargeURL
        ?: state.albumArtURL
    val title = state.trackName.ifBlank { "Unknown" }
    val artist = state.artistName.ifBlank { "Unknown" }
    // Same gate as the overflow "Go to Artist" row — tappable artist line mirrors iOS.
    val showsArtistRow = fullPlayerShowsArtistRow(menuSource, artistPagesEnabled)
    val likeable = !state.sourcePostId.isNullOrBlank()
    val isLoadingSourcePost by fullPlayerViewModel.isLoadingSourcePost.collectAsState()
    // iOS `isCatalogPlayback && composeTrackCandidate != null` — external Spotify /
    // search / album plays with no source post get the blue + compose control.
    val showsComposeButton = state.sourcePostId.isNullOrBlank() &&
        !isLoadingSourcePost &&
        !state.trackId.isNullOrBlank()
    val engagementStates = engagementManager?.states?.collectAsState()?.value ?: emptyMap()
    val isLiked = state.sourcePostId?.let { engagementStates[it]?.isLiked } ?: false
    val isPlaying = state.isPlaying
    val hasNext = state.hasNext
    val stagedFeedSkipLoading by nowPlayingManager.stagedFeedSkipLoading.collectAsState()
    val loadingTrackId by nowPlayingManager.loadingTrackId.collectAsState()
    val isResolvingSpotify by nowPlayingManager.isResolvingSpotifyFlow.collectAsState()
    val showsTransportSpinner = PostPlaybackHighlight.showsTransportSpinner(
        stagedFeedSkipLoading = stagedFeedSkipLoading,
        isPlaying = isPlaying,
        isPreviewMode = nowPlayingManager.isPreviewMode,
        loadingTrackId = loadingTrackId,
        currentTrackId = state.trackId,
        isResolvingFullSong = isResolvingSpotify,
    )
    val haptics = LocalHapticManager.current
    val artHeartScale = remember { Animatable(0f) }
    val artHeartAlpha = remember { Animatable(0f) }
    var showArtDoubleTapHeart by remember { mutableStateOf(false) }

    BoxWithWidth(modifier = modifier.fillMaxSize()) { widthDp ->
        val artSide = minOf(maxOf(widthDp - 48.dp, 0.dp), 320.dp)
        val artPx = with(LocalDensity.current) { artSide.roundToPx().coerceAtLeast(1) }

        // No overscroll stretch/glow — first pull-down pixels belong to the sheet.
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll, enabled = interactive)
                    .padding(top = statusTop + 4.dp),
            ) {
            FullPlayerTopChrome(
                openInServiceTitle = openInServiceTitle,
                showsArtistRow = showsArtistRow,
                showsAlbumRow = fullPlayerShowsAlbumRow(menuSource, artistPagesEnabled),
                showsShareRow = fullPlayerShowsShareRow(sourcePost),
                showsPlaybackModeToggle = showsPlaybackModeToggle,
                playFullSongs = playFullSongs,
                onDismiss = onDismiss,
                onPlaybackModeSelect = onPlaybackModeChange,
                onOpenInService = onOpenInService,
                onGoToArtist = { onGoToArtist?.invoke() },
                onGoToAlbum = { onGoToAlbum?.invoke() },
                onSharePost = { sourcePost?.let(onSharePost) },
                onOpenQueue = onOpenQueue,
                interactive = interactive,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val upcoming = remember(state.trackId, state.sourcePostId, state.hasNext) {
                val q = nowPlayingManager.queueSnapshot()
                val idx = nowPlayingManager.currentQueueIndexSnapshot() ?: return@remember null
                q.getOrNull(idx + 1)
            }
            // Double-tap art to like — same heart burst as feed PostCard / iOS full player.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(likeable, isLiked, interactive, state.sourcePostId) {
                        if (!interactive || !likeable) return@pointerInput
                        detectTapGestures(
                            onDoubleTap = {
                                haptics.impact(HapticManager.ImpactStyle.MEDIUM)
                                if (!isLiked) onLikeTap()
                                showArtDoubleTapHeart = true
                                scope.launch {
                                    playDoubleTapLikeHeartAnimation(artHeartScale, artHeartAlpha)
                                    showArtDoubleTapHeart = false
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                FullPlayerAlbumArt(
                    trackId = state.trackId,
                    url = artUrl,
                    upcomingTrackId = upcoming?.trackId,
                    upcomingUrl = upcoming?.albumArtLargeURL ?: upcoming?.albumArtURL,
                    side = artSide,
                    artPx = artPx,
                    slideForward = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showArtDoubleTapHeart) {
                    DoubleTapLikeHeartIcon(
                        scale = artHeartScale.value,
                        alpha = artHeartAlpha.value,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = CorusFont.songTitleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 28.sp,
                    ),
                    color = CorusColors.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Artist line → same path as menu "Go to Artist" (collapse + navigate).
                val artistTapModifier = if (showsArtistRow && onGoToArtist != null) {
                    Modifier.clickable {
                        haptics.impact(HapticManager.ImpactStyle.LIGHT)
                        onGoToArtist.invoke()
                    }
                } else {
                    Modifier
                }
                Text(
                    text = artist,
                    style = CorusFont.artistNameLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                    ),
                    color = CorusColors.Text.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().then(artistTapModifier),
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
                isLoading = showsTransportSpinner,
                hasNext = hasNext,
                isLiked = isLiked,
                likeable = likeable,
                showsComposeButton = showsComposeButton,
                trackSource = state.source,
                musicService = musicService,
                interactive = interactive,
                onPrevious = { nowPlayingManager.skipToPreviousOrRestart() },
                onPlayPause = {
                    if (showsTransportSpinner) {
                        nowPlayingManager.cancelLoading()
                    } else {
                        nowPlayingManager.togglePlayPause()
                    }
                },
                onNext = {
                    nowPlayingManager.skipToNext(
                        preferPreviewOnNext = nowPlayingManager.preferPreviewOnInAppSkip,
                        immediate = true,
                    )
                },
                onLike = onLikeTap,
                onCompose = onComposeTrack,
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
                // Inline comment attachments used these defaults (no-ops) before —
                // wire through the same collapse+navigate destinations as iOS.
                onNavigateToSong = onOpenSongDetail,
                onNavigateToFilm = onOpenFilmMovie,
                onNavigateToArtist = onOpenArtist?.let { open ->
                    { artist ->
                        open(
                            ArtistPageRoute(
                                artistId = artist.artistId,
                                name = artist.artistName,
                                imageUrl = artist.artistImageURL,
                            ),
                        )
                    }
                },
                onNavigateToAlbum = onOpenAlbum?.let { open ->
                    { album ->
                        open(
                            AlbumPageRoute(
                                albumId = album.albumId,
                                title = album.albumTitle,
                                artist = album.albumArtistName,
                                coverUrl = album.albumCoverURL,
                                year = album.albumYear?.toIntOrNull(),
                            ),
                        )
                    }
                },
                onNavigateToDirector = onOpenDirector?.let { open ->
                    { director ->
                        open(
                            fm.corus.android.ui.navigation.DirectorPageRoute(
                                directorId = director.directorId,
                                name = director.directorName,
                                imageUrl = director.directorImageURL,
                            ),
                        )
                    }
                },
                onComposeTrack = onComposeTrack,
                onMentionTap = onMentionTap,
                onHashtagTap = onHashtagTap,
                onLikeLongPress = onLikeLongPress,
                modifier = Modifier.fillMaxWidth(),
            )

            // Clear the 3-button nav scrim so the last comments aren't trapped under it.
            Spacer(
                modifier = Modifier.height(
                    48.dp + if (buttonNav) navBottom else 0.dp,
                ),
            )
        }
        }

        val bannerText = when {
            isResolvingDestination -> stringResource(R.string.full_player_resolving_destination)
            else -> destinationMissMessage
        }
        if (bannerText != null) {
            Text(
                text = bannerText,
                style = CorusFont.caption.copy(fontWeight = FontWeight.Medium),
                color = CorusColors.Text,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusTop + 56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CorusColors.Text.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun BoxWithWidth(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(width: Dp) -> Unit,
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
    openInServiceTitle: String,
    showsArtistRow: Boolean,
    showsAlbumRow: Boolean,
    showsShareRow: Boolean,
    showsPlaybackModeToggle: Boolean,
    playFullSongs: Boolean,
    onDismiss: () -> Unit,
    onPlaybackModeSelect: (Boolean) -> Unit,
    onOpenInService: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onSharePost: () -> Unit,
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
        val haptics = LocalHapticManager.current
        IconButton(
            onClick = {
                haptics.impact(HapticManager.ImpactStyle.LIGHT)
                onDismiss()
            },
            enabled = interactive,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.full_player_cd_close),
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
                    contentDescription = stringResource(R.string.full_player_cd_more),
                    tint = CorusColors.Text,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(openInServiceTitle) },
                    leadingIcon = {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onOpenInService()
                    },
                )
                if (showsArtistRow) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.post_menu_go_to_artist)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onGoToArtist()
                        },
                    )
                }
                if (showsAlbumRow) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.post_menu_go_to_album)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Album, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onGoToAlbum()
                        },
                    )
                }
                if (showsShareRow) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.post_menu_share)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onSharePost()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.full_player_queue_title)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    },
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
    val state by nowPlayingManager.state.collectAsState()
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val duration = clockDurationMs.coerceAtLeast(0L)
    val durationState = rememberUpdatedState(duration)
    val canScrub = duration > 0L && interactive
    val playbackFraction = if (duration > 0L) {
        (clockTimeMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayed = when {
        isScrubbing -> scrubFraction
        pendingSeekFraction != null -> pendingSeekFraction!!
        else -> playbackFraction
    }

    val holdingSeekLabel = isScrubbing || pendingSeekFraction != null
    val elapsedMs = if (holdingSeekLabel) (displayed * duration).toLong() else clockTimeMs
    val remainingMs = (duration - elapsedMs).coerceAtLeast(0L)

    // Hold the knob/label on the scrub target until live time catches up (iOS
    // pendingSeekFraction) — otherwise a stale Spotify position flash wins.
    LaunchedEffect(clockTimeMs, pendingSeekFraction, duration) {
        val pending = pendingSeekFraction ?: return@LaunchedEffect
        if (duration > 0L && abs(playbackFraction - pending) < 0.02f) {
            pendingSeekFraction = null
        }
    }
    LaunchedEffect(pendingSeekFraction) {
        if (pendingSeekFraction == null) return@LaunchedEffect
        delay(2_000L)
        pendingSeekFraction = null
    }
    LaunchedEffect(state.trackId) {
        pendingSeekFraction = null
        isScrubbing = false
    }

    val haptics = LocalHapticManager.current
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
                                haptics.impact(HapticManager.ImpactStyle.LIGHT)
                            }
                            change.consume()
                            scrubFraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                        }
                        if (dragging) {
                            val seekDuration = durationState.value
                            pendingSeekFraction = scrubFraction
                            if (seekDuration > 0L) {
                                nowPlayingManager.seek((scrubFraction * seekDuration).toLong())
                            }
                            haptics.impact(HapticManager.ImpactStyle.LIGHT)
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
            // Keep the full knob inside the track (flush at 0% / 100%). The old
            // `width * f - 6px` used raw pixels for a 12.dp thumb and overshot
            // the line at the end.
            val knobSize = 12.dp
            val knobOffset = with(density) {
                scrubberKnobStartPx(
                    trackWidthPx = trackWidthPx,
                    fraction = displayed,
                    knobSizePx = knobSize.toPx(),
                ).toDp()
            }
            Box(
                modifier = Modifier
                    .padding(start = knobOffset)
                    .size(knobSize)
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
    isLoading: Boolean,
    hasNext: Boolean,
    isLiked: Boolean,
    likeable: Boolean,
    showsComposeButton: Boolean,
    trackSource: TrackSource,
    musicService: MusicService,
    interactive: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onCompose: () -> Unit,
    onOpenInService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // iOS FullPlayerTransport: light impact on transport press / compose.
    val haptics = LocalHapticManager.current
    fun lightTap(action: () -> Unit) {
        haptics.impact(HapticManager.ImpactStyle.LIGHT)
        action()
    }
    val cancelLoadingLabel = stringResource(R.string.mini_player_cd_cancel_loading)
    val pauseLabel = stringResource(R.string.full_player_cd_pause)
    val playLabel = stringResource(R.string.full_player_cd_play)
    val previousLabel = stringResource(R.string.full_player_cd_previous)
    val nextLabel = stringResource(R.string.full_player_cd_next)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            likeable -> {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            enabled = interactive,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onLike,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) CorusColors.Like else CorusColors.Text,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            showsComposeButton -> {
                // Same glyph as the tab-bar ComposeButton (Icons.Rounded.Add) —
                // scaled for the 32dp disc (tab uses 25dp in a 40dp circle).
                val composeCd = stringResource(R.string.full_player_post)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            enabled = interactive,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { lightTap(onCompose) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CorusColors.Accent)
                            .semantics { contentDescription = composeCd },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            else -> Spacer(modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same 56.dp target as iOS — IconButton would clamp to 40.dp.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(
                        enabled = interactive,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { lightTap(onPrevious) },
                    )
                    .semantics { contentDescription = previousLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = null,
                    tint = CorusColors.Text,
                    modifier = Modifier.size(40.dp),
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
                        onClick = { lightTap(onPlayPause) },
                    )
                    .semantics {
                        contentDescription = when {
                            isLoading -> cancelLoadingLabel
                            isPlaying -> pauseLabel
                            else -> playLabel
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> CircularProgressIndicator(
                        color = CorusColors.Text,
                        trackColor = CorusColors.Text.copy(alpha = 0.22f),
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                    )
                    isPlaying -> Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = null,
                        tint = CorusColors.Text,
                        modifier = Modifier.size(32.dp),
                    )
                    else -> Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = CorusColors.Text,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(
                        enabled = interactive && hasNext,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { lightTap(onNext) },
                    )
                    .semantics { contentDescription = nextLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = null,
                    tint = if (hasNext) CorusColors.Text else CorusColors.Text.copy(alpha = 0.3f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // iOS: Spacer(minLength: 24) sharing leftover width with the left spacer
        // so play stays centered. A fixed 24.dp here packed the heart against skip.
        Spacer(modifier = Modifier.weight(1f).widthIn(min = 24.dp))

        // Wide marks (Audiomack) need a non-square hit target — IconButton's
        // fixed square clips the wave. Mirror mini-player: Box + clickable.
        Box(
            modifier = Modifier
                .height(44.dp)
                .widthIn(min = 36.dp)
                .clickable(
                    enabled = interactive,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onOpenInService,
                ),
            contentAlignment = Alignment.Center,
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
        TrackSource.AUDIOMACK -> AudiomackLogo(height = 26.dp)
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

/** Leading edge of the scrubber knob so the thumb stays fully on the track. */
internal fun scrubberKnobStartPx(
    trackWidthPx: Float,
    fraction: Float,
    knobSizePx: Float,
): Float {
    val travel = (trackWidthPx - knobSizePx).coerceAtLeast(0f)
    return travel * fraction.coerceIn(0f, 1f)
}
