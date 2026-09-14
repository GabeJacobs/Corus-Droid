package fm.corus.android.ui.screens.map

import fm.corus.android.data.model.CymbalUser
import org.junit.Assert.*
import org.junit.Test

class MapClusterArtworkTest {
    private val ids = listOf("a", "b", "c", "d")

    @Test fun fanSupportsZeroOneTwoAndThreeRealArtworks() {
        assertEquals(0, mapClusterArtwork(ids, emptyMap()).size)
        assertEquals(listOf("a"), mapClusterArtwork(ids, mapOf("a" to "1")).keys.toList())
        assertEquals(listOf("a", "b"), mapClusterArtwork(ids, mapOf("a" to "1", "b" to "2")).keys.toList())
        assertEquals(listOf("a", "b", "c"), mapClusterArtwork(ids, mapOf("a" to "1", "b" to "2", "c" to "3")).keys.toList())
    }

    @Test fun fanCapsMoreThanThreeAndNeverSynthesizesEmptyArt() {
        val art = mapClusterArtwork(ids, mapOf("a" to "1", "b" to "", "c" to "3", "d" to "4"))
        assertEquals(mapOf("a" to "1", "c" to "3", "d" to "4"), art)
    }

    @Test fun selectedSheetAndEntranceMatchIosContracts() {
        val html = appleMapHtml("token", compact = false, fontData = "font")
        assertFalse(Regex("markerKey=JSON\\.stringify\\(\\{[^}]*citySheetOpen").containsMatchIn(html))
        assertTrue(html.contains("art.className='art art-'+index"))
        assertTrue(html.contains("setTimeout(function(){fan.classList.add('raised')},35)"))
        assertTrue(html.contains("const markerOffset=sheetFocused?-62"))
    }

    @Test fun selectedArtworkIsOneAndAQuarterAvatarDiametersWithoutMovingCenterline() {
        assertEquals(1.25f, MAP_CLUSTER_ARTWORK_AVATAR_RATIO)
        assertEquals(MAP_CLUSTER_AVATAR_SIZE_PX * MAP_CLUSTER_ARTWORK_AVATAR_RATIO, MAP_CLUSTER_ARTWORK_SIZE_PX)
        assertEquals(MAP_CLUSTER_ART_FAN_WIDTH_PX / 2f, MAP_CLUSTER_ARTWORK_LEFT_PX + MAP_CLUSTER_ARTWORK_SIZE_PX / 2f)
        val html = appleMapHtml("token", compact = false, fontData = "font")
        assertTrue(html.contains("width:${MAP_CLUSTER_ARTWORK_SIZE_PX}px;height:${MAP_CLUSTER_ARTWORK_SIZE_PX}px"))
        assertFalse(html.contains("width:60px;height:60px"))
    }

    @Test fun selectedSheetPeopleCanSupplyArtworkCandidatesMissingFromPreview() {
        val city = MapCity("brooklyn", "Brooklyn", "NY", "US", 0.0, 0.0)
        fun person(id: String) = MapPerson(city, CymbalUser(id, id, id))
        assertEquals(listOf("empty-preview", "has-post"), mapFocusedFaces(listOf(person("empty-preview")), listOf(person("has-post")), city.cityId, null).map { it.user.id })
        assertTrue(mapFocusedFaces(emptyList(), listOf(person("other").copy(city = city.copy(cityId = "other"))), city.cityId, null).isEmpty())
    }
}
