package fm.corus.android.service

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regression: on a fresh signup the player-picker's TIDAL/Deezer cards
 * disappeared. The flags read `getBoolean`, which returns the Boolean
 * type-default (false) while Remote Config has no value yet (source == STATIC)
 * — a window that exists before the in-app defaults land. Defaults were only
 * applied inside fetchAndActivate(), which is delayed/aborted by a slow or
 * failing network fetch (App Check token minting), so the cards were gated off.
 *
 * Two guarantees verified here:
 *  1. Defaults are applied locally at construction (init), not just in the fetch.
 *  2. The picker getters fall back to their literal default during the STATIC
 *     window instead of returning false.
 */
class RemoteConfigServiceMusicServiceFlagsTest {

    private fun service(remoteConfig: FirebaseRemoteConfig) =
        RemoteConfigService(remoteConfig, mock<FirebaseAuth>(), mock<Context>())

    private fun configValue(sourceValue: Int, bool: Boolean): FirebaseRemoteConfigValue =
        mock {
            on { source } doReturn sourceValue
            on { asBoolean() } doReturn bool
        }

    @Test
    fun `applies in-app defaults locally at construction`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()

        service(remoteConfig)

        // init() must seed defaults without waiting on a network fetch.
        verify(remoteConfig).setDefaultsAsync(any<Map<String, Any>>())
    }

    @Test
    fun `tidal and deezer fall back to true while value is STATIC (no value yet)`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        // STATIC source + asBoolean=false is exactly what getValue returns before
        // defaults are applied. The picker must still show the cards. Build the
        // value mocks before stubbing getValue so their own stubbing doesn't nest.
        val tidalStatic = configValue(FirebaseRemoteConfig.VALUE_SOURCE_STATIC, false)
        val deezerStatic = configValue(FirebaseRemoteConfig.VALUE_SOURCE_STATIC, false)
        whenever(remoteConfig.getValue(eq("tidal_enabled"))).thenReturn(tidalStatic)
        whenever(remoteConfig.getValue(eq("deezer_enabled"))).thenReturn(deezerStatic)

        val service = service(remoteConfig)

        assertTrue(service.tidalEnabled)
        assertTrue(service.deezerEnabled)
    }

    @Test
    fun `tidal and deezer honor an activated value of false`() {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        // A real REMOTE/DEFAULT value of false must turn the cards off — the
        // fallback only covers the STATIC "not loaded yet" case.
        val tidalRemote = configValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, false)
        val deezerRemote = configValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, false)
        whenever(remoteConfig.getValue(eq("tidal_enabled"))).thenReturn(tidalRemote)
        whenever(remoteConfig.getValue(eq("deezer_enabled"))).thenReturn(deezerRemote)

        val service = service(remoteConfig)

        assertFalse(service.tidalEnabled)
        assertFalse(service.deezerEnabled)
    }
}
