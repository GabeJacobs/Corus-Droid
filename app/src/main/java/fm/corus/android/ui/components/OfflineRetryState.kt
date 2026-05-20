package fm.corus.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.theme.CorusSpacing

/**
 * Shared offline / error empty state with a retry button. Mirrors the iOS
 * `NetworkErrorEmptyState` component so the two platforms render the same UI
 * when a screen's fetch fails and there's nothing to show.
 *
 * Use this in any tab/screen that may fail to load due to bad connectivity.
 * Pair with an [onRetry] handler that re-runs the screen's load function.
 */
@Composable
fun OfflineRetryState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.WifiOff,
    title: String = stringResource(R.string.feed_offline_title),
    subtitle: String = stringResource(R.string.feed_offline_subtitle),
    retryLabel: String = stringResource(R.string.feed_offline_retry),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CorusColors.Tertiary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.height(CorusSpacing.md))
        Text(
            text = title,
            style = CorusFont.bodyMedium,
            color = CorusColors.Secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CorusSpacing.xs))
        Text(
            text = subtitle,
            style = CorusFont.caption,
            color = CorusColors.Tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = CorusSpacing.xl),
        )
        Spacer(modifier = Modifier.height(CorusSpacing.lg))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = CorusColors.Accent),
            shape = RoundedCornerShape(CorusSpacing.pillCornerRadius),
        ) {
            Text(
                text = retryLabel,
                style = CorusFont.button,
                color = CorusColors.Background,
            )
        }
    }
}
