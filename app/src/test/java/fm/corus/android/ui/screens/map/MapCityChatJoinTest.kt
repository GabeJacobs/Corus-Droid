package fm.corus.android.ui.screens.map

import android.location.Location
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import fm.corus.android.service.AnalyticsService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@org.robolectric.annotation.Config(application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class MapCityChatJoinTest {
    private val city = MapCity("london-gb", "London", "England", "GB", 51.5, -0.1)
    private val auth = mock<FirebaseAuth>()
    private val user = mock<FirebaseUser>()
    private val functions = mock<FirebaseFunctions>()
    private val resolve = mock<HttpsCallableReference>()
    private val join = mock<HttpsCallableReference>()
    private val joinResult = mock<HttpsCallableResult>()

    private fun repository(): MapRepository {
        whenever(user.uid).thenReturn("alice")
        whenever(auth.currentUser).thenReturn(user)
        whenever(functions.getHttpsCallable("resolveMapCity")).thenReturn(resolve)
        whenever(functions.getHttpsCallable("joinCityChat")).thenReturn(join)
        val resolved = mock<HttpsCallableResult>()
        whenever(resolved.getData()).thenReturn(mapOf("communityVersion" to 1, "city" to city.payload()))
        whenever(resolve.call(any<Any>())).thenReturn(Tasks.forResult(resolved))
        whenever(joinResult.getData()).thenReturn(mapOf("threadId" to "grp_city_london"))
        whenever(join.call(any<Any>())).thenReturn(Tasks.forResult(joinResult))
        return MapRepository(RuntimeEnvironment.getApplication(), auth, mock<FirebaseFirestore>(), functions, mock<AnalyticsService>())
    }

    private fun fix(ageMs: Long = 0, accuracyMeters: Float = 30f) = Location("network").apply {
        latitude = 51.5; longitude = -0.1; accuracy = accuracyMeters
        time = System.currentTimeMillis() - ageMs
    }

    @Test fun joiningReusesTheFreshLocationAlreadyResolvedForMapEligibility() = runTest {
        val repository = repository()
        val location = fix()
        repository.resolve(location)
        repository.join(city, Location(location))
        verify(resolve, times(1)).call(any<Any>())
        verify(join).call(argThat<Any> {
            val locationPayload = (this as Map<*, *>)["location"] as Map<*, *>
            locationPayload["cityId"] == city.cityId && locationPayload["timestamp"] == location.time
        })
    }

    @Test fun expiredInaccurateFutureAndOtherAccountEvidenceIsNotReused() = runTest {
        val repository = repository()
        for (location in listOf(fix(91_000), fix(-11_000), fix(accuracyMeters = 1001f))) {
            repository.resolve(location)
            repository.resolve(Location(location))
        }
        verify(resolve, times(6)).call(any<Any>())
        val fresh = fix()
        repository.resolve(fresh)
        whenever(user.uid).thenReturn("bob")
        repository.resolve(Location(fresh))
        verify(resolve, times(8)).call(any<Any>())
    }

    @Test fun cachedFixRequiresPermissionExpiresAndIsNotMutatedByCallers() = runTest {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val repository = repository()
        val location = fix()
        repository.resolve(location)
        val cached = repository.cachedJoinLocation()
        assertNotNull(cached)
        cached!!.latitude = 0.0
        assertEquals(51.5, repository.cachedJoinLocation()!!.latitude, 0.001)
        assertNull(repository.cachedJoinLocation(nowMs = location.time + 91_000L))
        repository.resolve(fix())
        shadowOf(app).denyPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        assertNull(repository.cachedJoinLocation())
    }

    @Test fun inlineConversationIsDecodedAndLegacyOrMismatchedPayloadsUseTheLiveFallback() = runTest {
        val repository = repository()
        val threadId = "grp_city_london"
        val row = mapOf("id" to threadId, "type" to "group", "cityChatId" to city.cityId,
            "cityName" to "London", "memberIds" to listOf("alice"), "members" to emptyMap<String, Any>())
        whenever(joinResult.getData()).thenReturn(mapOf("threadId" to threadId, "thread" to row))
        val joined = repository.join(city, null)
        assertEquals(threadId, joined.threadId)
        assertEquals("London", joined.thread?.cityName)
        assertEquals(listOf("alice"), joined.thread?.memberIds)
        for (bad in listOf(null, row + ("id" to "other"), row + ("type" to "direct"), row + ("memberIds" to listOf("bob")))) {
            whenever(joinResult.getData()).thenReturn(mapOf("threadId" to threadId, "thread" to bad))
            val fallback = repository.join(city, null)
            assertEquals(threadId, fallback.threadId)
            assertNull(fallback.thread)
        }
    }
}
