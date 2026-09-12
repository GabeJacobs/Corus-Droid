package fm.corus.android.ui.screens.map

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.CityChatPolicy
import fm.corus.android.domain.MapPlaybackOwner
import org.junit.Assert.*
import org.junit.Test

class MapParityTest {
    @Test fun cityFacesRetainTheirSlotsAcrossPageReordering() {
        val city = MapCity("c", "City", "", "US", 0.0, 0.0)
        fun person(id: String, name: String = id) = MapPerson(city, CymbalUser(id, name, name))
        val faces = stableMapFaces(listOf(person("a"), person("b"), person("c")), listOf(person("d"),person("b", "updated"),person("a"),person("e")))
        assertEquals(listOf("a","b","d"), faces.map { it.user.id })
        assertEquals("updated", faces[1].user.displayName)
    }
    @Test fun movedPersonReplacesTheirOldLocation() {
        val old = MapCity("old", "Old", "", "AU", 0.0, 0.0)
        val new = old.copy(cityId = "new")
        val user = CymbalUser("train", "train", "Train")
        assertEquals("new", sortedMapPeople(listOf(MapPerson(old, user), MapPerson(new, user))).single().city.cityId)
    }
    @Test fun serverCountsAndParentPagesIgnoreCachedMoves() {
        val oldCity = MapCity("old", "Old", "", "AU", 0.0, 0.0)
        val old = MapPerson(oldCity, CymbalUser("train", "train", "Train"), 1)
        val moved = old.copy(city = oldCity.copy(cityId = "new"), updatedAt = 2)
        val membership = MapMembership()
        membership.observe(listOf(moved, old))
        val summaries = membership.summaries(listOf(old, moved).map { MapCitySummary(it.city, mapOf("all" to MapFacet(1, listOf(it)))) })
        assertEquals(listOf(1,1), summaries.map { it.facets.getValue("all").count })
        assertEquals(listOf(old), membership.residents("parent", listOf(old)))
        assertEquals(listOf(moved), membership.residents("new", listOf(moved, old)))
        assertEquals("new", sortedMapPeople(listOf(moved, old)).single().city.cityId)
    }
    @Test fun backendChatStatusPreservesTravelingMembers() {
        for (member in listOf(false, true)) {
            val status = MapChatStatus("chat", member, true)
            assertTrue(showMapChat("old", "new", status))
            assertTrue(showMapChat("old", null, status))
            assertTrue(showMapChat("new", "new", status))
        }
    }
    @Test fun sharingOffRemovesOwnerFromEveryLoadedSurfaceOnlyOnce() {
        val city = MapCity("own", "Own", "", "US", 1.0, 2.0)
        val person = MapPerson(city, CymbalUser("viewer", "viewer", "Viewer"))
        val state = MapScreenState(cities = listOf(MapCitySummary(city, mapOf("all" to MapFacet(2, listOf(person), true)))), people = listOf(person), listPages = mapOf("own" to MapPeoplePage(listOf(person), null)))
        val cleaned = removeMapViewer(state, "viewer")
        assertEquals(1, cleaned.cities.single().facets.getValue("all").count)
        assertTrue(cleaned.people.isEmpty()); assertTrue(cleaned.listPages.getValue("own").people.isEmpty())
        assertEquals(cleaned, removeMapViewer(cleaned, "viewer"))
    }
    @Test fun oldBackendResponsesAreRejected() {
        requireMapCommunityVersion(mapOf("communityVersion" to 1))
        try { requireMapCommunityVersion(emptyMap()); fail("Accepted an old backend") } catch (_: IllegalStateException) {}
    }
    @Test fun lifetimeAllowancesUnionAcrossDevicesAndMedia() {
        val usage = MapPreviewUsage(setOf("a", "b"), setOf("film")).merge(MapPreviewUsage(setOf("b", "c")))
        assertEquals(7, usage.remaining("listen"))
        assertEquals(9, usage.remaining("watch"))
        assertEquals(usage, usage.record("listen", "a"))
        assertEquals(0, MapPreviewUsage((0..12).map(Int::toString).toSet()).remaining("listen"))
    }
    @Test fun directoryOrderIsStableAcrossPageOverlap() {
        val city = MapCity("c", "City", "", "US", 0.0, 0.0)
        fun person(id: String, name: String) = MapPerson(city, CymbalUser(id, name, name))
        assertEquals(listOf("1", "3", "2"), sortedMapPeople(listOf(person("2", "Bob"), person("3", "alice"), person("1", "Alice"), person("2", "Bob"))).map { it.user.id })
    }
    @Test fun cityPermissionsDoNotTurnEveryGroupIntoACityRoom() {
        assertTrue(CityChatPolicy.canEditMessages(null))
        assertTrue(CityChatPolicy.canAddMembers(null))
        assertFalse(CityChatPolicy.canEditMessages("city"))
        assertFalse(CityChatPolicy.canAddMembers("city"))
        assertFalse(CityChatPolicy.canDeleteMessages("city", "member"))
        assertTrue(CityChatPolicy.canDeleteMessages("city", CityChatPolicy.OWNER))
        assertFalse(CityChatPolicy.canLeave("city", CityChatPolicy.OWNER))
    }
    @Test fun transportOwnershipDoesNotInterceptAnotherTrack() {
        var advances = 0; var abandoned = false
        MapPlaybackOwner.current = MapPlaybackOwner.Owner("map-track", { advances++ }, {}, { abandoned = true })
        assertFalse(MapPlaybackOwner.advance("other-track"))
        assertTrue(MapPlaybackOwner.advance("map-track")); assertEquals(1, advances)
        MapPlaybackOwner.yield(); assertTrue(abandoned); assertNull(MapPlaybackOwner.current)
    }
}
