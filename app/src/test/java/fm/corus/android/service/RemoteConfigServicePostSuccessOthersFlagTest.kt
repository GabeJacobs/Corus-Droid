package fm.corus.android.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RemoteConfigServicePostSuccessOthersFlagTest {

    @Test
    fun `in-app defaults include post_success_others_enabled = false`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        val prefs = mock<SharedPreferences> {
            on { getBoolean(any(), any()) } doAnswer { inv -> inv.getArgument(1) }
        }
        val context = mock<Context> {
            on { getSharedPreferences(any(), any()) } doReturn prefs
        }

        RemoteConfigService(remoteConfig, mock<FirebaseAuth>(), context)

        val captor = argumentCaptor<Map<String, Any>>()
        verify(remoteConfig).setDefaultsAsync(captor.capture())
        // Release default stays false. DEBUG builds OR the remote value on
        // so a local signup can see the sheet without an RC allowlist.
        assertEquals(false, captor.firstValue["post_success_others_enabled"])
    }
}
