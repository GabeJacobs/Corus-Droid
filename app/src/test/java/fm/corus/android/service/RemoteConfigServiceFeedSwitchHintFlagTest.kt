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

/**
 * `feed_switch_hint_*` gate the one-time feed-switch discovery coachmark and
 * must mirror iOS/web: `feed_switch_hint_enabled` defaults false and reads via
 * the init-race-safe feedFlag path; the numeric min-session / max-impressions
 * getters default to 3 when Remote Config has no positive value.
 */
class RemoteConfigServiceFeedSwitchHintFlagTest {

    private fun service(
        remoteConfig: FirebaseRemoteConfig,
        cachedValue: Boolean? = null,
    ): RemoteConfigService {
        val prefs = mock<SharedPreferences> {
            on { getBoolean(any(), any()) } doAnswer { inv -> cachedValue ?: inv.getArgument(1) }
        }
        val context = mock<Context> {
            on { getSharedPreferences(any(), any()) } doReturn prefs
        }
        return RemoteConfigService(remoteConfig, mock<FirebaseAuth>(), context)
    }

    private fun boolValue(sourceValue: Int, bool: Boolean): FirebaseRemoteConfigValue =
        mock {
            on { source } doReturn sourceValue
            on { asBoolean() } doReturn bool
        }

    @Test
    fun `in-app defaults include the feed switch hint keys`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()

        service(remoteConfig)

        val captor = argumentCaptor<Map<String, Any>>()
        verify(remoteConfig).setDefaultsAsync(captor.capture())
        assertEquals(false, captor.firstValue["feed_switch_hint_enabled"])
        assertEquals(3L, captor.firstValue["feed_switch_hint_min_session"])
        assertEquals(3L, captor.firstValue["feed_switch_hint_max_impressions"])
    }

    @Test
    fun `enabled follows an activated remote value`() {
        val remoteTrue = boolValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, true)
        val remoteFalse = boolValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, false)

        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getValue(eq("feed_switch_hint_enabled"))).thenReturn(remoteTrue)
        assertTrue(service(remoteConfig).feedSwitchHintEnabled)

        whenever(remoteConfig.getValue(eq("feed_switch_hint_enabled"))).thenReturn(remoteFalse)
        assertFalse(service(remoteConfig).feedSwitchHintEnabled)
    }

    @Test
    fun `enabled falls back to the persisted cache before remote activation`() {
        val defaultFalse = boolValue(FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT, false)
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getValue(eq("feed_switch_hint_enabled"))).thenReturn(defaultFalse)

        assertTrue(service(remoteConfig, cachedValue = true).feedSwitchHintEnabled)
    }

    @Test
    fun `min session and max impressions default to 3 when unset`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getLong(eq("feed_switch_hint_min_session"))).thenReturn(0L)
        whenever(remoteConfig.getLong(eq("feed_switch_hint_max_impressions"))).thenReturn(0L)

        val svc = service(remoteConfig)
        assertEquals(3, svc.feedSwitchHintMinSession)
        assertEquals(3, svc.feedSwitchHintMaxImpressions)
    }

    @Test
    fun `min session and max impressions read positive remote values`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getLong(eq("feed_switch_hint_min_session"))).thenReturn(5L)
        whenever(remoteConfig.getLong(eq("feed_switch_hint_max_impressions"))).thenReturn(2L)

        val svc = service(remoteConfig)
        assertEquals(5, svc.feedSwitchHintMinSession)
        assertEquals(2, svc.feedSwitchHintMaxImpressions)
    }
}
