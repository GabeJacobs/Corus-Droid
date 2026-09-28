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

class RemoteConfigServiceProfileSharingV2Test {
    private fun service(
        remoteConfig: FirebaseRemoteConfig,
        cachedValue: Boolean? = null,
    ): RemoteConfigService {
        val prefs = mock<SharedPreferences> {
            on { getBoolean(any(), any()) } doAnswer { invocation ->
                cachedValue ?: invocation.getArgument(1)
            }
        }
        val context = mock<Context> {
            on { getSharedPreferences(any(), any()) } doReturn prefs
        }
        return RemoteConfigService(remoteConfig, mock<FirebaseAuth>(), context)
    }

    private fun boolValue(valueSource: Int, value: Boolean): FirebaseRemoteConfigValue = mock {
        on { source } doReturn valueSource
        on { asBoolean() } doReturn value
    }

    @Test fun `in-app default is false`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        service(remoteConfig)

        val defaults = argumentCaptor<Map<String, Any>>()
        verify(remoteConfig).setDefaultsAsync(defaults.capture())
        assertEquals(false, defaults.firstValue["profile_sharing_v2"])
    }

    @Test fun `activated remote value controls V2`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        doReturn(boolValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, true))
            .whenever(remoteConfig).getValue(eq("profile_sharing_v2"))
        assertTrue(service(remoteConfig).profileSharingV2)

        doReturn(boolValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, false))
            .whenever(remoteConfig).getValue(eq("profile_sharing_v2"))
        assertFalse(service(remoteConfig).profileSharingV2)
    }

    @Test fun `cached value is used before remote activation`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        doReturn(boolValue(FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT, false))
            .whenever(remoteConfig).getValue(eq("profile_sharing_v2"))
        assertTrue(service(remoteConfig, cachedValue = true).profileSharingV2)
    }
}
