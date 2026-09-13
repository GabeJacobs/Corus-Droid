package fm.corus.android.ui.screens.map

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fm.corus.android.R

data class MapCameraPosition(val latitude: Double, val longitude: Double, val zoom: Double)

@Composable
fun CityMapView(cities: List<MapCitySummary>, filter: String, selected: MapCity?, playing: MapCity?, modifier: Modifier = Modifier, compact: Boolean = false, sessionKey: Int = 0, browsing: MapCity? = null, anchor: MapCity? = null, initialCamera: MapCameraPosition? = null, onCameraChanged: (MapCameraPosition) -> Unit = {}, showArtwork: Boolean = true, playbackMode: String? = null, loadLatest: (suspend (List<String>) -> Map<String, fm.corus.android.data.model.CymbalPost?>)? = null, mapKitToken: String = "", focusOverride: MapCity? = null, focusRevision: Int = 0, citySheetOpen: Boolean = false, mapTopInsetFraction: Float = 0f, citySheetHeightFraction: Float = .52f, onCity: (MapCity) -> Unit) {
    if (mapKitToken.isBlank()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.map_unavailable))
        }
        return
    }
    AppleCityMapView(
        cities = cities,
        filter = filter,
        compact = compact,
        token = mapKitToken,
        focus = focusOverride ?: playing ?: selected ?: browsing ?: anchor,
        modifier = modifier,
        onCity = onCity,
        focusRevision = focusRevision,
        citySheetOpen = citySheetOpen,
        mapTopInsetFraction = mapTopInsetFraction,
        citySheetHeightFraction = citySheetHeightFraction,
        showArtwork = showArtwork && !compact,
        loadLatest = loadLatest,
    )
}
