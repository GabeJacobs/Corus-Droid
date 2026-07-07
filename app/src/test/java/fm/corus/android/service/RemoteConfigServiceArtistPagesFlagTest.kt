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
 * `artist_pages_enabled` gates the artist/album/director destination pages.
 * It must follow the init-race-safe feed-flag pattern:
 *  1. present in the in-app DEFAULTS applied at construction (value FALSE in
 *     code — the server currently sends true, and flipping the console key off
 *     must revert every client),
 *  2. read via the feedFlag path — live value when activated REMOTE, else the
 *     last persisted value so the gated search tabs render correctly on a cold
 *     launch before the disk-cached config loads.
 */
class RemoteConfigServiceArtistPagesFlagTest {

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
    fun `in-app defaults include artist_pages_enabled = false`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()

        service(remoteConfig)

        val captor = argumentCaptor<Map<String, Any>>()
        verify(remoteConfig).setDefaultsAsync(captor.capture())
        assertEquals(false, captor.firstValue["artist_pages_enabled"])
    }

    @Test
    fun `flag follows an activated remote value`() {
        // Build the value mocks BEFORE stubbing getValue so their own stubbing
        // doesn't nest (same pattern as RemoteConfigServiceMusicServiceFlagsTest).
        val remoteTrue = configValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, true)
        val remoteFalse = configValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, false)

        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getValue(eq("artist_pages_enabled"))).thenReturn(remoteTrue)
        assertTrue(service(remoteConfig).artistPagesEnabled)

        whenever(remoteConfig.getValue(eq("artist_pages_enabled"))).thenReturn(remoteFalse)
        assertFalse(service(remoteConfig).artistPagesEnabled)
    }

    @Test
    fun `flag falls back to the persisted cache before remote activation`() {
        // Cold launch: no activated REMOTE value yet (source == DEFAULT), but a
        // previous session cached true — the tabs must be gated ON from frame 1.
        val defaultFalse = configValue(FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT, false)
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getValue(eq("artist_pages_enabled"))).thenReturn(defaultFalse)

        assertTrue(service(remoteConfig, cachedValue = true).artistPagesEnabled)
    }

    @Test
    fun `flag is false with no cache and no remote value`() {
        val defaultFalse = configValue(FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT, false)
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.getValue(eq("artist_pages_enabled"))).thenReturn(defaultFalse)

        assertFalse(service(remoteConfig, cachedValue = null).artistPagesEnabled)
    }
}
