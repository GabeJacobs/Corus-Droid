package fm.corus.android.ui.screens.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonAlbumGridCell
import fm.corus.android.ui.navigation.AlbumPageRoute
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/** Spotify-style release-type filter. "single" is Spotify's album_type for
 *  both singles and EPs, hence the combined label. */
private enum class DiscographyFilter(val key: String?) {
    ALL(null),
    ALBUMS("album"),
    SINGLES_EPS("single"),
    COMPILATIONS("compilation"),
}

/**
 * Discography see-all: filter chips (only types the artist actually has, plus
 * All) over a cover grid. Reuses the artist page's getArtistDetail data via
 * the data source's in-memory catalog cache — no second network fetch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDiscographyScreen(
    artistId: String,
    nameHint: String? = null,
    viewModel: ArtistPageViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToAlbum: (AlbumPageRoute) -> Unit = {},
) {
    val detail by viewModel.detail.collectAsState()
    val isCatalogLoading by viewModel.isCatalogLoading.collectAsState()
    val catalogError by viewModel.catalogError.collectAsState()
    var filter by remember { mutableStateOf(DiscographyFilter.ALL) }

    LaunchedEffect(artistId) {
        viewModel.loadCatalog(artistId, nameHint)
    }

    val artistName = detail?.name?.takeIf { it.isNotBlank() } ?: nameHint
    val allAlbums = detail?.albums ?: emptyList()
    val presentTypes = allAlbums.map { it.albumType }.toSet()
    val chips = DiscographyFilter.entries.filter { it.key == null || it.key in presentTypes }
    val albums = if (filter.key == null) allAlbums else allAlbums.filter { it.albumType == filter.key }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.padding(horizontal = CorusSpacing.lg)) {
                Text(
                    text = stringResource(R.string.destination_discography),
                    style = CorusFont.songTitleLarge,
                    color = CorusColors.Text,
                )
                if (!artistName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    Text(
                        text = artistName,
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Secondary,
                    )
                }
            }

            // Filter chips — only when there's more than one real type to pick.
            if (chips.size > 2) {
                Spacer(modifier = Modifier.height(CorusSpacing.md))
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = CorusSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.sm),
                ) {
                    chips.forEach { chip ->
                        val selected = filter == chip
                        Text(
                            text = stringResource(
                                when (chip) {
                                    DiscographyFilter.ALL -> R.string.search_filter_all
                                    DiscographyFilter.ALBUMS -> R.string.destination_filter_albums
                                    DiscographyFilter.SINGLES_EPS -> R.string.destination_filter_singles_eps
                                    DiscographyFilter.COMPILATIONS -> R.string.destination_filter_compilations
                                }
                            ),
                            style = CorusFont.captionMedium,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else CorusColors.Secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) CorusColors.Accent else CorusColors.CardBackground)
                                .clickable { filter = chip }
                                .padding(horizontal = CorusSpacing.md, vertical = CorusSpacing.sm),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(CorusSpacing.md))

            val gridBottomPadding = CorusSpacing.xxxl

            if (isCatalogLoading && detail == null) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(
                        start = CorusSpacing.lg,
                        end = CorusSpacing.lg,
                        bottom = gridBottomPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                ) {
                    items(9) {
                        SkeletonAlbumGridCell(
                            modifier = Modifier.clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
                        )
                    }
                }
            } else if (catalogError && detail == null) {
                Text(
                    text = stringResource(R.string.destination_catalog_load_error),
                    style = CorusFont.body,
                    color = CorusColors.Secondary,
                    modifier = Modifier.padding(horizontal = CorusSpacing.lg, vertical = CorusSpacing.lg),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(
                        start = CorusSpacing.lg,
                        end = CorusSpacing.lg,
                        bottom = gridBottomPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                ) {
                    items(albums, key = { it.id }) { album ->
                        DiscographyGridCell(
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
}

@Composable
private fun DiscographyGridCell(
    album: fm.corus.android.data.model.AlbumSummary,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                .background(CorusColors.CardBackground),
        ) {
            if (album.coverUrl != null) {
                coil3.compose.AsyncImage(
                    model = album.coverUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
        Spacer(modifier = Modifier.height(CorusSpacing.sm))
        Text(
            text = album.title,
            style = CorusFont.captionMedium,
            color = CorusColors.Text,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            text = albumKindCaption(album),
            style = CorusFont.caption,
            color = CorusColors.Secondary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
