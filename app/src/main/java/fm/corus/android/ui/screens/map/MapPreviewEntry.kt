package fm.corus.android.ui.screens.map

import fm.corus.android.ui.components.parityCopy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

@Composable
fun MapPreviewEntry(onOpen: () -> Unit, model: MapExploreViewModel = hiltViewModel()) {
    val revision by model.remote.revision.collectAsState()
    if (!model.enabled) return
    val state by model.state.collectAsState()
    val focus = mapFocusCity(state.cities, "all", state.ownCity)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Text(parityCopy("Corus Map"), Modifier.weight(1f), style = CorusFont.sectionHeader, color = CorusColors.Secondary)
            Text(parityCopy("View Map"), color = CorusColors.Accent, style = CorusFont.caption)
        }
        Box(Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(16.dp))) {
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else CityMapView(state.cities, "all", null, null, anchor = if (state.ownPresenceReady) focus?.city else null, modifier = Modifier.fillMaxSize(), compact = true, onCity = {})
            Box(Modifier.matchParentSize().clickable(onClick = onOpen))
        }
    }
}
