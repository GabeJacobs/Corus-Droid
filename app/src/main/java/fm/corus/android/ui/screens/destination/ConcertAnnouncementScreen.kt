package fm.corus.android.ui.screens.destination

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.functions.FirebaseFunctions
import fm.corus.android.R
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont
import fm.corus.android.ui.util.DateUtils
import kotlinx.coroutines.tasks.await

@Composable
fun ConcertAnnouncementScreen(notificationId: String, onBack: () -> Unit, onConcert: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var shows by remember(notificationId) { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    var loading by remember(notificationId) { mutableStateOf(true) }
    var failed by remember(notificationId) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(notificationId, attempt) {
        loading = true; failed = false
        try {
            val data = FirebaseFunctions.getInstance("us-central1").getHttpsCallable("getConcertAnnouncement")
                .call(mapOf("id" to notificationId)).await().getData() as? Map<*, *>
            shows = (data?.get("shows") as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
        } catch (_: Exception) { failed = true }
        loading = false
    }
    Surface(color = CorusColors.Background) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(fm.corus.android.localization.CorusStrings.common_back)) }
            Text(stringResource(R.string.corus_discovery_concert_list), style = CorusFont.screenTitle, color = CorusColors.Text)
            if (loading) CircularProgressIndicator()
            else if (failed) TextButton(onClick = { attempt++ }) { Text(stringResource(fm.corus.android.localization.CorusStrings.common_retry)) }
            else LazyColumn {
                items(shows) { show ->
                    Column(Modifier.fillMaxWidth().clickable { (show["id"] as? String)?.let(onConcert) }.padding(vertical = 16.dp)) {
                        Text(show["venue"] as? String ?: "", style = CorusFont.body, color = CorusColors.Text)
                        Text(DateUtils.localizedConcertDate(show["date"] as? String ?: "", locale), style = CorusFont.caption, color = CorusColors.Secondary)
                    }
                }
            }
        }
    }
}
