package fm.corus.android.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fm.corus.android.domain.NowPlayingManager
import kotlinx.coroutines.delay

@Composable
fun ExpandingPlayerScaffold(
    expansion: Float,
    travelPx: Float,
    isMoving: Boolean,
    artworkUrl: String?,
    allowsMiniInteraction: Boolean,
    nowPlayingManager: NowPlayingManager,
    onMiniHeightChanged: (Float) -> Unit,
    miniContent: @Composable (miniInteractive: Boolean) -> Unit,
    fullContent: @Composable (fullInteractive: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val miniAlpha = miniOpacity(expansion, travelPx)
    val fullAlpha = fullOpacity(expansion)
    val cornerDp = playerCornerRadiusDp(expansion, isMoving).dp
    val fullInteractive = fullPlayerInteractive(expansion, isMoving)
    val miniInteractive = miniPlayerInteractive(allowsMiniInteraction, expansion, travelPx)

    var fullPlayerMounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        fullPlayerMounted = true
    }
    LaunchedEffect(expansion) {
        if (expansion > 0.01f) fullPlayerMounted = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = cornerDp, topEnd = cornerDp)),
    ) {
        PlayerArtworkBackdrop(
            artworkUrl = artworkUrl,
            expansion = expansion,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .alpha(miniAlpha)
                .zIndex(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onMiniHeightChanged(it.height.toFloat()) },
            ) {
                miniContent(miniInteractive)
                PlayerShellProgressLine(
                    nowPlayingManager = nowPlayingManager,
                    interactive = miniInteractive,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(3f),
                )
            }
        }

        if (fullPlayerMounted && fullAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(fullAlpha)
                    .zIndex(if (fullPlayerLayerAboveMini(expansion, isMoving)) 2f else 0f),
            ) {
                fullContent(fullInteractive)
            }
        }
    }
}
