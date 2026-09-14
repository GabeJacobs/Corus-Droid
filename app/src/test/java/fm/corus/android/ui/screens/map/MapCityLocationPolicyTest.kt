package fm.corus.android.ui.screens.map

import android.location.LocationManager
import org.junit.Assert.*
import org.junit.Test

class MapCityLocationPolicyTest {
    @Test fun approximateOnlyUsesCityLevelProvidersWithoutGps() {
        val providers = cityLocationProviders(
            setOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER),
            hasFine = false,
        )
        assertEquals(listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER), providers)
        assertFalse(LocationManager.GPS_PROVIDER in providers)
    }

    @Test fun preciseGrantOnlyAddsGpsAsLastFallback() {
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            cityLocationProviders(setOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER), hasFine = true),
        )
    }

    @Test fun firstTimeoutGetsOneAutomaticProviderWarmUpRetry() {
        assertTrue(shouldWarmUpCityLocation(attempt = 1))
        assertFalse(shouldWarmUpCityLocation(attempt = 2))
    }

    @Test fun locationLifecycleAlwaysClearsLoadingAndUsesOneErrorSurface() {
        assertTrue(cityLocationStarted().resolving)
        assertFalse(cityLocationFinished().resolving)
        val failed = cityLocationFailed()
        assertFalse(failed.resolving)
        assertTrue(failed.recoverySheet)
        assertNull(failed.genericError)
    }
}
