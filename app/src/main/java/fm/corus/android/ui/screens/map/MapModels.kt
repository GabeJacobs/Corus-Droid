package fm.corus.android.ui.screens.map

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.model.CymbalPost

data class MapCity(val cityId: String, val cityName: String, val regionName: String, val countryCode: String, val latitude: Double, val longitude: Double) {
    fun payload(): Map<String, Any> = mapOf("cityId" to cityId, "cityName" to cityName, "regionName" to regionName, "countryCode" to countryCode, "latitude" to latitude, "longitude" to longitude)
    companion object {
        fun decode(d: Map<String, Any?>) = MapCity(d["cityId"] as? String ?: "", d["cityName"] as? String ?: "", d["regionName"] as? String ?: "", d["countryCode"] as? String ?: "", (d["latitude"] as? Number)?.toDouble() ?: 0.0, (d["longitude"] as? Number)?.toDouble() ?: 0.0)
    }
}
/**
 * A city resident as returned by the map directory. The listening fields are
 * deliberately part of this compact projection: the backend maintains the
 * newest playable track pointer when a person posts, so Map can begin from
 * the same post the iOS session does without re-querying profile history.
 */
data class MapPerson(
    val city: MapCity,
    val user: CymbalUser,
    val updatedAt: Long = 0,
    val hasPlayableMusic: Boolean? = null,
    val latestPlayableTrackPostId: String? = null,
)
data class MapFacet(val count: Int, val previews: List<MapPerson>, val includesViewer: Boolean = false)
data class MapCitySummary(val city: MapCity, val facets: Map<String, MapFacet>, val parentCommunity: MapCity? = null, val subdivisions: List<MapCity> = emptyList())
data class MapPeoplePage(val people: List<MapPerson>, val cursor: String?, val reachedEnd: Boolean = cursor == null)
data class MapChatStatus(val threadId: String, val member: Boolean, val canJoin: Boolean, val clusterMember: Boolean = false) {
    val available: Boolean get() = member || canJoin || clusterMember
}
internal fun shouldLoadDirectoryChat(hasStatus: Boolean, isLoading: Boolean): Boolean = !hasStatus && !isLoading
data class MapPlaybackItem(val city: MapCity, val post: CymbalPost)
internal enum class MapPersonRowLayout { USERNAME_ONLY, LATEST_POST }
internal fun mapPersonRowLayout(hasPost: Boolean) = if (hasPost) MapPersonRowLayout.LATEST_POST else MapPersonRowLayout.USERNAME_ONLY
data class MapPreviewUsage(val listen: Set<String> = emptySet(), val watch: Set<String> = emptySet()) {
    fun ids(mode: String) = if (mode == "listen") listen else watch
    fun remaining(mode: String) = (10 - ids(mode).size).coerceAtLeast(0)
    fun record(mode: String, id: String) = if (mode == "listen") copy(listen = listen + id) else copy(watch = watch + id)
    fun merge(other: MapPreviewUsage) = MapPreviewUsage(listen + other.listen, watch + other.watch)
}
fun sortedMapPeople(people: List<MapPerson>) = people.groupBy { it.user.id }.values.map { rows -> rows.withIndex().maxWith(compareBy<IndexedValue<MapPerson>> { it.value.updatedAt }.thenBy { it.index }).value }.sortedWith(compareBy<MapPerson> { it.user.username.lowercase() }.thenBy { it.user.id })

/** Mirrors iOS MapPresenceStore.groups: nearest shared city first, then stable ID order. */
fun sortedMapCitiesNear(cities: List<MapCitySummary>, origin: MapCity?): List<MapCitySummary> =
    cities.sortedWith { a, b ->
        if (origin != null) {
            fun distanceSquared(city: MapCity): Double {
                val latitudeDelta = city.latitude - origin.latitude
                val longitudeDelta = city.longitude - origin.longitude
                return latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta
            }
            val distanceComparison = distanceSquared(a.city).compareTo(distanceSquared(b.city))
            if (distanceComparison != 0) return@sortedWith distanceComparison
        }
        a.city.cityId.compareTo(b.city.cityId)
    }

/** Degrees in (0, 360]. Same longitude is a full wrap so a different city
 * at that meridian is still reachable. Mirrors iOS `MapCityStep`. */
fun eastwardDegrees(from: Double, to: Double): Double {
    var delta = (to - from) % 360.0
    if (delta <= 0) delta += 360.0
    return delta
}

/** Right is east, left is west. Walks the globe instead of list order. */
fun nextMapCity(cities: List<MapCity>, currentId: String?, delta: Int): MapCity? {
    if (cities.isEmpty()) return null
    if (cities.size == 1 || delta == 0) return cities.firstOrNull { it.cityId == currentId } ?: cities.first()
    val current = cities.firstOrNull { it.cityId == currentId } ?: cities.first()
    val goingEast = delta > 0
    var best: MapCity? = null
    var bestScore = Double.POSITIVE_INFINITY
    for (city in cities) {
        if (city.cityId == current.cityId) continue
        val east = eastwardDegrees(current.longitude, city.longitude)
        val score = if (goingEast) east else 360.0 - east
        if (score < bestScore || (score == bestScore && city.cityId < (best?.cityId ?: city.cityId))) {
            bestScore = score
            best = city
        }
    }
    return best
}

fun nextMapCitySummary(cities: List<MapCitySummary>, currentId: String?, delta: Int): MapCitySummary? {
    val next = nextMapCity(cities.map { it.city }, currentId, delta) ?: return null
    return cities.first { it.city.cityId == next.cityId }
}

/**
 * How much of the map viewport is covered from the bottom by the people
 * sheet or a playback card. Measured overlay height wins so short and tall
 * devices (and expanded vs resting sheets) share the same framing math.
 */
internal fun mapBottomOcclusionFraction(
    viewportHeightPx: Int,
    overlayHeightPx: Int,
    fallback: Float,
): Float {
    if (viewportHeightPx > 0 && overlayHeightPx > 0) {
        return (overlayHeightPx.toFloat() / viewportHeightPx).coerceIn(0f, 0.95f)
    }
    return fallback.coerceIn(0f, 0.95f)
}

/**
 * Southward camera shift, as a fraction of latitude span, that places a city
 * in the middle of the map still visible between the header and the sheet.
 */
internal fun mapVisibleFocusLatitudeOffset(
    topInsetFraction: Float,
    bottomOcclusionFraction: Float,
): Double {
    val top = topInsetFraction.coerceIn(0f, 0.8f)
    val bottom = bottomOcclusionFraction.coerceIn(0f, 0.95f)
    return ((bottom - top) / 2f).toDouble()
}

fun stableMapFaces(previous: List<MapPerson>, candidates: List<MapPerson>): List<MapPerson> {
    val latest = candidates.associateBy { it.user.id }
    return (previous.mapNotNull { latest[it.user.id] } + candidates.mapNotNull { latest[it.user.id] }).distinctBy { it.user.id }.take(3)
}

fun preservingPaintedCityFaces(previous: List<MapCitySummary>, next: List<MapCitySummary>): List<MapCitySummary> {
    val oldById = previous.associateBy { it.city.cityId }
    val relocated = HashMap<String, String>()
    next.forEach { city ->
        city.facets.values.forEach { facet ->
            facet.previews.forEach { person -> relocated[person.user.id] = city.city.cityId }
        }
    }
    return next.map { city ->
        val old = oldById[city.city.cityId]
        city.copy(facets = city.facets.mapValues { (filter, facet) ->
            val kept = old?.facets?.get(filter)?.previews.orEmpty().filter { person ->
                relocated[person.user.id].let { it == null || it == city.city.cityId }
                    && person.city.cityId == city.city.cityId
            }
            facet.copy(previews = stableMapFaces(kept, kept + facet.previews))
        })
    }
}

// Only deduplicate within the current response. Cached people cannot alter a
// server summary or exclude a borough member from an explicit parent page.
class MapMembership {
    fun observe(people: List<MapPerson>) {}
    fun residents(cityId: String, people: List<MapPerson>): List<MapPerson> = sortedMapPeople(people)
    fun summaries(cities: List<MapCitySummary>): List<MapCitySummary> = cities
}
fun showMapChat(cityId: String, currentCityId: String?, status: MapChatStatus): Boolean = status.available
fun requireMapCommunityVersion(response: Map<String, Any?>) {
    check((response["communityVersion"] as? Number)?.toInt() == 1) { "Please refresh Map after the service is updated." }
}
fun removeMapViewer(state: MapScreenState, uid: String?): MapScreenState {
    if (uid == null) return state
    return state.copy(cities = state.cities.map { city -> city.copy(facets = city.facets.mapValues { (_, f) ->
        f.copy(count = (f.count - if (f.includesViewer) 1 else 0).coerceAtLeast(0), includesViewer = false, previews = f.previews.filterNot { it.user.id == uid })
    }) }, people = state.people.filterNot { it.user.id == uid }, directorySearchPeople = state.directorySearchPeople.filterNot { it.user.id == uid }, listPages = state.listPages.mapValues { (_, page) -> page.copy(people = page.people.filterNot { it.user.id == uid }) })
}
