package fm.corus.android.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RemoteConfigServiceGiftGateTest {
    private fun service(uid: String?, remoteValue: Boolean = true): RemoteConfigService {
        val value = mock<FirebaseRemoteConfigValue> {
            on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
            on { asBoolean() } doReturn remoteValue
        }
        val remoteConfig = mock<FirebaseRemoteConfig> {
            on { getValue("gifts_enabled") } doReturn value
        }
        val user = uid?.let { id ->
            mock<FirebaseUser>().also { whenever(it.uid).thenReturn(id) }
        }
        val auth = mock<FirebaseAuth> { on { currentUser } doReturn user }
        val preferences = mock<SharedPreferences>()
        val context = mock<Context> {
            on { getSharedPreferences(any(), any()) } doReturn preferences
        }
        return RemoteConfigService(remoteConfig, auth, context)
    }

    @Test fun `gifts require remote config and a signed in user`() {
        assertTrue(service("ordinary-user").giftsEnabledForCurrentUser)
        assertFalse(service(null).giftsEnabledForCurrentUser)
        assertFalse(service("ordinary-user", remoteValue = false).giftsEnabledForCurrentUser)
    }

    @Test fun `gift sending accepts any distinct recipient while respecting the release gate`() {
        val service = service("sender")
        assertTrue(service.canSendGiftTo("ordinary-recipient"))
        assertFalse(service.canSendGiftTo("sender"))
        assertFalse(service.canSendGiftTo(""))
        assertFalse(service.canSendGiftTo(" "))
        assertFalse(service(null).canSendGiftTo("ordinary-recipient"))
        assertFalse(service("sender", remoteValue = false).canSendGiftTo("ordinary-recipient"))
    }
}
