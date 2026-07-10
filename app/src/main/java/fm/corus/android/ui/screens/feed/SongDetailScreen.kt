package fm.corus.android.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.sp
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
import fm.corus.android.ui.components.ShareMediaSheet
import fm.corus.android.ui.components.ShareMediaSubject
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.components.ToastManager
import fm.corus.android.ui.components.UserAvatarView
import fm.corus.android.ui.components.FirstPosterBadge
import fm.corus.android.ui.components.NewReleaseBadge
import fm.corus.android.ui.components.UsernameWithFlair
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing
import fm.corus.android.ui.theme.CorusSystemBars
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
    isrc: String? = null,
    artistId: String? = null,
    artistIdCount: Int = 0,
    albumId: String? = null,
    /** Release date + precision carried from the caller (search/catalog/trending
     *  tracks have them) so the NEW RELEASE tag paints on the first frame instead
     *  of waiting for posts. Null = fall back to the loaded posts. */
    releaseDate: String? = null,
    releaseDatePrecision: String? = null,
    viewModel: SongDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToCompose: (CymbalTrack) -> Unit = {},
    /** Artist page (artist_pages_enabled) — null while the flag is off, which
     *  keeps the artist line as plain text. */
    onNavigateToArtist: ((fm.corus.android.ui.navigation.ArtistPageRoute) -> Unit)? = null,
    /** Album page (artist_pages_enabled) — null while the flag is off, which
     *  keeps the album line as plain text. */
    onNavigateToAlbum: ((fm.corus.android.ui.navigation.AlbumPageRoute) -> Unit)? = null,
) {
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val resolvedArtistId by viewModel.resolvedArtistId.collectAsState()
    val vmResolvedAlbumId by viewModel.resolvedAlbumId.collectAsState()
    val isResolvingDestination by viewModel.isResolvingDestination.collectAsState()
    val nowPlayingState by viewModel.nowPlayingState.collectAsState()
    val previewLoadingTrackId by viewModel.previewLoadingTrackId.collectAsState()
    val musicService by viewModel.musicServicePreference.current.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showShareSheet by remember { mutableStateOf(false) }
    val shareSearchResults by viewModel.shareSearchResults.collectAsState()
    val recentShareContacts by viewModel.recentShareContacts.collectAsState()
    val isShareSearching by viewModel.isShareSearching.collectAsState()
    val isLoadingShareContacts by viewModel.isLoadingShareContacts.collectAsState()

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

    // Fast-path ids for the "Go to Artist" / "Go to Album" rows + tappable
    // artist/album lines: the seed track, a loaded post, the route hint, or an
    // id resolved on a prior tap this session (vm caches). When none is known
    // the rows still show and resolve on tap (goToArtist / goToAlbum).
    val menuLoadedArtistIds = songInfo?.track?.artistIds ?: emptyList()
    val menuArtistId = menuLoadedArtistIds.firstOrNull() ?: artistId ?: resolvedArtistId
    val menuArtistIdCount = maxOf(menuLoadedArtistIds.size, artistIdCount)
    val menuAlbumTrack = songInfo?.track
    val effectiveAlbumId = resolveSongAlbumId(albumId, posts) ?: vmResolvedAlbumId

    // "Go to Album" is offered for everything except SoundCloud, which has no
    // album (no album concept, no ISRC to resolve with) — a dead end.
    val canShowAlbum = !isSoundCloud

    val artistMissMsg = stringResource(R.string.song_detail_artist_not_found)
    val albumMissMsg = stringResource(R.string.song_detail_album_not_found)

    fun openArtist(id: String, idCount: Int) {
        onNavigateToArtist?.invoke(
            fm.corus.android.ui.navigation.ArtistPageRoute(
                artistId = id,
                name = fm.corus.android.data.model.primaryNameHint(
                    displayArtist.orEmpty(), idCount,
                ).ifEmpty { null },
            )
        )
    }

    fun openAlbum(id: String) {
        onNavigateToAlbum?.invoke(
            fm.corus.android.ui.navigation.AlbumPageRoute(
                albumId = id,
                title = menuAlbumTrack?.albumName?.takeIf { it.isNotBlank() },
                artist = displayArtist,
                coverUrl = artUrl,
                year = (menuAlbumTrack?.releaseDate ?: "").take(4).toIntOrNull(),
            )
        )
    }

    // Open the artist page: use a known id instantly, else resolve on tap.
    fun goToArtist() {
        val known = menuArtistId
        if (known != null) {
            openArtist(known, menuArtistIdCount)
            return
        }
        scope.launch {
            val dest = viewModel.resolveDestinations(
                trackId, effectiveIsrc, displayName.orEmpty(), displayArtist.orEmpty(),
            )
            val aid = dest.artistIds.firstOrNull()
            if (aid != null) openArtist(aid, dest.artistIds.size) else ToastManager.show(artistMissMsg)
        }
    }

    // Open the album page: use a known id instantly, else resolve on tap.
    fun goToAlbum() {
        val known = effectiveAlbumId
        if (known != null) {
            openAlbum(known)
            return
        }
        scope.launch {
            val dest = viewModel.resolveDestinations(
                trackId, effectiveIsrc, displayName.orEmpty(), displayArtist.orEmpty(),
            )
            val alid = dest.albumId
            if (alid != null) openAlbum(alid) else ToastManager.show(albumMissMsg)
        }
    }

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
            isrc = isrc,
            trackName = songName,
            artistName = artistName,
            routeArtistId = artistId,
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
                actions = {
                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }
                        CorusHeaderIconButton(
                            onClick = { menuExpanded = true },
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.feed_cd_more_options),
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.post_menu_share), style = CorusFont.body) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showShareSheet = true
                                },
                            )
                            // Always offered when the feature is on — resolves
                            // the id on tap if the seed track lacks one.
                            if (onNavigateToArtist != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.post_menu_go_to_artist), style = CorusFont.body) },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        goToArtist()
                                    },
                                )
                            }
                            // Album everywhere except SoundCloud (no album).
                            if (onNavigateToAlbum != null && canShowAlbum) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.post_menu_go_to_album), style = CorusFont.body) },
                                    leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        goToAlbum()
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CorusColors.Background),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                    // Artist page (artist_pages_enabled): the artist line is
                    // tappable whenever the flag is on — goToArtist uses a known
                    // id (track / post / route / prior resolve) instantly, else
                    // resolves it on tap. Style unchanged (no accent, no underline).
                    val artistTapModifier = if (onNavigateToArtist != null) {
                        Modifier.clickable { goToArtist() }
                    } else Modifier
                    Text(
                        text = displayArtist,
                        style = CorusFont.artistNameLarge,
                        color = CorusColors.Secondary,
                        modifier = artistTapModifier,
                    )
                }

                // Album line (web/iOS parity): "{album} · {year}", muted, from
                // the first loaded post (posts carry albumName + releaseDate).
                // Tappable whenever the flag is on (except SoundCloud, no album);
                // goToAlbum uses a known id instantly or resolves it on tap. Same
                // plain style either way — no accent, no underline.
                val albumTrack = songInfo?.track
                if (albumTrack != null && albumTrack.albumName.isNotBlank()) {
                    val year = (albumTrack.releaseDate ?: "").take(4)
                    val albumLine = if (year.isEmpty()) albumTrack.albumName else "${albumTrack.albumName} · $year"
                    val albumTapModifier = if (onNavigateToAlbum != null && canShowAlbum) {
                        Modifier.clickable { goToAlbum() }
                    } else Modifier
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    Text(
                        text = albumLine,
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = CorusSpacing.lg)
                            .then(albumTapModifier),
                    )
                }

                // NEW RELEASE tag (web/iOS parity) — recently released songs badge
                // here, below the album/year line and above the buttons. Prefer the
                // seed release date carried on the route (search/catalog/trending
                // taps all have it) so the tag paints on the first frame; fall back
                // to the loaded posts for callers that didn't carry it.
                if (CymbalPost.isTrackNewRelease(releaseDate, releaseDatePrecision) ||
                    posts.any { it.isNewRelease() }
                ) {
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    NewReleaseBadge(fontSize = 11.sp)
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
        // On-tap destination resolve HUD — a brief top spinner while we look up
        // the artist/album id. Misses surface as a toast (ToastManager).
        if (isResolvingDestination) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(CorusSpacing.sm))
                Text(
                    text = stringResource(R.string.song_detail_resolving),
                    style = CorusFont.caption,
                    color = Color.White,
                )
            }
        }
        }
    }

    // ── Share Song bottom sheet ──
    if (showShareSheet) {
        val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val songSharedMsg = stringResource(R.string.song_detail_toast_song_sent)

        LaunchedEffect(Unit) { viewModel.loadRecentShareContacts() }

        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            sheetState = shareSheetState,
            containerColor = CorusColors.Background,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            contentWindowInsets = { WindowInsets.systemBars.only(WindowInsetsSides.Bottom) },
        ) {
            CorusSystemBars()
            BackHandler { showShareSheet = false }
            ShareMediaSheet(
                subject = ShareMediaSubject.Track(resolvedTrack),
                recentContacts = recentShareContacts,
                searchResults = shareSearchResults,
                isSearching = isShareSearching,
                isLoadingContacts = isLoadingShareContacts,
                onSearchQueryChange = { query -> viewModel.searchShareUsers(query) },
                onSendToUser = { userId, message ->
                    viewModel.sendTrackToUser(userId, resolvedTrack, message)
                    ToastManager.show(songSharedMsg)
                    showShareSheet = false
                },
                onDismiss = { showShareSheet = false },
                onAnalyticsLog = { method ->
                    viewModel.analyticsService.logSongShared(trackId = resolvedTrack.id, method = method)
                },
            )
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

/**
 * Resolve the album id backing the song page's "Go to Album" menu row + the
 * tappable album line. Prefers the route hint (search/catalog rows carry a
 * Spotify album id); otherwise falls back to the first loaded post that
 * denormalizes one — scanning ALL posts (not just the first) so a post missing
 * the id (older / `am:` / `sc:`) doesn't hide the link. Mirrors the artist-id
 * fallback: an artist-page "Popular" tap arrives Apple-sourced with no album
 * id, but its posts carry one. Returns null when no source has one (link/row
 * stays hidden). Blank strings are treated as absent.
 */
internal fun resolveSongAlbumId(routeAlbumId: String?, posts: List<CymbalPost>): String? =
    routeAlbumId?.takeIf { it.isNotBlank() }
        ?: posts.firstNotNullOfOrNull { it.track.albumId?.takeIf { id -> id.isNotBlank() } }

private fun formatUserCount(count: Int): String {
    return when {
        count < 1000 -> "$count"
        count < 1_000_000 -> String.format("%.2fK", count / 1000.0)
        else -> String.format("%.2fM", count / 1_000_000.0)
    }
}
