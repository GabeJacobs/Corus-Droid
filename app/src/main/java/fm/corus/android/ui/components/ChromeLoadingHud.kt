package fm.corus.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import kotlinx.coroutines.delay

/**
 * How long a resolve must stay in-flight before the chrome HUD paints.
 *
 * A tap-frame spinner on a ~50–200ms catalog hit is more jarring than the
 * wait itself (Material / HIG: don't show a progress indicator until the
 * action has taken more than a moment). Fast trending-album / artist /
 * link-out resolves never flash; slow ones still get the dim + spinner.
 */
const val CHROME_HUD_SHOW_DELAY_MS = 300L

private const val CHROME_HUD_FADE_IN_MS = 150

/**
 * Blocking chrome HUD: dim + large white spinner on a dark card.
 * Used for music-service link-out and Search destination resolve
 * (trending album / artist). Mirrors iOS `ChromeLoadingHud`.
 *
 * [visible] is the in-flight flag. The HUD itself is deferred by
 * [CHROME_HUD_SHOW_DELAY_MS] so a fast resolve never paints. Hide is
 * instant — the destination push is about to cover this chrome.
 */
@Composable
fun ChromeLoadingHud(visible: Boolean) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(CHROME_HUD_SHOW_DELAY_MS)
            shown = true
        } else {
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(animationSpec = tween(CHROME_HUD_FADE_IN_MS)),
        exit = ExitTransition.None,
    ) {
        ChromeLoadingHudCard()
    }
}

@Composable
private fun ChromeLoadingHudCard() {
    val loading = stringResource(R.string.song_detail_resolving)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = loading },
        color = Color.Black.copy(alpha = 0.25f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.85f),
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.padding(28.dp),
                )
            }
        }
    }
}
