package fm.corus.android.ui.screens.profile

import androidx.compose.runtime.Composable
import fm.corus.android.ui.components.CorusPromptButton
import fm.corus.android.ui.components.CorusPromptOverlay

/**
 * "Quick vs export all" chooser for profile/hashtag playlist generation.
 */
@Composable
fun PlaylistExportChooserDialog(
    count: Int,
    caveat: String,
    onQuick: () -> Unit,
    onAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = buildString {
        append("Make a playlist of the most recent 75 songs, or export all $count songs.")
        if (caveat.isNotBlank()) {
            append(' ')
            append(caveat)
        }
    }

    CorusPromptOverlay(
        visible = true,
        title = "Generate a playlist?",
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
