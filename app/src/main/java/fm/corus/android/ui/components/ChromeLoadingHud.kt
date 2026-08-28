package fm.corus.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fm.corus.android.R

/**
 * Blocking chrome HUD: dim + large white spinner on a dark card.
 * Used for music-service link-out and Search destination resolve
 * (trending album / artist). Mirrors iOS `ChromeLoadingHud`.
 */
@Composable
fun ChromeLoadingHud() {
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
