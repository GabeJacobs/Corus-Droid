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
        assertTrue(source.contains("MapPreviewWebViewCache.take(previewCacheKey)"))
        assertTrue(source.contains("MapPreviewWebViewCache.put(previewCacheKey, webView)"))
        assertTrue(source.contains("retainedPreviewWebView ?: WebView(context.applicationContext)"))
        assertTrue(source.contains("mutableStateOf(retainedPreviewWebView != null)"))
        assertTrue(source.contains("\"preview\" else \"full\""))
        assertFalse(source.contains("webView.loadUrl(\"about:blank\")"))
        assertFalse(source.contains("webView.draw(Canvas(it))\n                MapPreview"))
    }

    @Test fun retappingOpenCityClusterDoesNotReloadDirectory() {
        val screen = File("src/main/java/fm/corus/android/ui/screens/map/MapExploreScreen.kt").readText()
        val model = File("src/main/java/fm/corus/android/ui/screens/map/MapExploreViewModel.kt").readText()
        val skip = "mutable.value.selected?.cityId == city.cityId && mutable.value.mode == null) return"
        assertTrue(screen.contains("state.selected?.cityId == city.cityId && citySheetVisible && state.playing == null) return"))
        assertTrue(model.contains(skip))
        assertTrue(model.indexOf(skip) < model.indexOf("selected = city, people = emptyList()"))
    }

    @Test fun citySheetPaintsAboveHeaderAndStopsShortOfFullScreen() {
        val screen = File("src/main/java/fm/corus/android/ui/screens/map/MapExploreScreen.kt").readText()
        val sheet = File("src/main/java/fm/corus/android/ui/screens/map/MapCityPeopleSheet.kt").readText()
        assertTrue(screen.contains("Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(2f)"))
        assertTrue(sheet.contains("MAP_CITY_SHEET_EXPANDED_FRACTION = 0.88f"))
        assertTrue(sheet.contains("mapCitySheetExpandedOffsetPx"))
    }

    @Test fun returningCitySheetDoesNotReplayEnterAnimation() {
        val source = File("src/main/java/fm/corus/android/ui/screens/map/MapExploreScreen.kt").readText()
        assertTrue(source.contains("var citySheetVisible by remember { mutableStateOf(state.selected != null) }"))
        assertTrue(source.contains("val citySheetTransition = remember { MutableTransitionState(state.selected != null) }"))
        assertTrue(source.contains("model.savedMapTopInsetFraction"))
        assertTrue(source.contains("model.savedMapBottomOcclusionFraction"))
    }
    @Test fun focusedCityIsTopmostWithoutHidingNearbyPreviewClusters() {
        val html = appleMapHtml("token", compact = true, fontData = "font")
        assertTrue(html.contains("const active=city.id===window.CorusAppleMap.data.focus?.id"))
        assertTrue(html.contains("collisionMode:'none'"))
        assertTrue(html.contains("setAnnotationSelected(annotation,active)"))
        assertFalse(html.contains(".compact .city.active .label"))
        assertTrue(html.contains("content.append(faces,label)"))
        assertTrue(html.contains("interactive?0:(active?-10:-18)"))
        assertFalse(html.contains("annotations.forEach(function(a){map.removeAnnotation(a)}"))
        assertTrue(html.contains(".compact .city:not(.active){--cluster-opacity:.65}"))
        assertTrue(html.contains("transform:scale(.84)"))
        assertTrue(html.contains("transition:opacity .22s ease-in-out"))
        assertTrue(html.contains("transition:transform .22s ease-in-out"))
        assertTrue(html.contains("@keyframes cluster-in"))
        assertTrue(html.contains("animation:cluster-in .22s ease-out"))
        assertTrue(html.contains(".compact .city{animation:none}"))
        assertFalse(html.contains("focused-preview-in"))
    }
    @Test fun mapPreviewUsesMapKitProjectionGridAndSixteenRealCandidates() {
        val html = appleMapHtml("token", compact = true, fontData = "font")
        assertTrue(html.contains("map.convertCoordinateToPointOnPage"))
        assertTrue(html.contains("Math.floor(row.x/width*8)"))
        assertTrue(html.contains("Math.floor(row.y/height*2)"))
        assertTrue(html.contains("selected.length<16"))
        assertTrue(html.contains("insetX=24,insetY=16"))
        assertTrue(html.contains("Math.abs(row.x-chosen.x)<38&&Math.abs(row.y-chosen.y)<28"))
        assertTrue(html.contains("[horizontal,horizontal.slice().reverse()]"))
        assertTrue(html.contains("interactive?7:125"))
        assertTrue(html.contains("interactive?10:300"))
        assertTrue(html.contains("const animate=interactive&&this.lastFocus!==null"))
        assertTrue(html.contains("row.city.id===window.CorusAppleMap.data.focus?.id"))
        assertTrue(html.contains("const selected=focused?[focused]:[]"))
        assertTrue(html.contains("const list=cells.get(key)||[];list.push(row)"))
        assertTrue(html.contains("ranked.find(function(entry){return !selected.some"))
        assertTrue(html.contains("function expandedPreviewClusterIDs(locked,proposed,limit)"))
        assertTrue(html.contains("if(this.lastPreviewCamera!==cameraKey){this.lockedPreviewIds=[];this.lastPreviewCamera=cameraKey;}"))
    }
    @Test fun compactClusterDensityMatchesIosTiers() {
        val html = appleMapHtml("token", compact = true, fontData = "font")
        assertTrue(html.contains("city.count>=15?2:(city.count>=5?1:0)"))
        assertTrue(html.contains(".compact .city.density-0 .face + .face{margin-left:-12px}"))
        assertTrue(html.contains(".compact .city.density-1 .face + .face{margin-left:-10px}"))
        assertTrue(html.contains(".compact .city.density-2 .face + .face{margin-left:-7px}"))
        assertTrue(html.contains("compact?6+densityTier:7"))
        assertTrue(html.contains(".compact .city.density-1 .face{width:30px;height:30px"))
        assertTrue(html.contains(".compact .city.density-2 .face{width:32px;height:32px"))
        assertTrue(html.contains("const compactBadgeSize=20+densityTier"))
        assertTrue(html.contains(".city.density-1 .face{width:41px;height:41px"))
        assertTrue(html.contains(".city.density-2 .face{width:44px;height:44px"))
        assertTrue(html.contains("body:not(.compact) .city.density-0 .face{width:37px;height:37px"))
        assertTrue(html.contains("compact?compactBadgeSize:23+densityTier"))
    }
    @Test fun clusterTapsUseMapKitSelectAndSingleTapOnAndroid() {
        val html = appleMapHtml("token", compact = false, fontData = "font")
        val source = File("src/main/java/fm/corus/android/ui/screens/map/AppleCityMapView.kt").readText()
        assertTrue(html.contains("function emitCity("))
        assertTrue(html.contains("function hitAnnotation("))
        assertTrue(html.contains("bindAnnotationGestures(map)"))
        assertTrue(html.contains("owner.addEventListener('select'"))
        assertTrue(html.contains("owner.addEventListener('single-tap'"))
        assertTrue(html.contains("b.dataset.cityId=city.id"))
        assertTrue(source.contains("APPLE_MAP_JS_BRIDGE_TAG"))
        assertFalse(source.contains("removeJavascriptInterface(\"CorusAndroidMap\")"))
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
        assertTrue(html.contains("const offset=(bottom-top)/2"))
        assertTrue(html.contains("function onMapViewportChange("))
        assertTrue(html.contains("size.width+'x'+size.height"))
        assertFalse(html.contains("height:100vh"))
        assertFalse(html.contains("if(map&&map.region)map.region=map.region}window.addEventListener('resize',sizeMap)"))
        assertFalse(html.contains("visibleFocus?.24"))
    }

    @Test fun visibleFocusCentersCityInMapGapAboveTheSheet() {
        assertEquals(0.16, mapVisibleFocusLatitudeOffset(0.20f, 0.52f), 0.0001)
        assertEquals(0.26, mapVisibleFocusLatitudeOffset(0f, 0.52f), 0.0001)
        assertEquals(0.31, mapVisibleFocusLatitudeOffset(0.28f, 0.90f), 0.0001)
        assertEquals(0.52f, mapBottomOcclusionFraction(1000, 0, 0.52f))
        assertEquals(0.47f, mapBottomOcclusionFraction(1000, 470, 0.52f))
        val html = appleMapHtml("token", compact = false, fontData = "font")
        assertTrue(html.contains("html,body{position:fixed;inset:0}"))
        assertTrue(html.contains("function mapCssSize("))
        assertTrue(html.contains("latitudeDelta*size.width/size.height"))
        assertTrue(html.contains("focus.id+':'+focus.latitude+':'+focus.longitude"))
        assertTrue(html.contains("!geometryChanged&&this.data.playbackMode!=='listen'"))
        assertTrue(html.contains("const listenBias=this.data.playbackMode==='listen' ? .06*visibleGap : 0"))
        assertTrue(html.contains("const preserveCamera=!!this.data.preserveCameraOnFocus"))
        assertTrue(html.contains("if(!preserveCamera){const latitudeDelta="))
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

    @Test fun cityArrowsWalkEastAndWestByLongitude() {
        fun city(id: String, longitude: Double) = MapCity(id, id, "", "US", 0.0, longitude)
        val globe = listOf(city("new-york-us", -74.0), city("tokyo-jp", 139.7), city("los-angeles-us", -118.2))
        assertEquals("new-york-us", nextMapCity(globe, "los-angeles-us", 1)?.cityId)
        assertEquals("tokyo-jp", nextMapCity(globe, "new-york-us", 1)?.cityId)
        assertEquals("los-angeles-us", nextMapCity(globe, "tokyo-jp", 1)?.cityId)
        val west = listOf(city("new-york-us", -74.0), city("los-angeles-us", -118.2), city("chicago-us", -87.6))
        assertEquals("chicago-us", nextMapCity(west, "new-york-us", -1)?.cityId)
        val alpha = listOf(city("austin-us", -97.7), city("boston-us", -71.1), city("chicago-us", -87.6))
        assertEquals("austin-us", nextMapCity(alpha, "boston-us", 1)?.cityId)
        assertEquals("chicago-us", nextMapCity(alpha, "boston-us", -1)?.cityId)
    }
    @Test fun cityFacesRetainTheirSlotsAcrossPageReordering() {
        val city = MapCity("c", "City", "", "US", 0.0, 0.0)
        fun person(id: String, name: String = id) = MapPerson(city, CymbalUser(id, name, name))
        val faces = stableMapFaces(listOf(person("a"), person("b"), person("c")), listOf(person("d"),person("b", "updated"),person("a"),person("e")))
        assertEquals(listOf("a","b","d"), faces.map { it.user.id })
        assertEquals("updated", faces[1].user.displayName)
    }
    @Test fun richerDirectoryKeepsPaintedFacesAndAddsNewCities() {
        val nyc = MapCity("new-york-us", "New York", "NY", "US", 40.7, -74.0)
        val london = MapCity("london-gb", "London", "", "GB", 51.5, -0.1)
        fun person(city: MapCity, id: String) = MapPerson(city, CymbalUser(id, id, id))
        fun summary(city: MapCity, vararg ids: String) = MapCitySummary(
            city,
            mapOf("all" to MapFacet(ids.size, ids.map { person(city, it) })),
        )
        val kept = preservingPaintedCityFaces(
            listOf(summary(nyc, "a", "b", "c")),
            listOf(summary(nyc, "d", "b", "c"), summary(london, "e")),
        )
        assertEquals(listOf("a", "b", "c"), kept.first { it.city.cityId == "new-york-us" }.facets.getValue("all").previews.map { it.user.id })
        assertEquals(listOf("london-gb"), kept.filter { it.city.cityId == "london-gb" }.map { it.city.cityId })
    }
    @Test fun viewerFaceIsFirstAndPaintedAboveTheirCluster() {
        val city = MapCity("c", "City", "", "US", 0.0, 0.0)
        fun person(id: String) = MapPerson(city, CymbalUser(id, id, id))
        assertEquals(
            listOf("viewer", "a", "b"),
            mapClusterFaces(listOf(person("a"), person("b"), person("viewer")), "viewer").map { it.user.id },
        )
        val html = appleMapHtml("token", compact = true, fontData = "font")
        assertTrue(html.contains(".face:first-child{z-index:3}"))
        assertTrue(html.contains(".count{position:absolute;right:-15px;top:-9px;z-index:4"))
    }
    @Test fun listeningAuthorIsIncludedEvenWhenAbsentFromCityPreview() {
        val city = MapCity("c", "City", "", "US", 0.0, 0.0)
        fun person(id: String) = MapPerson(city, CymbalUser(id, id, id))
        val faces = mapFocusedFaces(listOf(person("a"), person("b"), person("c")), listOf(person("playing")), city.cityId, "playing")
        assertEquals(listOf("playing", "a", "b"), faces.map { it.user.id })
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
        assertTrue(showMapChat("old", "new", MapChatStatus("chat", member = false, canJoin = false, clusterMember = true)))
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
    @Test fun mapExplainerCopyIsLocalizedThroughParityCatalog() {
        val explainer = File("src/main/java/fm/corus/android/ui/screens/map/MapExploreScreen.kt").readText()
        val catalog = File("src/main/java/fm/corus/android/ui/components/ParityCopy.kt").readText()
        listOf(
            "Explore the Corus Map",
            "Find people by city",
            "Message, meet up, or go to concerts together.",
            "Share your city, not your exact location.",
            "People can find you in the city you choose.",
            "No one sees you until you choose.",
            "Change this anytime.",
            "Who can see your city",
            "Everyone",
            "Anyone on Corus can see your city.",
            "People I follow",
            "Only accounts you follow.",
            "No one",
            "Don’t share. You can still explore.",
        ).forEach { key ->
            assertTrue(key, explainer.contains("\"$key\"") || explainer.contains("parityCopy(\"$key\")"))
            assertTrue(key, catalog.contains("\"$key\""))
        }
        assertFalse(explainer.contains("Only accounts you follow can see your city."))
        assertFalse(explainer.contains("Stay private while you explore the map."))
    }

    @Test fun mapFeatureCopyLiteralsAreInParityCatalog() {
        val catalog = File("src/main/java/fm/corus/android/ui/components/ParityCopy.kt").readText()
        val files = File("src/main/java/fm/corus/android/ui/screens/map").listFiles().orEmpty().toList() +
            File("src/main/java/fm/corus/android/ui/screens/profile/EditProfileCitySection.kt")
        val pattern = Regex("""parityCopy\("((?:\\.|[^"\\])*)"\)""")
        files.filter { it.extension == "kt" }.forEach { file ->
            pattern.findAll(file.readText()).forEach { match ->
                val key = match.groupValues[1]
                assertTrue("${file.name}: $key", catalog.contains("\"$key\""))
            }
        }
    }

    @Test fun ownCityLocatorSitsInTheListenWatchRow() {
        val source = File("src/main/java/fm/corus/android/ui/screens/map/MapExploreScreen.kt").readText()
        val listen = source.indexOf("MapGlassModeButton(\"Listen\"")
        val watch = source.indexOf("MapGlassModeButton(\"Watch\"")
        val locate = source.indexOf("MapGlassLocateButton(")
        assertTrue(listen >= 0 && watch > listen && locate > watch)
        val row = source.substring(listen, locate)
        assertFalse(row.contains("Alignment.BottomEnd"))
        assertTrue(row.contains("state.ownCity?.takeIf { state.ownAudience != \"off\" }"))
        assertTrue(source.contains("Icons.Outlined.NearMe"))
        assertFalse(source.contains("Icons.Default.MyLocation"))
    }

    @Test fun citySheetChatButtonMatchesListenWithIcon() {
        val source = File("src/main/java/fm/corus/android/ui/screens/map/MapExploreScreen.kt").readText()
        assertTrue(source.contains("Icons.Outlined.ChatBubbleOutline"))
        assertTrue(source.contains("parityCopy(if (chat.member) \"Open chat\" else \"Join chat\")"))
        assertFalse(source.contains("R.string.map_join_chat"))
        assertFalse(source.contains("R.string.map_open_chat"))
    }
}
