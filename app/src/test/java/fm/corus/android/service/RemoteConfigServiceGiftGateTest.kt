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

    @Test fun `gifts require both remote config and an exact tester uid`() {
        for (uid in RemoteConfigService.GIFT_TESTER_UIDS) {
            assertTrue(service(uid).giftsEnabledForCurrentUser)
        }
        assertFalse(service("gabe").giftsEnabledForCurrentUser)
        assertFalse(service("some-other-uid").giftsEnabledForCurrentUser)
        assertFalse(service(null).giftsEnabledForCurrentUser)
        assertFalse(service(RemoteConfigService.GIFT_TESTER_UIDS.first(), remoteValue = false).giftsEnabledForCurrentUser)
    }

    @Test fun `gift sending requires distinct approved sender and recipient`() {
        val sender = RemoteConfigService.GIFT_TESTER_UIDS.first()
        val anotherTester = RemoteConfigService.GIFT_TESTER_UIDS.first { it != sender }
        val service = service(sender)

        assertTrue(service.canSendGiftTo(anotherTester))
        assertFalse(service.canSendGiftTo(sender))
        assertFalse(service.canSendGiftTo("not-approved"))
        assertFalse(service(sender, remoteValue = false).canSendGiftTo(anotherTester))
    }
}
