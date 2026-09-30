package fm.corus.android.ui.screens.map

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
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@org.robolectric.annotation.Config(application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class MapRepositoryCitySearchTest {
    @Test fun successfulQueriesNormalizeExpireAndStayAccountScoped() = runTest {
        val auth = mock<FirebaseAuth>()
        val user = mock<FirebaseUser>()
        whenever(user.uid).thenReturn("one")
        whenever(auth.currentUser).thenReturn(user)
        val functions = mock<FirebaseFunctions>()
        val callable = mock<HttpsCallableReference>()
        val result = mock<HttpsCallableResult>()
        whenever(functions.getHttpsCallable("searchMapCities")).thenReturn(callable)
        whenever(result.getData()).thenReturn(mapOf("communityVersion" to 1, "cities" to emptyList<Any>()))
        whenever(callable.call(any<Any>())).thenReturn(Tasks.forResult(result))
        val repository = MapRepository(RuntimeEnvironment.getApplication(), auth, mock<FirebaseFirestore>(), functions, mock<AnalyticsService>())
        repository.search(" São   Paulo ")
        assertEquals(emptyList<MapCity>(), repository.cachedCitySearch("sao paulo"))
        repository.search("sao paulo")
        verify(callable, times(1)).call(any<Any>())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
        assertNull(repository.cachedCitySearch("sao paulo"))
        repository.search("sao paulo")
        verify(callable, times(2)).call(any<Any>())
        whenever(user.uid).thenReturn("two")
        assertNull(repository.cachedCitySearch("sao paulo"))
        whenever(result.getData()).thenReturn(mapOf("communityVersion" to 1, "cities" to emptyList<Any>(), "providerUnavailable" to true))
        repository.search("Rio")
        assertNull(repository.cachedCitySearch("Rio"))
    }
}
