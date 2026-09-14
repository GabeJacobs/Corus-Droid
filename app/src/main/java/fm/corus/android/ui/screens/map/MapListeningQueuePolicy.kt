package fm.corus.android.ui.screens.map

/** Location scope is independent of the city that happens to be playing now. */
internal fun mapPlaybackLocationIncludes(
    city: MapCity,
    selectedCityId: String?,
    countryCodes: Set<String>,
): Boolean =
    (selectedCityId == null || city.cityId == selectedCityId) &&
        (countryCodes.isEmpty() || city.countryCode in countryCodes)

internal data class MapListeningQueueCandidate(val cityId: String, val postId: String)

/** Mirrors iOS playedPostIds exhaustion across the complete scoped pool. */
internal fun mapListeningUnheardCandidates(
    candidates: List<MapListeningQueueCandidate>,
    selectedCityId: String?,
    playedPostIds: Set<String>,
): List<MapListeningQueueCandidate> = candidates.filter {
    (selectedCityId == null || it.cityId == selectedCityId) && it.postId !in playedPostIds
}.distinctBy { it.postId }

/** iOS seeds broad playback from every already-loaded in-scope preview. */
internal fun mapInitialPlaybackCandidates(
    cities: List<MapCitySummary>,
    filter: String,
    selectedCityId: String?,
    selectedCityPeople: List<MapPerson>,
    viewerId: String?,
    mode: String,
): List<Pair<MapCity, MapPerson>> {
    val people = if (selectedCityId != null) selectedCityPeople
    else cities.flatMap { it.facets[filter]?.previews.orEmpty() }
    return people.asSequence()
        .filter { mode == "listen" && (it.user.id != viewerId || selectedCityId != null) || mode != "listen" && it.user.id != viewerId }
        .distinctBy { it.user.id }
        .map { person ->
            val city = cities.firstOrNull { it.city.cityId == person.city.cityId }?.city ?: person.city
            city to person
        }
        .toList()
}
