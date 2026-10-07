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
class FeedTabPresentationTest {
    private val dispatcher = StandardTestDispatcher()
    private val rc = mock<FirebaseRemoteConfig>()
    private val auth = mock<FirebaseAuth>()
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var authListener: FirebaseAuth.AuthStateListener
    private var newEnabled = true
    private var trendingEnabled = true

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        context.getSharedPreferences("corus_rc_cache", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("corus_feed_tab_presentation", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("corus_dev_flags", Context.MODE_PRIVATE).edit().clear().commit()
        val value = mock<FirebaseRemoteConfigValue> {
            on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
            on { asString() } doReturn ""
            on { asBoolean() } doAnswer { trendingEnabled }
        }
        whenever(rc.getValue(any())).thenReturn(value)
        whenever(rc.getString(any())).thenReturn("")
        whenever(rc.getBoolean(any())).thenAnswer {
            when (it.getArgument<String>(0)) {
                "feed_new_tab_enabled" -> newEnabled
                "trending_feed_enabled" -> trendingEnabled
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

    @Test fun `refresh cannot rearrange tabs already presented to this account`() = runTest(dispatcher) {
        val service = RemoteConfigService(rc, auth, context)
        service.fetchAndActivate(forceFresh = true)
        assertTrue(service.feedNewTabEnabled)
        assertTrue(service.trendingFeedEnabled)
        authListener.onAuthStateChanged(auth)
        assertFalse("Same-account token refresh must not restart the cover", service.isResolvingFeedTabs)
        newEnabled = false
        trendingEnabled = false
        service.fetchAndActivate(forceFresh = true)
        assertTrue("New must stay in the current layout", service.feedNewTabEnabled)
        assertTrue("Trending must stay in the current layout", service.trendingFeedEnabled)
    }
    @Test fun `first paint waits for a fast account config response`() = runTest(dispatcher) {
        val fetch = TaskCompletionSource<Void>()
        whenever(rc.fetch()).thenReturn(fetch.task)
        val service = RemoteConfigService(rc, auth, context)
        val request = launch { service.fetchAndActivate(forceFresh = true) }
        val presentation = launch { service.awaitFeedTabPresentation() }
        runCurrent()
        assertFalse(service.feedTabPresentation.value.hasPresentation)
        advanceTimeBy(200)
        fetch.setResult(null)
        runCurrent()
        assertTrue(presentation.isCompleted)
        assertTrue(service.feedTabPresentation.value.hasPresentation)
        assertTrue(service.feedNewTabEnabled)
        request.join()
    }

    @Test fun `deadline preserves the fallback and a late ON belongs to the next launch`() = runTest(dispatcher) {
        val fetch = TaskCompletionSource<Void>()
        whenever(rc.fetch()).thenReturn(fetch.task)
        val service = RemoteConfigService(rc, auth, context)
        val request = launch { service.fetchAndActivate(forceFresh = true) }
        val presentation = launch { service.awaitFeedTabPresentation() }
        runCurrent()
        advanceTimeBy(999); runCurrent()
        assertFalse(presentation.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertTrue(presentation.isCompleted)
        assertFalse(service.feedNewTabEnabled)
        fetch.setResult(null); runCurrent()
        assertFalse("Late response must not move visible tabs", service.feedNewTabEnabled)
        request.join()
        val nextLaunch = RemoteConfigService(rc, auth, context)
        launch { nextLaunch.awaitFeedTabPresentation() }
        advanceUntilIdle()
        assertTrue("Late response must warm this account's next launch", nextLaunch.feedNewTabEnabled)
    }

    @Test fun `failed fetch retains confirmed tabs offline and cannot leak to another account`() = runTest(dispatcher) {
        RemoteConfigService(rc, auth, context).fetchAndActivate(forceFresh = true)
        whenever(rc.fetch()).thenReturn(Tasks.forException(IllegalStateException("offline")))
        val offline = RemoteConfigService(rc, auth, context)
        offline.fetchAndActivate(forceFresh = true)
        assertFalse(offline.isResolvingFeedTabs)
        assertTrue(offline.feedNewTabEnabled)
        signIn("other")
        assertTrue(offline.isResolvingFeedTabs)
        offline.fetchAndActivate(forceFresh = true)
        assertFalse(offline.feedNewTabEnabled)
        assertTrue("Uncached accounts retain the ordinary in-app defaults", offline.trendingFeedEnabled)
        assertEquals("other", offline.feedTabPresentation.value.uid)
    }

    @Test fun `obsolete fetch completion cannot settle the next account`() = runTest(dispatcher) {
        val fetch = TaskCompletionSource<Void>()
        whenever(rc.fetch()).thenReturn(fetch.task)
        val service = RemoteConfigService(rc, auth, context)
        launch { service.fetchAndActivate(forceFresh = true) }; runCurrent()
        signIn("other")
        launch { service.awaitFeedTabPresentation() }; runCurrent()
        fetch.setResult(null); runCurrent()
        assertEquals("other", service.feedTabPresentation.value.uid)
        assertFalse(service.feedTabPresentation.value.hasPresentation)
        advanceUntilIdle()
        assertFalse(service.feedNewTabEnabled)
    }

    @Test fun `leaving and returning to the same uid rejects the previous session response`() = runTest(dispatcher) {
        val fetch = TaskCompletionSource<Void>()
        whenever(rc.fetch()).thenReturn(fetch.task)
        val service = RemoteConfigService(rc, auth, context)
        launch { service.fetchAndActivate(forceFresh = true) }; runCurrent()
        signIn("other")
        signIn("gabe")
        launch { service.awaitFeedTabPresentation() }; runCurrent()
        fetch.setResult(null); runCurrent()
        assertFalse(service.feedTabPresentation.value.hasPresentation)
        advanceUntilIdle()
        assertFalse(service.feedNewTabEnabled)
    }

    @Test fun `first launch after upgrade retains ordinary Matches from the legacy cache`() = runTest(dispatcher) {
        context.getSharedPreferences("corus_rc_cache", Context.MODE_PRIVATE).edit()
            .putBoolean("taste_matches_enabled", true)
            .putBoolean("taste_matches_tester", true)
            .putBoolean("feed_new_tab_enabled", true)
            .apply()
        newEnabled = false
        val service = RemoteConfigService(rc, auth, context)
        launch { service.awaitFeedTabPresentation() }
        advanceUntilIdle()
        assertTrue("A slow fetch must preserve the released Matches tab", service.tasteMatchesEnabled)
        assertFalse("Unscoped tester access must not cross accounts", service.tasteMatchesTester)
        assertFalse("Unscoped pilot access must not cross accounts", service.feedNewTabEnabled)
        assertEquals(listOf("following", "trending", "tasteMatches"),
            fm.corus.android.ui.screens.feed.visibleFeedModeTabs(
                trendingEnabled = service.trendingFeedEnabled,
                tasteMatchesAvailable = service.tasteMatchesEnabled || service.tasteMatchesTester,
                favoritesEnabled = service.favoritesEnabled,
                favoritesCount = 0,
                prototypeEnabled = false,
                newTabEnabled = service.feedNewTabEnabled,
            ))
    }

    @Test fun `confirmed account Matches decision overrides the legacy cache`() = runTest(dispatcher) {
        context.getSharedPreferences("corus_rc_cache", Context.MODE_PRIVATE).edit()
            .putBoolean("taste_matches_enabled", true).apply()
        context.getSharedPreferences("corus_feed_tab_presentation", Context.MODE_PRIVATE).edit()
            .putString("gabe:taste_matches_enabled", "false").apply()
        val service = RemoteConfigService(rc, auth, context)
        launch { service.awaitFeedTabPresentation() }
        advanceUntilIdle()
        assertFalse(service.tasteMatchesEnabled)
    }

}
