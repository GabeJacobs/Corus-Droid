package fm.corus.android.ui.screens.destination

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.R
import fm.corus.android.data.model.MusicVideo
import fm.corus.android.ui.components.CorusHeaderIconButton
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * "See all" music-videos grid (mirrors web's /artist/{id}/videos). All
 * YouTube-matched videos, NEWEST-FIRST so it's obvious nothing recent is
 * missing (the rail is ranked hits-first, which can bury new releases).
 * Reuses the artist page's getArtistDetail data via the data source's cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistMusicVideosScreen(
    artistId: String,
    nameHint: String? = null,
    viewModel: ArtistPageViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val detail by viewModel.detail.collectAsState()
    var activeVideo by remember { mutableStateOf<MusicVideo?>(null) }

    LaunchedEffect(artistId) {
        viewModel.loadCatalog(artistId, nameHint)
    }

    val artistName = detail?.name?.takeIf { it.isNotBlank() } ?: nameHint
    val videos = (detail?.musicVideos ?: emptyList())
        .filter { it.youtubeId != null }
        .sortedByDescending { it.year ?: 0 }

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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = CorusSpacing.lg,
                end = CorusSpacing.lg,
                bottom = CorusSpacing.xxxl,
            ),
            horizontalArrangement = Arrangement.spacedBy(CorusSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CorusSpacing.lg),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        text = stringResource(R.string.destination_music_videos),
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
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }
            }

            val activeYouTubeId = activeVideo?.youtubeId
            if (activeYouTubeId != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MusicVideoPlayerPanel(
                        youtubeId = activeYouTubeId,
                        onClose = { activeVideo = null },
                        modifier = Modifier.padding(bottom = CorusSpacing.sm),
                    )
                }
            }

            items(videos, key = { it.id }) { video ->
                MusicVideoCard(
                    video = video,
                    isActive = activeVideo?.id == video.id,
                    onClick = {
                        viewModel.analyticsService.logMusicVideoPlayed(artistId, video.id)
                        activeVideo = video
                    },
                )
            }
        }
    }
}
