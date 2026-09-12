package fm.corus.android.ui.screens.map

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.CymbalPost

data class MapCity(val cityId: String, val cityName: String, val regionName: String, val countryCode: String, val latitude: Double, val longitude: Double) {
    fun payload(): Map<String, Any> = mapOf("cityId" to cityId, "cityName" to cityName, "regionName" to regionName, "countryCode" to countryCode, "latitude" to latitude, "longitude" to longitude)
    companion object {
        fun decode(d: Map<String, Any?>) = MapCity(d["cityId"] as? String ?: "", d["cityName"] as? String ?: "", d["regionName"] as? String ?: "", d["countryCode"] as? String ?: "", (d["latitude"] as? Number)?.toDouble() ?: 0.0, (d["longitude"] as? Number)?.toDouble() ?: 0.0)
    }
}
data class MapPerson(val city: MapCity, val user: CymbalUser, val updatedAt: Long = 0)
data class MapFacet(val count: Int, val previews: List<MapPerson>, val includesViewer: Boolean = false)
data class MapCitySummary(val city: MapCity, val facets: Map<String, MapFacet>, val parentCommunity: MapCity? = null, val subdivisions: List<MapCity> = emptyList())
data class MapPeoplePage(val people: List<MapPerson>, val cursor: String?, val reachedEnd: Boolean = cursor == null)
data class MapChatStatus(val threadId: String, val member: Boolean, val canJoin: Boolean)
data class MapPlaybackItem(val city: MapCity, val post: CymbalPost)
data class MapPreviewUsage(val listen: Set<String> = emptySet(), val watch: Set<String> = emptySet()) {
    fun ids(mode: String) = if (mode == "listen") listen else watch
    fun remaining(mode: String) = (10 - ids(mode).size).coerceAtLeast(0)
    fun record(mode: String, id: String) = if (mode == "listen") copy(listen = listen + id) else copy(watch = watch + id)
    fun merge(other: MapPreviewUsage) = MapPreviewUsage(listen + other.listen, watch + other.watch)
}
fun sortedMapPeople(people: List<MapPerson>) = people.groupBy { it.user.id }.values.map { rows -> rows.withIndex().maxWith(compareBy<IndexedValue<MapPerson>> { it.value.updatedAt }.thenBy { it.index }).value }.sortedWith(compareBy<MapPerson> { it.user.username.lowercase() }.thenBy { it.user.id })

fun stableMapFaces(previous: List<MapPerson>, candidates: List<MapPerson>): List<MapPerson> {
    val latest = candidates.associateBy { it.user.id }
    return (previous.mapNotNull { latest[it.user.id] } + candidates.mapNotNull { latest[it.user.id] }).distinctBy { it.user.id }.take(3)
}


// Only deduplicate within the current response. Cached people cannot alter a
// server summary or exclude a borough member from an explicit parent page.
class MapMembership {
    fun observe(people: List<MapPerson>) {}
    fun residents(cityId: String, people: List<MapPerson>): List<MapPerson> = sortedMapPeople(people)
    fun summaries(cities: List<MapCitySummary>): List<MapCitySummary> = cities
}
fun showMapChat(cityId: String, currentCityId: String?, status: MapChatStatus): Boolean = status.member || status.canJoin
fun requireMapCommunityVersion(response: Map<String, Any?>) {
    check((response["communityVersion"] as? Number)?.toInt() == 1) { "Please refresh Map after the service is updated." }
}
fun removeMapViewer(state: MapScreenState, uid: String?): MapScreenState {
    if (uid == null) return state
    return state.copy(cities = state.cities.map { city -> city.copy(facets = city.facets.mapValues { (_, f) ->
        f.copy(count = (f.count - if (f.includesViewer) 1 else 0).coerceAtLeast(0), includesViewer = false, previews = f.previews.filterNot { it.user.id == uid })
    }) }, people = state.people.filterNot { it.user.id == uid }, listPages = state.listPages.mapValues { (_, page) -> page.copy(people = page.people.filterNot { it.user.id == uid }) })
}
