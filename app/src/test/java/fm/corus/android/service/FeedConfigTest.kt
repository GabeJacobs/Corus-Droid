package fm.corus.android.service

import android.app.Application
import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FeedConfigTest {
    private val dispatcher = StandardTestDispatcher()
    private val rc = mock<FirebaseRemoteConfig>()
    private val auth = mock<FirebaseAuth>()
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var authListener: FirebaseAuth.AuthStateListener
    private var favoritesEnabled = true
    private var testerEnabled = false

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        context.getSharedPreferences("corus_rc_cache", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("corus_feed_tab_presentation", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("corus_dev_flags", Context.MODE_PRIVATE).edit().clear().commit()
        val value = mock<FirebaseRemoteConfigValue> {
            on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
            on { asString() } doReturn ""
            on { asBoolean() } doReturn false
        }
        whenever(rc.getValue(any())).thenReturn(value)
        whenever(rc.getString(any())).thenReturn("")
        whenever(rc.getBoolean(any())).thenAnswer {
            when (it.getArgument<String>(0)) {
                "taste_matches_tester" -> testerEnabled
                "favorites_enabled" -> favoritesEnabled
                else -> false
            }
        }
        whenever(rc.setDefaultsAsync(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))
        whenever(rc.setConfigSettingsAsync(any())).thenReturn(Tasks.forResult(null))
        whenever(rc.setCustomSignals(any())).thenReturn(Tasks.forResult(null))
        whenever(rc.fetch()).thenReturn(Tasks.forResult(null))
        whenever(rc.activate()).thenReturn(Tasks.forResult(true))
        doAnswer { authListener = it.getArgument(0); authListener.onAuthStateChanged(auth); null }
            .whenever(auth).addAuthStateListener(any())
        signIn("gabe")
    }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun signIn(uid: String) {
        val user = mock<FirebaseUser> { on { this.uid } doReturn uid }
        whenever(auth.currentUser).thenReturn(user)
        if (this::authListener.isInitialized) authListener.onAuthStateChanged(auth)
    }

    @Test fun `cached account settings are available while the refresh is pending`() = runTest(dispatcher) {
        context.getSharedPreferences("corus_feed_tab_presentation", Context.MODE_PRIVATE).edit()
            .putString("gabe:favorites_enabled", "false").apply()
        val fetch = TaskCompletionSource<Void>()
        whenever(rc.fetch()).thenReturn(fetch.task)
        val service = RemoteConfigService(rc, auth, context)
        val request = launch { service.fetchAndActivate(forceFresh = true) }
        runCurrent()

        assertTrue(service.feedNewTabEnabled)
        assertFalse(service.favoritesEnabled)
        assertFalse(request.isCompleted)

        fetch.setResult(null)
        request.join()
        assertTrue(service.favoritesEnabled)
        assertTrue(RemoteConfigService(rc, auth, context).favoritesEnabled)
    }

    @Test fun `failed refresh retains the account cache without leaking tester access`() = runTest(dispatcher) {
        testerEnabled = true
        RemoteConfigService(rc, auth, context).fetchAndActivate(forceFresh = true)
        whenever(rc.fetch()).thenReturn(Tasks.forException(IllegalStateException("offline")))
        val offline = RemoteConfigService(rc, auth, context)
        offline.fetchAndActivate(forceFresh = true)
        assertTrue(offline.tasteMatchesTester)

        signIn("other")
        offline.fetchAndActivate(forceFresh = true)
        assertTrue(offline.feedNewTabEnabled)
        assertFalse(offline.tasteMatchesTester)
    }

    @Test fun `obsolete fetch completion cannot replace the next account settings`() = runTest(dispatcher) {
        val fetch = TaskCompletionSource<Void>()
        whenever(rc.fetch()).thenReturn(fetch.task)
        val service = RemoteConfigService(rc, auth, context)
        val request = launch { service.fetchAndActivate(forceFresh = true) }
        runCurrent()
        signIn("other")
        favoritesEnabled = false

        fetch.setResult(null)
        request.join()
        assertTrue(service.favoritesEnabled)
    }

    @Test fun `leaving and returning to the same uid rejects the previous session response`() = runTest(dispatcher) {
        val fetch = TaskCompletionSource<Void>()
        whenever(rc.fetch()).thenReturn(fetch.task)
        val service = RemoteConfigService(rc, auth, context)
        val request = launch { service.fetchAndActivate(forceFresh = true) }
        runCurrent()
        signIn("other")
        signIn("gabe")
        favoritesEnabled = false

        fetch.setResult(null)
        request.join()
        assertTrue(service.favoritesEnabled)
    }

    @Test fun `existing installs retain ordinary flags without inheriting tester access`() {
        context.getSharedPreferences("corus_rc_cache", Context.MODE_PRIVATE).edit()
            .putBoolean("taste_matches_enabled", true)
            .putBoolean("taste_matches_tester", true)
            .apply()
        val service = RemoteConfigService(rc, auth, context)

        assertTrue(service.tasteMatchesEnabled)
        assertFalse(service.tasteMatchesTester)
        assertTrue(service.feedNewTabEnabled)
    }
}
