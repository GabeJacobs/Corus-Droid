package fm.corus.android.ui.screens.map

import androidx.lifecycle.ViewModelStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import fm.corus.android.data.model.CymbalUser
import fm.corus.android.data.repository.SubscriptionRepository
import fm.corus.android.data.repository.UserRepository
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapExploreStartupTest {
    private class Fixture(val failTaste: Boolean = false) {
        val store = ViewModelStore()
        val started = mutableSetOf<String>()
        val profile = CymbalUser("viewer", "viewer", "Viewer")
        var cachedTaste: MapRepository.TasteMatchIdsCache? = null
        val city = MapCity("london", "London", "", "GB", 51.5, -0.1)
        val base = listOf(MapCitySummary(city, mapOf("all" to MapFacet(1, emptyList()), "tasteMatches" to MapFacet(0, emptyList()))))
        val matched = listOf(base.single().copy(facets = base.single().facets + ("tasteMatches" to MapFacet(1, emptyList()))))
        val users = mock<UserRepository> {
            on { followingIds } doReturn MutableStateFlow(setOf("friend"))
            onBlocking { fetchUserProfile("viewer") } doSuspendableAnswer {
                started += "profile"; delay(1_000); profile
            }
            onBlocking { prefetchFollowingSet("viewer") } doSuspendableAnswer {
                started += "following"; delay(1_000)
            }
        }
        val repository = mock<MapRepository> {
            on { ownPresence("viewer") } doReturn emptyFlow()
            on { previewUpdates("viewer") } doReturn emptyFlow()
            on { canonicalUpdates(any()) } doReturn emptyFlow()
            on { cachedTasteMatchIds("viewer") } doAnswer { cachedTaste }
            onBlocking { fetchTasteMatchIds("viewer") } doSuspendableAnswer {
                started += "taste"; delay(60_000)
                if (failTaste) error("Taste lookup unavailable")
                cachedTaste = MapRepository.TasteMatchIdsCache(listOf("match"), System.currentTimeMillis())
                listOf("match")
            }
            onBlocking { cities(any(), any(), anyOrNull(), any()) } doSuspendableAnswer { invocation ->
                if (invocation.getArgument<List<String>>(1).isEmpty()) base else matched
            }
        }
        val firebaseUser = mock<FirebaseUser> { on { uid } doReturn "viewer" }
        val auth = mock<FirebaseAuth> { on { currentUser } doReturn firebaseUser }
        val subscription = mock<SubscriptionRepository> {
            on { hasFullAccess } doReturn true
            on { hasFullAccessFlow } doReturn MutableStateFlow(true)
        }
        val remote = mock<RemoteConfigService> {
            on { revision } doReturn MutableStateFlow(0)
            on { mapEnabled } doReturn false
        }
        val model = MapExploreViewModel(repository, users, auth, subscription, remote, mock(), mock())
        init { store.put("map", model) }
    }

    @Test fun allAndFollowingLoadWithoutAnUncachedTasteLookup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            runCurrent()
            whenever(f.remote.mapEnabled).thenReturn(true)
            f.model.refresh()
            runCurrent()
            assertEquals(setOf("profile", "following"), f.started)
            advanceTimeBy(1_000); runCurrent()
            assertFalse(f.model.state.value.loading)
            assertTrue(f.model.state.value.directoryReady)
            assertFalse(f.model.state.value.tasteMatchesReady)
            assertEquals(f.base, f.model.state.value.cities)
            assertEquals(f.profile, f.model.state.value.user)
            verify(f.repository).cities(listOf("friend"), emptyList(), null, false)
            f.model.filter("following")
            assertFalse(f.model.state.value.loading)
            verify(f.repository, never()).fetchTasteMatchIds(any())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun coldTasteHasItsOwnLoadingStateAndSwitchingBackToAllRemainsUsable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture()
        try {
            runCurrent()
            whenever(f.remote.mapEnabled).thenReturn(true)
            f.model.refresh(); advanceUntilIdle()
            f.model.filter("tasteMatches"); runCurrent()
            assertTrue(f.model.state.value.loading)
            assertFalse(f.model.state.value.tasteMatchesReady)
            assertTrue("Only selecting Taste starts its lookup", "taste" in f.started)
            advanceTimeBy(1_000); runCurrent()
            assertTrue(f.model.state.value.loading)
            f.model.filter("all")
            assertFalse(f.model.state.value.loading)
            assertEquals(f.base, f.model.state.value.cities)
            advanceUntilIdle()
            assertEquals("all", f.model.state.value.filter)
            assertTrue(f.model.state.value.tasteMatchesReady)
            assertEquals(f.matched, f.model.state.value.cities)
            f.model.filter("tasteMatches")
            assertFalse(f.model.state.value.loading)
            verify(f.repository, times(1)).fetchTasteMatchIds("viewer")
            verify(f.repository).cities(listOf("friend"), listOf("match"), null, false)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun aTasteFailureNeverBecomesAConfirmedEmptyResultOrBlocksAll() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = Fixture(failTaste = true)
        try {
            runCurrent()
            whenever(f.remote.mapEnabled).thenReturn(true)
            f.model.refresh(); advanceUntilIdle()
            f.model.filter("tasteMatches"); advanceUntilIdle()
            assertFalse(f.model.state.value.loading)
            assertFalse(f.model.state.value.tasteMatchesReady)
            assertNotNull(f.model.state.value.error)
            assertEquals(f.base, f.model.state.value.cities)
            f.model.filter("all")
            assertFalse(f.model.state.value.loading)
            assertEquals(f.base, f.model.state.value.cities)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
}
