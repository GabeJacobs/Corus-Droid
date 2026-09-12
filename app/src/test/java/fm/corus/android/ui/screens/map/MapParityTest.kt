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
