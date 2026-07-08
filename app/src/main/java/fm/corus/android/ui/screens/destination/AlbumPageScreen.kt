package fm.corus.android.ui.screens.destination

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import fm.corus.android.R
import fm.corus.android.data.model.primaryNameHint
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.navigation.ArtistPageRoute
import fm.corus.android.ui.navigation.SongDetailRoute
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPageScreen(
    albumId: String,
    titleHint: String? = null,
    artistHint: String? = null,
    coverUrlHint: String? = null,
    yearHint: Int? = null,
    viewModel: AlbumPageViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToSong: (SongDetailRoute) -> Unit = {},
    onNavigateToArtist: (ArtistPageRoute) -> Unit = {},
) {
    val catalog by viewModel.catalog.collectAsState()
    val isCatalogLoading by viewModel.isCatalogLoading.collectAsState()
    val catalogError by viewModel.catalogError.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val isPostsLoading by viewModel.isPostsLoading.collectAsState()
    val postsError by viewModel.postsError.collectAsState()
    val context = LocalContext.current

    val title = catalog?.title?.takeIf { it.isNotBlank() } ?: titleHint
    val artistName = catalog?.artistName?.takeIf { it.isNotBlank() } ?: artistHint
    val cover = catalog?.coverUrl ?: coverUrlHint
    val artistId = catalog?.artistIds?.firstOrNull()

    // Full tracklist as a queue so tapping the cover (or a track row) plays the
    // album and rolls on through the rest. `toQueuedTrack` is internal to this
    // package.
    val tracks = catalog?.tracks ?: emptyList()
    val albumQueue = remember(tracks) { tracks.map { it.toQueuedTrack() } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(albumId) {
        viewModel.analyticsService.logAlbumPageViewed(albumId)
        viewModel.load(albumId)
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
            contentPadding = PaddingValues(bottom = CorusSpacing.xxxl + CorusSpacing.xxxl),
        ) {
            // ── Header: cover (tap to play the album), title, tappable artist,
            // meta line ──
            // Tapping the cover plays the album as queued 30s previews (Android
            // has no full-track tier), mirroring tapping the first track row.
            // There's still no separate album Play button — the art tap is the
            // affordance.
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    if (cover != null) {
                        AsyncImage(
                            model = cover,
                            contentDescription = title,
                            modifier = Modifier
                                .size(200.dp)
                                .shadow(4.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = albumQueue.isNotEmpty()) {
                                    val first = albumQueue.firstOrNull() ?: return@clickable
                                    viewModel.analyticsService.logAlbumTrackPreviewed(albumId, first.trackId)
                                    scope.launch {
                                        viewModel.nowPlayingManager.play(track = first, queue = albumQueue)
                                    }
                                },
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.height(CorusSpacing.md))
                    }
                    if (title != null || catalogError) {
                        Text(
                            text = title ?: stringResource(R.string.destination_album_label),
                            style = CorusFont.songTitleLarge,
                            color = CorusColors.Text,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (!artistName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                        // Tappable ONLY when the catalog carries a Spotify
                        // artist id (`am:` albums return artistIds: [] and the
                        // name renders as plain text). Exact same style either
                        // way — no accent, no underline.
                        val idCount = catalog?.artistIds?.size ?: 0
                        Text(
                            text = artistName,
                            style = CorusFont.bodyMedium,
                            color = CorusColors.Text,
                            textAlign = TextAlign.Center,
                            modifier = if (artistId != null) {
                                Modifier.clickable {
                                    onNavigateToArtist(
                                        ArtistPageRoute(
                                            artistId = artistId,
                                            name = primaryNameHint(artistName, idCount),
                                        )
                                    )
                                }
                            } else Modifier,
                        )
                    }
                    // Meta: "Album · {year} · {N} songs". "Album" (plus the
                    // year, when the source row hinted it) renders from first
                    // paint; the song count fills in on the same line when the
                    // catalog lands — no layout shift.
                    val trackCount = catalog?.tracks?.size ?: 0
                    val metaParts = buildList {
                        add(stringResource(R.string.destination_album_label))
                        (catalog?.year ?: yearHint)?.let { add(it.toString()) }
                        if (trackCount > 0) {
                            add(pluralStringResource(R.plurals.destination_song_count, trackCount, trackCount))
                        }
                    }
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = CorusFont.caption,
                        color = CorusColors.Secondary,
                    )
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }
            }

            // ── Tracklist ──
            if (isCatalogLoading && catalog == null) {
                items(6) { SkeletonNumberedTrackRow() }
            } else if (catalogError && catalog == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CorusSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.destination_album_load_error),
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                        )
                        TextButton(onClick = { viewModel.loadCatalog(albumId) }) {
                            Text(
                                text = stringResource(R.string.song_detail_try_again),
                                style = CorusFont.buttonSmall,
                                color = CorusColors.Accent,
                            )
                        }
                    }
                }
            } else {
                if (tracks.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.destination_no_tracks),
                            style = CorusFont.body,
                            color = CorusColors.Secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = CorusSpacing.xl),
                        )
                    }
                } else {
                    items(tracks.size) { index ->
                        val track = tracks[index]
                        CatalogTrackRow(
                            track = track,
                            nowPlaying = viewModel.nowPlayingManager,
                            number = index + 1,
                            queue = albumQueue,
                            onRowTap = {
                                viewModel.analyticsService.logPostFromAlbum(albumId, track.id)
                                onNavigateToSong(track.toSongDetailRoute())
                            },
                            onPreviewStarted = {
                                viewModel.analyticsService.logAlbumTrackPreviewed(albumId, track.id)
                            },
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                HorizontalDivider(color = CorusColors.Divider, thickness = 0.5.dp)
            }

            // ── Shared from this album ──
            item {
                DestinationSectionHeader(
                    title = if (uniquePosterCount > 0) {
                        pluralStringResource(
                            R.plurals.destination_shared_by_people,
                            uniquePosterCount,
                            formatDestinationCount(uniquePosterCount),
                        )
                    } else {
                        stringResource(R.string.destination_shared_from_album)
                    },
                )
            }
            if (isPostsLoading) {
                items(4) { index ->
                    SkeletonUserRow()
                    if (index < 3) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = CorusColors.Divider,
                            thickness = 0.5.dp,
                        )
                    }
                }
            } else if (postsError) {
                item {
                    Text(
                        text = stringResource(R.string.destination_album_posts_load_error),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    )
                }
            } else if (posts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.destination_no_posts_album),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    )
                }
            } else {
                items(posts.size) { index ->
                    val post = posts[index]
                    DestinationPostRow(
                        post = post,
                        onUserTap = { onNavigateToUser(post.user.id) },
                        onPostTap = { onNavigateToPost(post.id) },
                    )
                }
            }

            // ── Attribution footer (Spotify link hidden for `am:` albums) ──
            item {
                DestinationAttributionFooter(
                    attribution = stringResource(R.string.destination_music_attribution),
                    onOpenSpotify = if (albumId.startsWith("am:")) null else {
                        {
                            val url = "https://open.spotify.com/album/${Uri.encode(albumId)}"
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    },
                )
            }
        }
    }
}
