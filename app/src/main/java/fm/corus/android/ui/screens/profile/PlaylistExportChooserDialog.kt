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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * "Quick vs export all" chooser for the profile playlist, shown when the source
 * has more than 75 eligible songs. Built as a custom [Dialog] rather than a
 * Material [androidx.compose.material3.AlertDialog] because the latter's
 * confirm/dismiss slots only lay out one or two buttons in a bottom row — three
 * stacked actions there render centered and over-spaced. Here the actions stack
 * cleanly, end-aligned (the Material idiom for stacked dialog buttons), with the
 * Spotify/SoundCloud caveat folded into the message so we never stack popups.
 */
@Composable
fun PlaylistExportChooserDialog(
    count: Int,
    caveat: String,
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
                    text = "Generate a playlist?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Make a quick 75-song playlist, or export all $count songs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                // Service caveat (e.g. "Deezer can't build playlists…") rendered as
                // its own emphasized line so it reads as a clear notice, not a
                // trailing sentence on the choice prompt.
                if (caveat.isNotBlank()) {
                    Text(
                        text = caveat,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TextButton(onClick = onQuick) { Text("Quick playlist · 75 songs") }
                    TextButton(onClick = onAll) { Text("All $count songs") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}
