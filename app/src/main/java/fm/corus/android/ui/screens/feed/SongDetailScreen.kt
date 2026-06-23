package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fm.corus.android.R
import fm.corus.android.data.model.CymbalPost
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicService
import fm.corus.android.data.model.TrackSource
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.FirstPosterBadge
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.util.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    trackId: String,
    albumArtURL: String? = null,
    albumArtLargeURL: String? = null,
    songName: String? = null,
    artistName: String? = null,
    spotifyURI: String? = null,
    spotifyWebURL: String? = null,
    previewUrl: String? = null,
    source: String? = null,
    soundcloudId: String? = null,
    soundcloudPermalinkUrl: String? = null,
    viewModel: SongDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToCompose: (CymbalTrack) -> Unit = {},
) {
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val nowPlayingState by viewModel.nowPlayingState.collectAsState()
    val previewLoadingTrackId by viewModel.previewLoadingTrackId.collectAsState()
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Use route metadata immediately, upgrade to post data when available
    val songInfo = posts.firstOrNull()
    val displayName = songInfo?.displayTitle?.takeIf { it.isNotBlank() } ?: songName
    val displayArtist = songInfo?.displaySubtitle?.takeIf { it.isNotBlank() } ?: artistName
    val artUrl = songInfo?.track?.albumArtLargeURL ?: songInfo?.track?.albumArtURL ?: albumArtLargeURL ?: albumArtURL
    val effectiveSpotifyURI = songInfo?.track?.spotifyURI ?: spotifyURI ?: ""
    val effectiveSpotifyWebURL = songInfo?.track?.spotifyWebURL ?: spotifyWebURL ?: ""
    val effectivePreviewUrl = songInfo?.track?.previewUrl ?: previewUrl
    val effectiveIsrc = songInfo?.track?.isrc
    val effectiveSource = songInfo?.track?.source ?: TrackSource.fromRaw(source)
    val effectiveSoundcloudId = songInfo?.track?.soundcloudId ?: soundcloudId
    val effectiveSoundcloudPermalinkUrl = songInfo?.track?.soundcloudPermalinkUrl ?: soundcloudPermalinkUrl
    val isSoundCloud = effectiveSource == TrackSource.SOUNDCLOUD
    val isAppleMusic = effectiveSource == TrackSource.APPLEMUSIC
    // Apple Music URL is derived from the appleMusicId on the resolved
    // track (preferred) or the `am:` prefix on the trackId (fallback).
    val effectiveAppleMusicURL = songInfo?.track?.appleMusicURL
        ?: trackId.takeIf { it.startsWith("am:") }?.removePrefix("am:")?.takeIf { it.isNotEmpty() }
            ?.let { "https://music.apple.com/us/song/$it" }

    // Stable track model for link-out resolution + composing. Prefer the loaded
    // post's track (carries isrc / appleMusicURL); fall back to route metadata.
    val resolvedTrack = songInfo?.track ?: CymbalTrack(
        id = trackId,
        name = displayName ?: "",
        artistName = displayArtist ?: "",
        albumName = "",
        albumArtURL = artUrl,
        albumArtLargeURL = albumArtLargeURL ?: artUrl,
        spotifyURI = effectiveSpotifyURI,
        spotifyWebURL = effectiveSpotifyWebURL,
        previewUrl = effectivePreviewUrl,
        isrc = effectiveIsrc,
        source = effectiveSource,
        soundcloudId = effectiveSoundcloudId,
        soundcloudPermalinkUrl = effectiveSoundcloudPermalinkUrl,
    )

    // Default: keep the viewer's preference. Flip to the source on a confirmed
    // empty appleMusicId ("") OR when the Apple id is in a storefront the viewer
    // can't open (foreign-catalog-only). null = unknown -> no flip. Mirrors iOS.
    val hasAppleMusicEquivalent = when {
        isAppleMusic -> true
        resolvedTrack.appleMusicId == "" -> false
        resolvedTrack.appleMusicId == null -> true
        else -> resolvedTrack.appleMusicReachable(
            from = fm.corus.android.domain.MusicServiceLinkOut.deviceStorefront(),
        )
    }
    // Service the Spotify-source link-out routes to and badges as. A genuinely
    // Spotify-only track can't open on Apple Music, so an Apple Music viewer is
    // sent to Spotify rather than a dead Apple Music search. Mirrors iOS.
    val linkOutService = if (musicService == MusicService.APPLE_MUSIC && !hasAppleMusicEquivalent) {
        MusicService.SPOTIFY
    } else {
        musicService
    }
    // Secondary "also on …" button. Hidden when it would 404 (a Spotify-only
    // track has no Apple Music page) or duplicate the primary.
    val alternateLinkService: MusicService? = run {
        val alternate = if (musicService == MusicService.SPOTIFY) MusicService.APPLE_MUSIC else MusicService.SPOTIFY
        when {
            alternate == linkOutService -> null
            alternate == MusicService.APPLE_MUSIC && !hasAppleMusicEquivalent -> null
            else -> alternate
        }
    }

    // Open a track in the given service, mirroring iOS SongDetailView. Spotify
    // opens the post's own URI synchronously; Apple Music / TIDAL / Deezer
    // resolve the catalog URL via backend (cached; the global MainTabScreen
    // overlay shows the spinner) then open it.
    val openInService: (MusicService) -> Unit = { service ->
        viewModel.analyticsService.logMusicServiceLinkTapped(service, trackId)
        if (service == MusicService.SPOTIFY) {
            val uri = effectiveSpotifyURI.ifBlank { effectiveSpotifyWebURL }
            if (uri.isNotBlank()) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) } catch (_: Exception) { }
            }
        } else {
            scope.launch {
                val url = viewModel.resolveLinkUrl(resolvedTrack, service)
                if (!url.isNullOrBlank()) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            }
        }
    }

    val isPlayingThisTrack = nowPlayingState.trackId == trackId && nowPlayingState.isPlaying
    val isLoadingThisTrack = previewLoadingTrackId == trackId

    LaunchedEffect(trackId) {
        viewModel.loadSongPosts(
            trackId = trackId,
            spotifyURI = spotifyURI,
            trackName = songName,
            artistName = artistName,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    CorusHeaderIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.feed_cd_back),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Song header — always shown using route metadata
            item {
                Spacer(modifier = Modifier.height(CorusSpacing.xl))

                if (artUrl != null) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .shadow(4.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.togglePreview(
                                    trackId = trackId,
                                    trackName = displayName ?: "",
                                    artistName = displayArtist ?: "",
                                    albumArtURL = artUrl,
                                    albumArtLargeURL = songInfo?.track?.albumArtLargeURL ?: albumArtLargeURL,
                                    previewUrl = effectivePreviewUrl,
                                    spotifyURI = effectiveSpotifyURI.ifBlank { null },
                                    spotifyWebURL = effectiveSpotifyWebURL.ifBlank { null },
                                    isrc = effectiveIsrc,
                                    source = effectiveSource,
                                    soundcloudId = effectiveSoundcloudId,
                                    soundcloudPermalinkUrl = effectiveSoundcloudPermalinkUrl,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )

                        // A single circular badge in every state (play / pause /
                        // loading) so the affordance reads consistently and never
                        // covers the whole cover. Mirrors the search-row preview
                        // badge (SongPreviewArtwork), scaled up for the large art.
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                isLoadingThisTrack -> CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp),
                                )
                                isPlayingThisTrack -> Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = stringResource(R.string.song_detail_cd_pause_preview),
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                                else -> Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.song_detail_cd_play_preview),
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }

                // Song title + artist — show immediately from route data
                if (displayName != null) {
                    Text(
                        text = displayName,
                        style = CorusFont.songTitleLarge,
                        color = CorusColors.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg),
                    )
                }
                if (displayArtist != null) {
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    Text(
                        text = displayArtist,
                        style = CorusFont.artistNameLarge,
                        color = CorusColors.Secondary,
                    )
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                // Capsule buttons row — Post Song + preferred-service CTA (matching iOS order)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                ) {
                    // Post Song capsule
                    Button(
                        onClick = {
                            viewModel.analyticsService.logPostThisSongTapped(trackId)
                            onNavigateToCompose(resolvedTrack)
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CorusColors.Accent,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    ) {
                        Text(stringResource(R.string.song_detail_post_song), style = CorusFont.buttonSmall)
                    }

                    if (isSoundCloud) {
                        // Listen on SoundCloud capsule (matches iOS: white logo on black)
                        Button(
                            onClick = {
                                val permalink = effectiveSoundcloudPermalinkUrl
                                if (!permalink.isNullOrBlank()) {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(permalink))) }
                                }
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.soundcloud_white),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(CorusSpacing.sm))
                            Text(stringResource(R.string.song_detail_listen_soundcloud), style = CorusFont.buttonSmall)
                        }
                    } else if (isAppleMusic) {
                        // Apple-only tracks (e.g. Joanna Newsom) aren't in Spotify's
                        // catalog. If the viewer prefers a service that shares Apple's
                        // catalog (TIDAL / Deezer) route there; otherwise — including
                        // Spotify viewers — fall back to Apple Music, which is
                        // guaranteed to have it. Never offer Spotify (it would 404).
                        val opened = if (musicService == MusicService.TIDAL || musicService == MusicService.DEEZER) {
                            musicService
                        } else {
                            MusicService.APPLE_MUSIC
                        }
                        // Spotify viewers can't open this in Spotify, so the label
                        // shows the service we actually send them to.
                        val displayed = if (musicService == MusicService.SPOTIFY) MusicService.APPLE_MUSIC else musicService
                        Button(
                            onClick = {
                                if (opened == MusicService.APPLE_MUSIC && !effectiveAppleMusicURL.isNullOrBlank()) {
                                    // Open Apple Music directly via the resolved URL.
                                    viewModel.analyticsService.logMusicServiceLinkTapped(MusicService.APPLE_MUSIC, trackId)
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effectiveAppleMusicURL))) }
                                } else {
                                    openInService(opened)
                                }
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = serviceColor(displayed),
                                contentColor = serviceTextColor(displayed),
                            ),
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        ) {
                            Text(
                                stringResource(R.string.song_detail_open_in_service, displayed.displayLabel),
                                style = CorusFont.buttonSmall,
                            )
                        }
                    } else {
                        // Preferred-service CTA: Spotify → "Play in Spotify" (green),
                        // every other service → "Open in <service>" in its brand
                        // color. Uses linkOutService so a Spotify-only track shows
                        // "Play in Spotify" to an Apple Music viewer instead of a
                        // dead "Open in Apple Music".
                        Button(
                            onClick = { openInService(linkOutService) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = serviceColor(linkOutService),
                                contentColor = serviceTextColor(linkOutService),
                            ),
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        ) {
                            Text(
                                if (linkOutService == MusicService.SPOTIFY) {
                                    stringResource(R.string.song_detail_play_in_service, linkOutService.displayLabel)
                                } else {
                                    stringResource(R.string.song_detail_open_in_service, linkOutService.displayLabel)
                                },
                                style = CorusFont.buttonSmall,
                            )
                        }
                    }
                }

                // Alternate-service button — only when the track exists on multiple
                // services. SoundCloud has no equivalent; Apple-only tracks aren't on
                // Spotify so the "Spotify is the alternate" assumption doesn't hold.
                // alternateLinkService also hides the button when the alternate would
                // 404 (a Spotify-only track has no Apple Music page) or duplicate the
                // primary (an Apple Music viewer on a Spotify-only track).
                if (!isSoundCloud && !isAppleMusic && alternateLinkService != null) {
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                    val altService = alternateLinkService
                    OutlinedButton(
                        onClick = { openInService(altService) },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, serviceColor(altService)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = serviceColor(altService)),
                        contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    ) {
                        Text(
                            if (altService == MusicService.SPOTIFY) {
                                stringResource(R.string.song_detail_play_in_service, altService.displayLabel)
                            } else {
                                stringResource(R.string.song_detail_open_in_service, altService.displayLabel)
                            },
                            style = CorusFont.buttonSmall,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CorusSpacing.md))

                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            }

            // Posted by section
            if (isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.song_detail_posted_by),
                        style = CorusFont.sectionHeader,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .padding(top = CorusSpacing.lg, bottom = CorusSpacing.md),
                    )
                }
                items(6) { index ->
                    SkeletonUserRow()
                    if (index < 5) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }
                }
            } else if (loadError != null) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.song_detail_load_error), style = CorusFont.bodyMedium, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        TextButton(onClick = { viewModel.loadSongPosts(trackId) }) {
                            Text(stringResource(R.string.song_detail_try_again), style = CorusFont.buttonSmall, color = CorusColors.Accent)
                        }
                    }
                }
            } else if (posts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.song_detail_empty), style = CorusFont.body, color = CorusColors.Secondary)
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                        Button(
                            onClick = { onNavigateToCompose(resolvedTrack) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CorusColors.Accent,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                        ) {
                            Text(stringResource(R.string.song_detail_be_the_first), style = CorusFont.buttonSmall)
                        }
                    }
                }
            } else {
                // Header with count
                item {
                    val count = uniquePosterCount ?: posts.map { it.user.id }.toSet().size
                    Text(
                        text = pluralStringResource(R.plurals.song_detail_posted_by_count, count, formatUserCount(count)),
                        style = CorusFont.sectionHeader,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg)
                            .padding(top = CorusSpacing.lg, bottom = CorusSpacing.md),
                    )
                }

                items(posts, key = { it.id }) { post ->
                    PostedByRow(
                        post = post,
                        onUserTap = {
                            viewModel.analyticsService.logPostedByProfileTapped("song", trackId, post.user.id)
                            onNavigateToUser(post.user.id)
                        },
                        onPostTap = {
                            viewModel.analyticsService.logPostedByPostTapped("song", trackId, post.id)
                            onNavigateToPost(post.id)
                        },
                    )
                    if (post.id != posts.lastOrNull()?.id) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }

                    // Pagination trigger
                    if (post.id == posts.lastOrNull()?.id && hasMore && !isLoadingMore) {
                        LaunchedEffect(post.id) { viewModel.loadMore() }
                    }
                }

                if (isLoadingMore) {
                    item {
                        CircularProgressIndicator(
                            color = CorusColors.Accent,
                            modifier = Modifier.padding(CorusSpacing.lg),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PostedByRow(
    post: CymbalPost,
    onUserTap: () -> Unit = {},
    onPostTap: () -> Unit = {},
) {
    // Whole row opens the post; the avatar and name column carry their own
    // clickable for the profile. A child clickable consumes the tap, so taps on
    // the avatar/name go to onUserTap and everything else falls through to the
    // row's onPostTap.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPostTap)
            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatarView(
            avatarURL = post.user.avatarURL,
            displayName = post.user.displayName,
            size = CorusSpacing.avatarMedium,
            modifier = Modifier.clickable(onClick = onUserTap),
        )

        Spacer(modifier = Modifier.width(CorusSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            // Username + display name open the profile. Leads with the @username
            // (with the 1ST trophy inline) to match search/follow lists; display
            // name sits muted below.
            Column(modifier = Modifier.clickable(onClick = onUserTap)) {
                UsernameWithFlair(
                    username = post.user.username,
                    isVerified = post.user.isVerified,
                    isClubMember = post.user.isClubMember,
                    flairStyle = post.user.flairStyle,
                    isBot = post.user.isBot,
                    isFirstPoster = post.isFirstPoster,
                    showAtPrefix = true,
                    style = CorusFont.username,
                    color = CorusColors.Text,
                )
                if (post.user.displayName.isNotBlank()) {
                    Text(
                        text = post.user.displayName,
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Caption snippet — part of the row's post tap (no clickable here).
            val caption = post.caption?.trim()
            if (!caption.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "“$caption”",
                    style = CorusFont.caption,
                    color = CorusColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(CorusSpacing.sm))

        // Centered against the full row: timestamp + chevron read as a
        // row-level "open post" affordance.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DateUtils.relativeTime(LocalContext.current, post.timestamp),
                style = CorusFont.caption,
                color = CorusColors.Tertiary,
            )
            Spacer(modifier = Modifier.width(CorusSpacing.xs))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CorusColors.Tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Brand fill color for a service capsule, mirroring iOS `serviceColor`. */
@Composable
@ReadOnlyComposable
private fun serviceColor(service: MusicService): Color = when (service) {
    MusicService.SPOTIFY -> CorusColors.SpotifyGreen
    MusicService.APPLE_MUSIC -> CorusColors.AppleMusicPink
    MusicService.TIDAL -> CorusColors.Tidal
    MusicService.DEEZER -> CorusColors.DeezerPurple
}

/**
 * Foreground for a filled service capsule. TIDAL's fill is monochrome and
 * inverts per theme, so its text must invert too; the colored fills keep white
 * text. Mirrors iOS `serviceTextColor`.
 */
@Composable
@ReadOnlyComposable
private fun serviceTextColor(service: MusicService): Color =
    if (service == MusicService.TIDAL) CorusColors.TidalText else Color.White

private fun formatUserCount(count: Int): String {
    return when {
        count < 1000 -> "$count"
        count < 1_000_000 -> String.format("%.2fK", count / 1000.0)
        else -> String.format("%.2fM", count / 1_000_000.0)
    }
}
