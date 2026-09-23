package fm.corus.android.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class RemoteConfigServiceInstagramV2Test {
    @Test fun `flag defaults off and follows live remote value`() {
        val rc = mock<FirebaseRemoteConfig>()
        val prefs = mock<SharedPreferences>()
        val context = mock<Context> { on { getSharedPreferences(any(), any()) } doReturn prefs }
        val service = RemoteConfigService(rc, mock<FirebaseAuth>(), context)
        val defaults = argumentCaptor<Map<String, Any>>()
        verify(rc).setDefaultsAsync(defaults.capture())
        assertEquals(false, defaults.firstValue["post_to_instagram_v2"])
        assertFalse(service.postToInstagramV2)
        whenever(rc.getBoolean("post_to_instagram_v2")).thenReturn(true)
        assertTrue(service.postToInstagramV2)
        whenever(rc.getBoolean("post_to_instagram_v2")).thenReturn(false)
        assertFalse(service.postToInstagramV2)
    }
}
