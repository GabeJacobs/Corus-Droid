package fm.corus.android.ui.screens.map

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.CymbalPost

data class MapCity(val cityId: String, val cityName: String, val regionName: String, val countryCode: String, val latitude: Double, val longitude: Double) {
    fun payload(): Map<String, Any> = mapOf("cityId" to cityId, "cityName" to cityName, "regionName" to regionName, "countryCode" to countryCode, "latitude" to latitude, "longitude" to longitude)
    companion object {
        fun decode(d: Map<String, Any?>) = MapCity(d["cityId"] as? String ?: "", d["cityName"] as? String ?: "", d["regionName"] as? String ?: "", d["countryCode"] as? String ?: "", (d["latitude"] as? Number)?.toDouble() ?: 0.0, (d["longitude"] as? Number)?.toDouble() ?: 0.0)
    }
}
data class MapPerson(val city: MapCity, val user: CymbalUser)
data class MapFacet(val count: Int, val previews: List<MapPerson>)
data class MapCitySummary(val city: MapCity, val facets: Map<String, MapFacet>)
data class MapPeoplePage(val people: List<MapPerson>, val cursor: String?)
data class MapChatStatus(val threadId: String, val member: Boolean, val canJoin: Boolean)
data class MapPlaybackItem(val city: MapCity, val post: CymbalPost)
data class MapPreviewUsage(val listen: Set<String> = emptySet(), val watch: Set<String> = emptySet()) {
    fun ids(mode: String) = if (mode == "listen") listen else watch
    fun remaining(mode: String) = (10 - ids(mode).size).coerceAtLeast(0)
    fun record(mode: String, id: String) = if (mode == "listen") copy(listen = listen + id) else copy(watch = watch + id)
    fun merge(other: MapPreviewUsage) = MapPreviewUsage(listen + other.listen, watch + other.watch)
}
fun sortedMapPeople(people: List<MapPerson>) = people.distinctBy { it.user.id }.sortedWith(compareBy<MapPerson> { it.user.username.lowercase() }.thenBy { it.user.id })

fun stableMapFaces(previous: List<MapPerson>, candidates: List<MapPerson>): List<MapPerson> {
    val latest = candidates.associateBy { it.user.id }
    return (previous.mapNotNull { latest[it.user.id] } + candidates.mapNotNull { latest[it.user.id] }).distinctBy { it.user.id }.take(3)
}
