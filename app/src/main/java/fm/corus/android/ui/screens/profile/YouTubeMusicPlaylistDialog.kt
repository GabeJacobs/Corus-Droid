package fm.corus.android.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Explainer shown when the viewer's music service is YouTube Music, which has no
 * playlist export path yet. It leads with the limitation, then offers a Spotify
 * playlist instead (mirroring the feed's Spotify-fallback flow). When
 * [offersFullExport] is true it keeps the quick-vs-all choice, Spotify-labelled so
 * the destination is clear; otherwise it collapses to a single generate action.
 *
 * Built as a custom [Dialog] for the same reason as [PlaylistExportChooserDialog]:
 * three stacked, end-aligned actions render cleanly here where a Material
 * AlertDialog's confirm/dismiss button row does not.
 */
@Composable
fun YouTubeMusicPlaylistDialog(
    count: Int,
    offersFullExport: Boolean,
    onQuick: () -> Unit,
    onAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                Text(
                    text = "YouTube Music playlists coming soon",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Playlist export isn't available for YouTube Music yet, but it works with other services. " +
                        if (offersFullExport) {
                            "Corus can make a Spotify playlist instead."
                        } else {
                            "Want to make a Spotify playlist instead?"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (offersFullExport) {
                        TextButton(onClick = onQuick) { Text("Quick Spotify playlist · 75 songs") }
                        TextButton(onClick = onAll) { Text("All $count songs on Spotify") }
                    } else {
                        TextButton(onClick = onQuick) { Text("Generate Spotify Playlist") }
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}
