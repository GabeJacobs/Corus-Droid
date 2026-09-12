package fm.corus.android.ui.screens.map

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import fm.corus.android.R

@Composable
fun MapCommunityPicker(cities: List<MapCitySummary>, selected: String?, onSelect: (String?) -> Unit) {
    if (selected == null && cities.none { it.parentCommunity != null || it.subdivisions.isNotEmpty() }) return
    var expanded by remember { mutableStateOf(false) }
    val options = cities.flatMap { listOf(it.city) + listOfNotNull(it.parentCommunity) + it.subdivisions }.distinctBy { it.cityId }
    Box {
        TextButton(onClick = { expanded = true }) { Text(options.firstOrNull { it.cityId == selected }?.cityName ?: stringResource(R.string.map_all_communities)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.map_all_communities)) }, onClick = { expanded = false; onSelect(null) })
            options.forEach { city -> DropdownMenuItem(text = { Text(city.cityName) }, onClick = { expanded = false; onSelect(city.cityId) }) }
        }
    }
}
