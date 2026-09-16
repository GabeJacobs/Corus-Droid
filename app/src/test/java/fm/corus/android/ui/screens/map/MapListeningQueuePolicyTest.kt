package fm.corus.android.ui.screens.map

import org.junit.Assert.*
import org.junit.Test

class MapListeningQueuePolicyTest {
    private val brooklynOne = MapListeningQueueCandidate("brooklyn", "brooklyn-1")
    private val brooklynTwo = MapListeningQueueCandidate("brooklyn", "brooklyn-2")
    private val melbourne = MapListeningQueueCandidate("melbourne", "melbourne-1")

    @Test
    fun `broad playback interleaves cities instead of weighting large cities first`() {
        val candidates = (1..6).map { MapListeningQueueCandidate("new-york", "ny-$it") } +
            MapListeningQueueCandidate("london", "london-1") +
            MapListeningQueueCandidate("lagos", "lagos-1")

        val ordered = mapPlaybackInterleavedByCity(candidates, MapListeningQueueCandidate::cityId)

        assertEquals(3, ordered.take(3).map { it.cityId }.toSet().size)
        assertEquals(candidates.map { it.postId }.toSet(), ordered.map { it.postId }.toSet())
    }
    private val pool = listOf(brooklynOne, brooklynTwo, melbourne)

    @Test fun anywhereContinuesAcrossCitiesUntilEveryPostIsHeard() {
        assertEquals(pool, mapListeningUnheardCandidates(pool, null, emptySet()))
        assertEquals(
            listOf(brooklynTwo, melbourne),
            mapListeningUnheardCandidates(pool, null, setOf(brooklynOne.postId)),
        )
        assertEquals(
            listOf(melbourne),
            mapListeningUnheardCandidates(pool, null, setOf(brooklynOne.postId, brooklynTwo.postId)),
        )
    }

    @Test fun citySelectionNeverLeaksIntoAnotherCity() {
        assertEquals(
            listOf(brooklynTwo),
            mapListeningUnheardCandidates(pool, "brooklyn", setOf(brooklynOne.postId)),
        )
        assertTrue(mapPlaybackLocationIncludes(MapCity("brooklyn", "Brooklyn", "NY", "US", 0.0, 0.0), "brooklyn", emptySet()))
        assertFalse(mapPlaybackLocationIncludes(MapCity("melbourne", "Melbourne", "VIC", "AU", 0.0, 0.0), "brooklyn", emptySet()))
    }

    @Test fun trueExhaustionRequiresEveryInScopePostToBeHeard() {
        assertTrue(mapListeningUnheardCandidates(pool, null, pool.map { it.postId }.toSet()).isEmpty())
        assertTrue(mapListeningUnheardCandidates(pool, "brooklyn", setOf(brooklynOne.postId, brooklynTwo.postId)).isEmpty())
        assertFalse(mapListeningUnheardCandidates(pool, null, setOf(brooklynOne.postId, brooklynTwo.postId)).isEmpty())
    }

    @Test fun anywhereSeedsAlreadyLoadedPeopleAcrossCitiesWhileCityUsesItsSheet() {
        val brooklyn = MapCity("brooklyn", "Brooklyn", "NY", "US", 0.0, 0.0)
        val melbourneCity = MapCity("melbourne", "Melbourne", "VIC", "AU", 0.0, 0.0)
        fun person(id: String, city: MapCity) = MapPerson(city, fm.corus.android.data.model.CymbalUser(id, id, id))
        val brooklynPreview = person("brooklyn-user", brooklyn)
        val melbournePreview = person("melbourne-user", melbourneCity)
        val sheetOnly = person("sheet-user", brooklyn)
        val cities = listOf(
            MapCitySummary(brooklyn, mapOf("all" to MapFacet(1, listOf(brooklynPreview)))),
            MapCitySummary(melbourneCity, mapOf("all" to MapFacet(1, listOf(melbournePreview)))),
        )
        assertEquals(
            setOf("brooklyn-user", "melbourne-user"),
            mapInitialPlaybackCandidates(cities, "all", null, emptyList(), "viewer", "listen").map { it.second.user.id }.toSet(),
        )
        assertEquals(
            listOf("sheet-user"),
            mapInitialPlaybackCandidates(cities.take(1), "all", "brooklyn", listOf(sheetOnly), "viewer", "listen").map { it.second.user.id },
        )
    }
}
