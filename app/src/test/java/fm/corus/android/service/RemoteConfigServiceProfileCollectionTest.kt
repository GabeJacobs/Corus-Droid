package fm.corus.android.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RemoteConfigServiceProfileCollectionTest {
    private fun service(remoteConfig: FirebaseRemoteConfig): RemoteConfigService {
        val prefs = mock<SharedPreferences> {
            on { getBoolean(any(), any()) } doAnswer { it.getArgument<Boolean>(1) }
        }
        val context = mock<Context> {
            on { getSharedPreferences(any(), any()) } doReturn prefs
        }
        return RemoteConfigService(remoteConfig, mock<FirebaseAuth>(), context)
    }

    private fun value(enabled: Boolean): FirebaseRemoteConfigValue = mock {
        on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
        on { asBoolean() } doReturn enabled
    }

    @Test fun `shared flag defaults off including debug builds`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        val flags = service(remoteConfig)
        val defaults = argumentCaptor<Map<String, Any>>()
        verify(remoteConfig).setDefaultsAsync(defaults.capture())
        assertEquals(false, defaults.firstValue["profile_collection_enabled"])
        val missing = mock<FirebaseRemoteConfigValue> {
            on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_STATIC
        }
        doReturn(missing).whenever(remoteConfig).getValue(eq("profile_collection_enabled"))
        assertFalse(flags.profileCollectionEnabled)
    }

    @Test fun `activated shared flag controls collection without a forced debug override`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        val flags = service(remoteConfig)
        doReturn(value(true)).whenever(remoteConfig).getValue(eq("profile_collection_enabled"))
        assertTrue(flags.profileCollectionEnabled)
        doReturn(value(false)).whenever(remoteConfig).getValue(eq("profile_collection_enabled"))
        assertFalse(flags.profileCollectionEnabled)
    }
}
