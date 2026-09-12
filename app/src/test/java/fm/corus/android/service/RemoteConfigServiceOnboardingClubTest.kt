package fm.corus.android.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class RemoteConfigServiceOnboardingClubTest {
    private fun service(): RemoteConfigService {
        val user = mock<FirebaseUser> {
            on { uid } doReturn "user-a"
        }
        val auth = mock<FirebaseAuth> { on { currentUser } doReturn user }
        val prefs = mock<SharedPreferences> {
            on { getBoolean(any(), any()) } doAnswer { invocation -> invocation.getArgument<Boolean>(1) }
        }
        val context = mock<Context> { on { getSharedPreferences(any(), any()) } doReturn prefs }
        val configValue = mock<FirebaseRemoteConfigValue> {
            on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT
            on { asBoolean() } doReturn false
        }
        val config = mock<FirebaseRemoteConfig> { on { getValue(any()) } doReturn configValue }
        return RemoteConfigService(config, auth, context)
    }

    @Test fun `remote config default is false`() {
        val config = mock<FirebaseRemoteConfig>()
        val prefs = mock<SharedPreferences>()
        val context = mock<Context> { on { getSharedPreferences(any(), any()) } doReturn prefs }
        RemoteConfigService(config, mock(), context)
        val defaults = argumentCaptor<Map<String, Any>>()
        verify(config).setDefaultsAsync(defaults.capture())
        assertEquals(false, defaults.firstValue["onboarding_club_offer_enabled"])
    }

    @Test fun `third post cannot appear at first or second post`() {
        val service = service()
        assertFalse(service.claimThirdPostOffer(0))
        assertFalse(service.claimThirdPostOffer(1))
        assertFalse(service.claimThirdPostOffer(2))
    }
}
