package fm.corus.android.ui.screens.destination

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.data.model.AlbumSummary
import fm.corus.android.data.model.CymbalTrack
import fm.corus.android.data.model.MusicVideo
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonAlbumGridCell
import fm.corus.android.ui.components.SkeletonSongRow
import fm.corus.android.ui.components.SkeletonUserRow
import fm.corus.android.ui.navigation.AlbumPageRoute
import fm.corus.android.ui.navigation.SongDetailRoute
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/** Localized "{Album|Single|Compilation} · {year}" caption for a rail cover. */
@Composable
internal fun albumKindCaption(album: AlbumSummary): String {
    val kind = stringResource(
        when (album.albumType) {
            "single" -> R.string.destination_single_label
            "compilation" -> R.string.destination_compilation_label
            else -> R.string.destination_album_label
        }
    )
    return listOfNotNull(kind, album.year?.toString()).joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPageScreen(
    artistId: String,
    nameHint: String? = null,
    imageUrlHint: String? = null,
    viewModel: ArtistPageViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToPost: (String) -> Unit = {},
    onNavigateToSong: (SongDetailRoute) -> Unit = {},
    onNavigateToAlbum: (AlbumPageRoute) -> Unit = {},
    onSeeAllPosts: () -> Unit = {},
    onSeeAllDiscography: () -> Unit = {},
    onSeeAllVideos: () -> Unit = {},
) {
    val detail by viewModel.detail.collectAsState()
    val isCatalogLoading by viewModel.isCatalogLoading.collectAsState()
    val catalogError by viewModel.catalogError.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val viewerPosts by viewModel.viewerPosts.collectAsState()
    val posters by viewModel.posters.collectAsState()
    val uniquePosterCount by viewModel.uniquePosterCount.collectAsState()
    val isPostsLoading by viewModel.isPostsLoading.collectAsState()
    val postsError by viewModel.postsError.collectAsState()
    val context = LocalContext.current

    // Popular shows 6 by default (keeps the social sections high); Show more
    // reveals the full top-10 the payload already carries.
    var showAllPopular by remember { mutableStateOf(false) }
    // The music-video card the user tapped — its full video plays inline.
    var activeVideo by remember { mutableStateOf<MusicVideo?>(null) }

    val artistName = detail?.name?.takeIf { it.isNotBlank() } ?: nameHint
    val heroImage = detail?.imageUrl ?: imageUrlHint
    val matchedVideos = detail?.musicVideos?.filter { it.youtubeId != null } ?: emptyList()

    LaunchedEffect(artistId) {
        viewModel.analyticsService.logArtistPageViewed(artistId)
        viewModel.loadCatalog(artistId, nameHint)
        viewModel.loadPosts(artistId, nameHint)
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
            // ── Hero: artist image with name on a bottom gradient scrim ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CorusSpacing.lg)
                        .aspectRatio(5f / 3f)
                        .clip(RoundedCornerShape(CorusSpacing.cornerRadiusLarge))
                        .background(CorusColors.CardBackground),
                ) {
                    if (heroImage != null) {
                        AsyncImage(
                            model = heroImage,
                            contentDescription = artistName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (heroImage != null || artistName != null || catalogError) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.35f),
                                            Color.Black.copy(alpha = 0.75f),
                                        ),
                                    ),
                                )
                                .padding(CorusSpacing.lg)
                                .padding(top = CorusSpacing.xxl),
                        ) {
                            Text(
                                text = artistName
                                    ?: stringResource(R.string.destination_artist_label),
                                style = CorusFont.songTitleLarge,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                            Text(
                                text = stringResource(R.string.destination_artist_label),
                                style = CorusFont.captionMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }

            // ── Shared by N people (hidden when 0) ──
            if (uniquePosterCount > 0) {
                item {
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    SharedByPeopleRow(
                        posters = posters,
                        count = uniquePosterCount,
                        onClick = onSeeAllPosts,
                    )
                }
            } else if (isPostsLoading && !postsError) {
                // Reserve the row's space while the social fetch is in flight
                // so content below doesn't shift when the facepile lands.
                item {
                    Spacer(modifier = Modifier.height(CorusSpacing.sm))
                    SkeletonSharedByPeopleRow()
                }
            }

            // ── Your posts (only when the viewer has posted this artist) ──
            if (viewerPosts.isNotEmpty()) {
                item {
                    DestinationSectionHeader(stringResource(R.string.destination_your_posts))
                }
                items(viewerPosts.size) { index ->
                    val post = viewerPosts[index]
                    DestinationPostRow(
                        post = post,
                        onUserTap = { onNavigateToUser(post.user.id) },
                        onPostTap = { onNavigateToPost(post.id) },
                    )
                }
            }

            // ── Catalog (Popular + Discography) ──
            if (isCatalogLoading && detail == null) {
                item {
                    Column {
                        DestinationSectionHeader(stringResource(R.string.destination_popular))
                        repeat(4) { SkeletonSongRow() }
                        DestinationSectionHeader(stringResource(R.string.destination_discography))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        ) {
                            items(4) {
                                SkeletonAlbumGridCell(
                                    modifier = Modifier
                                        .width(132.dp)
                                        .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
                                )
                            }
                        }
                    }
                }
            } else if (catalogError && detail == null) {
                item {
                    Text(
                        text = stringResource(R.string.destination_catalog_load_error),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.lg),
                    )
                }
            } else {
                val allTopTracks = detail?.topTracks ?: emptyList()
                val topTracks = allTopTracks.take(if (showAllPopular) 10 else 6)
                if (topTracks.isNotEmpty()) {
                    item {
                        DestinationSectionHeader(stringResource(R.string.destination_popular))
                    }
                    items(topTracks.size) { index ->
                        val track = topTracks[index]
                        CatalogTrackRow(
                            track = track,
                            nowPlaying = viewModel.nowPlayingManager,
                            onRowTap = {
                                viewModel.analyticsService.logPostFromArtistPage(artistId, track.id)
                                onNavigateToSong(track.toSongDetailRoute())
                            },
                            onPreviewStarted = {
                                viewModel.analyticsService.logArtistSongPreviewed(artistId, track.id)
                            },
                        )
                    }
                    if (allTopTracks.size > 6) {
                        item {
                            Text(
                                text = stringResource(
                                    if (showAllPopular) R.string.destination_show_less
                                    else R.string.destination_show_more
                                ),
                                style = CorusFont.captionMedium,
                                color = CorusColors.Secondary,
                                modifier = Modifier
                                    .clickable { showAllPopular = !showAllPopular }
                                    .padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.xs),
                            )
                        }
                    }
                }

                val albums = detail?.albums ?: emptyList()
                if (albums.isNotEmpty()) {
                    item {
                        DestinationSectionHeader(
                            title = stringResource(R.string.destination_discography),
                            onSeeAll = onSeeAllDiscography,
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = CorusSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                        ) {
                            items(albums.take(12).size) { index ->
                                val album = albums[index]
                                DiscographyRailCell(
                                    album = album,
                                    onClick = {
                                        onNavigateToAlbum(
                                            AlbumPageRoute(
                                                albumId = album.id,
                                                title = album.title,
                                                artist = artistName,
                                                coverUrl = album.coverUrl,
                                                year = album.year,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ── Recent posts ──
            item {
                DestinationSectionHeader(
                    title = stringResource(R.string.destination_recent_posts),
                    onSeeAll = if (posts.size >= ArtistPageViewModel.PAGE_SIZE) onSeeAllPosts else null,
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
                        text = stringResource(R.string.destination_posts_load_error),
                        style = CorusFont.body,
                        color = CorusColors.Secondary,
                        modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.sm),
                    )
                }
            } else if (posts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.destination_no_posts_artist),
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

            // ── Music videos rail — below the social content by design (posts
            //    are the differentiator; videos are the end-of-page delighter). ──
            if (matchedVideos.isNotEmpty()) {
                item {
                    MusicVideoRail(
                        videos = matchedVideos,
                        activeVideo = activeVideo,
                        onPlay = { video ->
                            viewModel.analyticsService.logMusicVideoPlayed(artistId, video.id)
                            activeVideo = video
                        },
                        onClosePlayer = { activeVideo = null },
                        onSeeAll = if (matchedVideos.size > 12) onSeeAllVideos else null,
                    )
                }
            }

            // ── Attribution footer ──
            item {
                DestinationAttributionFooter(
                    attribution = stringResource(R.string.destination_music_attribution),
                    onOpenSpotify = {
                        val url = "https://open.spotify.com/artist/${Uri.encode(artistId)}"
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun DiscographyRailCell(
    album: AlbumSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .background(CorusColors.CardBackground),
        ) {
            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            text = album.title,
            style = CorusFont.captionMedium,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = albumKindCaption(album),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
