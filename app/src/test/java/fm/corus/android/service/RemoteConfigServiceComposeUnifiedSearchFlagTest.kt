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
 * `compose_unified_search_enabled` gates the unified compose picker (one search
 * field over songs AND films, All/Songs/Films chips, blended zero state). It
 * must follow the init-race-safe feed-flag pattern:
 *  1. present in the in-app DEFAULTS applied at construction (value FALSE in
 *     code — the picker ships dark and the console flip is what launches it),
 *  2. read via the feedFlag path — live value when activated REMOTE, else the
 *     last persisted value, so the picker's layout is right on the first frame
 *     after a cold launch instead of reverting to the segmented toggle.
 *
 * That second point is the bug class this guards: forgetting the
 * `cacheFeedFlags()` line makes the flag silently read false on every cold
 * launch until the fetch lands.
 */
class RemoteConfigServiceComposeUnifiedSearchFlagTest {

    private fun service(
        remoteConfig: FirebaseRemoteConfig,
        cachedValue: Boolean? = null,
    ): RemoteConfigService {
        // feedFlag's fallback path reads the "corus_rc_cache" SharedPreferences;
        // emulate a persisted value (or pass-through of the provided default).
        val prefs = mock<SharedPreferences> {
            on { getBoolean(any(), any()) } doAnswer { inv ->
                cachedValue ?: inv.getArgument(1)
            }
        }
        val context = mock<Context> {
            on { getSharedPreferences(any(), any()) } doReturn prefs
        }
        return RemoteConfigService(remoteConfig, mock<FirebaseAuth>(), context)
    }

    private fun configValue(sourceValue: Int, bool: Boolean): FirebaseRemoteConfigValue =
        mock {
            on { source } doReturn sourceValue
            on { asBoolean() } doReturn bool
        }

    @Test
    fun `in-app defaults include compose_unified_search_enabled = false`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()

        service(remoteConfig)

        val captor = argumentCaptor<Map<String, Any>>()
        verify(remoteConfig).setDefaultsAsync(captor.capture())
        assertEquals(false, captor.firstValue["compose_unified_search_enabled"])
    }

    @Test
    fun `flag follows an activated remote value`() {
        // Build the value mocks BEFORE stubbing getValue so their own stubbing
        // doesn't nest (same pattern as the artist-pages flag test).
        val remoteTrue = configValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, true)
        val remoteFalse = configValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, false)

        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getValue(eq("compose_unified_search_enabled"))).thenReturn(remoteTrue)
        assertTrue(service(remoteConfig).composeUnifiedSearchEnabled)

        whenever(remoteConfig.getValue(eq("compose_unified_search_enabled"))).thenReturn(remoteFalse)
        assertFalse(service(remoteConfig).composeUnifiedSearchEnabled)
    }

    @Test
    fun `flag falls back to the persisted cache before remote activation`() {
        // Cold launch: no activated REMOTE value yet (source == DEFAULT), but a
        // previous session cached true — the picker must be unified from frame 1.
        val defaultFalse = configValue(FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT, false)
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getValue(eq("compose_unified_search_enabled"))).thenReturn(defaultFalse)

        assertTrue(service(remoteConfig, cachedValue = true).composeUnifiedSearchEnabled)
    }

    @Test
    fun `flag is false with no cache and no remote value`() {
        val defaultFalse = configValue(FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT, false)
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getValue(eq("compose_unified_search_enabled"))).thenReturn(defaultFalse)

        assertFalse(service(remoteConfig, cachedValue = null).composeUnifiedSearchEnabled)
    }
}
