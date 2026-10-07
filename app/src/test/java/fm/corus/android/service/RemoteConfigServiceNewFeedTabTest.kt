package fm.corus.android.service

import android.app.Application
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RemoteConfigServiceNewFeedTabTest {
    @Test fun `pilot is default off and stale SDK values cannot cross accounts`() = runTest {
        val rc = mock<FirebaseRemoteConfig>()
        val value = mock<FirebaseRemoteConfigValue> {
            on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
            on { asString() } doReturn ""
        }
        whenever(rc.getValue(any())).thenReturn(value)
        whenever(rc.getString(any())).thenReturn("")
        whenever(rc.setDefaultsAsync(any<Map<String, Any>>())).thenReturn(Tasks.forResult(null))
        whenever(rc.setConfigSettingsAsync(any())).thenReturn(Tasks.forResult(null))
        whenever(rc.setCustomSignals(any())).thenReturn(Tasks.forResult(null))
        whenever(rc.fetch()).thenReturn(Tasks.forResult(null))
        whenever(rc.activate()).thenReturn(Tasks.forResult(true))
        whenever(rc.getBoolean("feed_new_tab_enabled")).thenReturn(true)
        var uid = "gabe"
        val viewer = mock<FirebaseUser> { on { this.uid } doAnswer { uid } }
        val auth = mock<FirebaseAuth> { on { currentUser } doReturn viewer }
        val service = RemoteConfigService(rc, auth, RuntimeEnvironment.getApplication())
        val defaults = argumentCaptor<Map<String, Any>>()
        verify(rc).setDefaultsAsync(defaults.capture())
        assertEquals(false, defaults.firstValue["feed_new_tab_enabled"])
        assertFalse(service.feedNewTabEnabled) // stale cached true before first fetch
        service.fetchAndActivate(forceFresh = true)
        assertTrue(service.feedNewTabEnabled)
        uid = "someone-else"
        assertFalse(service.feedNewTabEnabled) // no previous viewer's rollout while fetching
        service.setCurrentUserSignal(uid)
        whenever(rc.getBoolean("feed_new_tab_enabled")).thenReturn(false)
        service.fetchAndActivate(forceFresh = true)
        assertFalse(service.feedNewTabEnabled)
        whenever(auth.currentUser).thenReturn(null)
        assertFalse(service.feedNewTabEnabled)
    }
}
