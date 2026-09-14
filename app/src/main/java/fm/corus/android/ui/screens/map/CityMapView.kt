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
fun CityMapView(cities: List<MapCitySummary>, filter: String, selected: MapCity?, playing: MapCity?, modifier: Modifier = Modifier, compact: Boolean = false, sessionKey: Int = 0, browsing: MapCity? = null, anchor: MapCity? = null, initialCamera: MapCameraPosition? = null, onCameraChanged: (MapCameraPosition) -> Unit = {}, showArtwork: Boolean = true, playbackMode: String? = null, playingUserId: String? = null, loadLatest: (suspend (List<String>) -> Map<String, fm.corus.android.data.model.CymbalPost?>)? = null, mapKitToken: String = "", focusOverride: MapCity? = null, focusRevision: Int = 0, focusInVisibleMap: Boolean = false, mapTopInsetFraction: Float = 0f, mapBottomOcclusionFraction: Float = .52f, citySheetOpen: Boolean = false, onCity: (MapCity) -> Unit) {
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
        focusInVisibleMap = focusInVisibleMap,
        citySheetOpen = citySheetOpen,
        mapTopInsetFraction = mapTopInsetFraction,
        mapBottomOcclusionFraction = mapBottomOcclusionFraction,
        showArtwork = showArtwork && !compact,
        playbackMode = playbackMode,
        playingUserId = playingUserId,
        loadLatest = loadLatest,
    )
}
