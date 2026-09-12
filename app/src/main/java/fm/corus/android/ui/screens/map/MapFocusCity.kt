package fm.corus.android.ui.screens.map

/** Shared-city first, nearest to shared origin second, densest otherwise. Never requests location. */
fun mapFocusCity(cities: List<MapCitySummary>, filter: String, origin: MapCity?): MapCitySummary? {
    val visible = cities.filter { (it.facets[filter]?.count ?: 0) > 0 }
    visible.firstOrNull { it.city.cityId == origin?.cityId }?.let { return it }
    if (origin != null) return visible.minByOrNull {
        val longitude = ((it.city.longitude - origin.longitude + 540.0) % 360.0) - 180.0
        val x = longitude * kotlin.math.cos(Math.toRadians(origin.latitude))
        val y = it.city.latitude - origin.latitude
        x*x + y*y
    }
    return visible.maxByOrNull { it.facets[filter]?.count ?: 0 }
}
