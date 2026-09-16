package fm.corus.android.ui.screens.map

import fm.corus.android.data.model.CymbalUser
import fm.corus.android.domain.CityChatPolicy
import fm.corus.android.domain.MapPlaybackOwner
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MapParityTest {
    @Test fun previewKeepsBaseMapMountedUnderDirectoryProgress() {
        val source = File("src/main/java/fm/corus/android/ui/screens/map/MapPreviewEntry.kt").readText()
        assertTrue(source.indexOf("CityMapView(") < source.indexOf("!state.directoryReady"))
        assertTrue(source.contains("CircularProgressIndicator"))
        assertFalse(source.contains("repeat(2)"))
        val mapSource = File("src/main/java/fm/corus/android/ui/screens/map/AppleCityMapView.kt").readText()
        assertTrue(mapSource.contains("webView.draw(Canvas(it))"))
        assertTrue(mapSource.contains("mapPreviewSnapshot"))
        assertTrue(mapSource.contains("ValueAnimator.areAnimatorsEnabled()"))
        assertTrue(mapSource.contains("durationMillis = if (android.animation.ValueAnimator.areAnimatorsEnabled()) 320 else 0"))
        assertTrue(mapSource.contains("val currentPayload by rememberUpdatedState(payload)"))
        assertTrue(mapSource.contains("window.CorusAppleMap&&window.CorusAppleMap.update(\$currentPayload)"))
    }
    @Test fun returningPreviewPrefersFreshCompleteDirectory() {
        val repository = File("src/main/java/fm/corus/android/ui/screens/map/MapRepository.kt").readText()
        val preview = File("src/main/java/fm/corus/android/ui/screens/map/MapPreviewViewModel.kt").readText()
        assertTrue(repository.contains("fullCitiesCache"))
        assertTrue(repository.contains("System.currentTimeMillis() - cached.first < 60_000"))
        assertTrue(preview.contains("repository.previewCities()"))
    }
    @Test fun returningPreviewRetainsItsRenderedMapKitSurface() {
        val source = File("src/main/java/fm/corus/android/ui/screens/map/AppleCityMapView.kt").readText()
        assertTrue(source.contains("if (compact) MapPreviewWebViewCache.take(previewCacheKey) else null"))
        assertTrue(source.contains("MapPreviewWebViewCache.put(previewCacheKey, webView)"))
        assertTrue(source.contains("retainedPreviewWebView ?: WebView(if (compact) context.applicationContext else context)"))
        assertTrue(source.contains("mutableStateOf(retainedPreviewWebView != null)"))
        assertFalse(source.contains("webView.draw(Canvas(it))\n                MapPreview"))
    }
    @Test fun focusedCityIsTopmostWithoutHidingNearbyPreviewClusters() {
        val html = appleMapHtml("token", compact = true, fontData = "font")
        assertTrue(html.contains("const active=city.id===window.CorusAppleMap.data.focus?.id"))
        assertTrue(html.contains("collisionMode:'none'"))
        assertTrue(html.contains("annotation.selected=active"))
        assertFalse(html.contains(".compact .city.active .label"))
        assertTrue(html.contains("if(!compact||active)b.appendChild(label)"))
        assertTrue(html.contains("interactive?0:(active?-38.5:-18)"))
        assertFalse(html.contains("annotations.forEach(function(a){map.removeAnnotation(a)}"))
        assertTrue(html.contains(".compact .city:not(.active){--cluster-opacity:.45}"))
        assertTrue(html.contains("transform:scale(.84)"))
        assertTrue(html.contains("transition:opacity .22s ease-in-out"))
        assertTrue(html.contains("transition:transform .22s ease-in-out"))
        assertTrue(html.contains("@keyframes cluster-in"))
        assertTrue(html.contains("animation:cluster-in .22s ease-out"))
        assertTrue(html.contains(".compact .city{animation:none}"))
        assertFalse(html.contains("focused-preview-in"))
    }
    @Test fun mapPreviewUsesMapKitProjectionGridAndTwelveRealCandidates() {
        val html = appleMapHtml("token", compact = true, fontData = "font")
        assertTrue(html.contains("map.convertCoordinateToPointOnPage"))
        assertTrue(html.contains("Math.floor(row.x/width*7)"))
        assertTrue(html.contains("Math.floor(row.y/height*2)"))
        assertTrue(html.contains("selected.length<14"))
        assertTrue(html.contains("insetX=34,insetY=22"))
        assertTrue(html.contains("Math.abs(row.x-chosen.x)<46&&Math.abs(row.y-chosen.y)<36"))
        assertTrue(html.contains("[horizontal,horizontal.slice().reverse()]"))
        assertTrue(html.contains("interactive?7:125"))
        assertTrue(html.contains("interactive?10:300"))
        assertTrue(html.contains("const animate=interactive&&this.lastFocus!==null"))
        assertTrue(html.contains("row.city.id===window.CorusAppleMap.data.focus?.id"))
        assertTrue(html.contains("const selected=focused?[focused]:[]"))
    }
    @Test fun settledCameraSelectsNearestCityAndNotifiesCompose() {
        fun summary(id: String, latitude: Double, longitude: Double) = MapCitySummary(
            MapCity(id, id, "", "US", latitude, longitude),
            mapOf("all" to MapFacet(1, emptyList())),
        )
        val cities = listOf(summary("brooklyn", 40.68, -73.94), summary("queens", 40.73, -73.79))
        assertEquals("queens", nearestMapCity(cities, "all", 40.72, -73.80)?.cityId)
        val html = appleMapHtml("token", compact = false, fontData = "font")
        assertTrue(html.contains("map.addEventListener('region-change-end'"))
        assertTrue(html.contains("window.CorusAndroidMap.cameraSettled"))
        assertTrue(html.contains("const focusKey=focus&&this.data.focusRevision"))
        assertFalse(html.contains("const focusKey=focus&&focus.id"))
    }
    @Test fun cityGroupsUseIosDistanceOrderWithStableFallback() {
        fun summary(id: String, latitude: Double, longitude: Double) = MapCitySummary(
            MapCity(id, id, "", "US", latitude, longitude),
            mapOf("all" to MapFacet(1, emptyList())),
        )
        val brooklyn = MapCity("brooklyn", "Brooklyn", "NY", "US", 40.68, -73.94)
        val cities = listOf(
            summary("melbourne", -37.81, 144.96),
            summary("boston", 42.36, -71.06),
            summary("queens", 40.73, -73.79),
        )
        assertEquals(listOf("queens", "boston", "melbourne"), sortedMapCitiesNear(cities, brooklyn).map { it.city.cityId })
        assertEquals(listOf("boston", "melbourne", "queens"), sortedMapCitiesNear(cities, null).map { it.city.cityId })
    }
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
    @Test fun directoryChatStatusIsRetainedWhileFreshOrRefreshing() {
        assertTrue(shouldLoadDirectoryChat(hasStatus = false, isLoading = false))
        assertFalse(shouldLoadDirectoryChat(hasStatus = true, isLoading = false))
        assertFalse(shouldLoadDirectoryChat(hasStatus = false, isLoading = true))
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
        assertTrue(MapPlaybackOwner.canAdvance("map-track"))
        assertFalse(MapPlaybackOwner.canAdvance("other-track"))
        assertFalse(MapPlaybackOwner.advance("other-track"))
        assertTrue(MapPlaybackOwner.advance("map-track")); assertEquals(1, advances)
        MapPlaybackOwner.yield(); assertTrue(abandoned); assertNull(MapPlaybackOwner.current)
    }
}
