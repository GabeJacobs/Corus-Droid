package fm.corus.android.ui.screens.map

import fm.corus.android.ui.components.parityCopy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fm.corus.android.ui.theme.CorusColors
import fm.corus.android.ui.theme.CorusFont

@Composable
fun MapPreviewEntry(onOpen: () -> Unit, model: MapPreviewViewModel = hiltViewModel()) {
    val revision by model.remote.revision.collectAsState()
    val mapKitToken = model.remote.mapKitJsToken
    if (!model.enabled) return
    val state by model.state.collectAsState()
    val focus = mapFocusCity(state.cities, "all", state.ownCity)
    LaunchedEffect(Unit) { model.refresh() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Public, null, tint = CorusColors.Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(parityCopy("Corus Map").uppercase(), Modifier.weight(1f), style = CorusFont.sectionHeader, color = CorusColors.Secondary)
            Text(parityCopy("View Map"), color = CorusColors.Accent, style = CorusFont.caption)
        }
        Box(Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(16.dp))) {
            CityMapView(state.cities, "all", null, null, anchor = if (state.ownPresenceReady) focus?.city else null, modifier = Modifier.fillMaxSize(), compact = true, mapKitToken = mapKitToken, onLoadEvent = model::reportMapLoad, onCity = {})
            if (!state.directoryReady) CircularProgressIndicator(Modifier.align(Alignment.Center))
            Box(Modifier.matchParentSize().clickable(onClick = onOpen))
        }
    }
}
