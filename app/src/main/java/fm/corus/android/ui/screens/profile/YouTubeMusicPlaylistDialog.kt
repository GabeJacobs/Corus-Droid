package fm.corus.android.ui.screens.profile

import androidx.compose.runtime.Composable
import fm.corus.android.R
import fm.corus.android.ui.components.CorusPromptButton
import fm.corus.android.ui.components.CorusPromptOverlay

/**
 * Explainer when the viewer's music service is YouTube Music — offers a Spotify
 * playlist fallback, optionally with quick-vs-all choice.
 */
@Composable
fun YouTubeMusicPlaylistDialog(
    count: Int,
    offersFullExport: Boolean,
    onQuick: () -> Unit,
    onAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = buildString {
        append(
            "Playlist export isn't available for YouTube Music yet, but it works with other services. "
        )
        append(
            if (offersFullExport) {
                "Corus can make a Spotify playlist instead."
            } else {
                "Want to make a Spotify playlist instead?"
            }
        )
    }

    val buttons = buildList {
        if (offersFullExport) {
            add(
                CorusPromptButton(
                    label = "Quick playlist · most recent 75",
                    emphasized = true,
                    onClick = onQuick,
                )
            )
            add(
                CorusPromptButton(
                    label = "All $count songs",
                    onClick = onAll,
                )
            )
        } else {
            add(
                CorusPromptButton(
                    label = "Generate Spotify Playlist",
                    emphasized = true,
                    onClick = onQuick,
                )
            )
        }
        add(CorusPromptButton(label = "Cancel", onClick = onDismiss))
    }

    CorusPromptOverlay(
        visible = true,
        title = "YouTube Music playlists unavailable",
        message = message,
        iconRes = R.drawable.spotify_logo,
        onDismiss = onDismiss,
        buttons = buttons,
    )
}
