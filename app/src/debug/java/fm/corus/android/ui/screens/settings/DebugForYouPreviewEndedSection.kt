package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import fm.corus.android.ui.screens.feed.ForYouPreviewEndedSheet

/** Local, repeatable production UI preview. No feed store or Firebase calls. */
@Composable
internal fun DebugForYouPreviewEndedSection() {
    var trial by remember { mutableStateOf<Boolean?>(null) }
    val events = remember { mutableStateListOf<String>() }
    Column {
        Text("Your Mix Preview Ended", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { events.clear(); trial = true }) { Text("Preview: Free Trial CTA") }
        TextButton(onClick = { events.clear(); trial = false }) { Text("Preview: Join Club CTA") }
        Text("Reopen as often as needed. Account preview and seen state stay unchanged. Club is simulated; analytics appear below instead of being sent.", style = MaterialTheme.typography.bodySmall)
        events.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
    }
    trial?.let { hasTrial ->
        ForYouPreviewEndedSheet(hasClubIntroTrial = hasTrial, onDismiss = { trial = null }, onClub = {},
            onTrack = { event -> events.add("${event.name} · ${event.params.toSortedMap()}") })
    }
}
