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
 * "See all" trailers grid for a director page (mirrors web's
 * /director/{id}/trailers). Same card/player as music videos; NEWEST-FIRST so
 * recency is obvious. Reuses the director page's getDirectorDetail data via the
 * data source's cache. Logs `trailer_played` (not `music_video_played`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorTrailersScreen(
    directorId: String,
    nameHint: String? = null,
    viewModel: DirectorPageViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val detail by viewModel.detail.collectAsState()
    var activeVideo by remember { mutableStateOf<MusicVideo?>(null) }

    LaunchedEffect(directorId) {
        viewModel.loadCatalog(directorId)
    }

    val directorName = detail?.name?.takeIf { it.isNotBlank() } ?: nameHint
    val trailers = (detail?.trailers ?: emptyList())
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
                        text = stringResource(R.string.destination_trailers),
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
                    Spacer(modifier = Modifier.height(CorusSpacing.md))
                }
            }

            val active = activeVideo
            val activeYouTubeId = active?.youtubeId
            if (active != null && activeYouTubeId != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MusicVideoPlayerPanel(
                        youtubeId = activeYouTubeId,
                        title = active.title,
                        onClose = { activeVideo = null },
                        modifier = Modifier.padding(bottom = CorusSpacing.sm),
                    )
                }
            }

            items(trailers, key = { it.id }) { video ->
                MusicVideoCard(
                    video = video,
                    isActive = activeVideo?.id == video.id,
                    onClick = {
                        viewModel.analyticsService.logTrailerPlayed(directorId, video.id)
                        activeVideo = video
                    },
                )
            }
        }
    }
}
