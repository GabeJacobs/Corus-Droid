package fm.corus.android.ui.screens.destination

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import fm.corus.android.R
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.components.SkeletonAlbumGridCell
import fm.corus.android.ui.navigation.FilmDetailRoute
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Filmography see-all: a 2:3-poster grid of everything the director directed
 * (no type chips — films only). Reuses the director page's getDirectorDetail
 * via the in-memory catalog cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorFilmographyScreen(
    directorId: String,
    nameHint: String? = null,
    viewModel: DirectorPageViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToFilm: (FilmDetailRoute) -> Unit = {},
) {
    val detail by viewModel.detail.collectAsState()
    val isCatalogLoading by viewModel.isCatalogLoading.collectAsState()
    val catalogError by viewModel.catalogError.collectAsState()

    LaunchedEffect(directorId) {
        viewModel.loadCatalog(directorId)
    }

    val directorName = detail?.name?.takeIf { it.isNotBlank() } ?: nameHint
    val films = detail?.films ?: emptyList()

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
                    text = stringResource(R.string.destination_filmography),
                    style = CorusFont.songTitleLarge,
                    color = CorusColors.Text,
                )
                if (!directorName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(CorusSpacing.xxs))
                    Text(
                        text = directorName,
                        style = CorusFont.bodyMedium,
                        color = CorusColors.Secondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(CorusSpacing.md))

            if (isCatalogLoading && detail == null) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(
                        start = CorusSpacing.lg,
                        end = CorusSpacing.lg,
                        bottom = CorusSpacing.xxxl,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                ) {
                    items(9) {
                        SkeletonAlbumGridCell(
                            modifier = Modifier.clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium)),
                            aspectRatio = 2f / 3f,
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
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(
                        start = CorusSpacing.lg,
                        end = CorusSpacing.lg,
                        bottom = CorusSpacing.xxxl,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
                ) {
                    items(films, key = { it.id }) { film ->
                        Column(
                            modifier = Modifier.clickable {
                                onNavigateToFilm(
                                    FilmDetailRoute(
                                        movieId = film.id,
                                        movieTitle = film.title,
                                        directorName = directorName,
                                        releaseYear = film.year?.toString(),
                                        posterURL = film.posterUrl,
                                    )
                                )
                            },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(CorusSpacing.cornerRadiusMedium))
                                    .background(CorusColors.CardBackground),
                            ) {
                                if (film.posterUrl != null) {
                                    AsyncImage(
                                        model = film.posterUrl,
                                        contentDescription = film.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(CorusSpacing.sm))
                            Text(
                                text = film.title,
                                style = CorusFont.captionMedium,
                                color = CorusColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (film.year != null) {
                                Text(
                                    text = "${film.year}",
                                    style = CorusFont.caption,
                                    color = CorusColors.Secondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
