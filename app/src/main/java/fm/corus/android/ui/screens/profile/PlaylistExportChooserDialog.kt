package fm.corus.android.ui.screens.profile

import androidx.compose.runtime.Composable
import fm.corus.android.domain.PLAYLIST_EXPORT_CHOOSER_TITLE
import fm.corus.android.ui.components.CorusPromptButton
import fm.corus.android.ui.components.CorusPromptOverlay

/**
 * "Quick vs export all" chooser for profile/hashtag playlist generation.
 */
@Composable
fun PlaylistExportChooserDialog(
    count: Int,
    message: String,
    onQuick: () -> Unit,
    onAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    CorusPromptOverlay(
        visible = true,
        title = PLAYLIST_EXPORT_CHOOSER_TITLE,
        message = message,
        onDismiss = onDismiss,
        buttons = listOf(
            CorusPromptButton(
                label = "Quick playlist · most recent 75",
                emphasized = true,
                onClick = onQuick,
            ),
            CorusPromptButton(
                label = "All $count songs",
                onClick = onAll,
            ),
            CorusPromptButton(
                label = "Cancel",
                onClick = onDismiss,
            ),
        ),
    )
}
