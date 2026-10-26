package fm.corus.android.ui.screens.map

import android.location.LocationManager
import org.junit.Assert.*
import org.junit.Test

class MapCityLocationPolicyTest {
    @Test fun chatFixMustMeetServerAccuracyAndFreshnessWithTransitMargin() {
        val now = 1_000_000L
        fun valid(age: Long = 0, accuracy: Float = 30f, latitude: Double = 51.5, longitude: Double = -0.1) =
            cityChatLocationIsFresh(now - age, accuracy, latitude, longitude, now)
        assertTrue(valid())
        assertTrue(valid(age = 90_000))
        assertTrue(valid(age = -10_000, accuracy = 1000f))
        assertFalse(valid(age = 90_001))
        assertFalse(valid(age = -10_001))
        assertFalse(valid(accuracy = 1001f))
        assertFalse(valid(accuracy = -1f))
        assertFalse(valid(accuracy = Float.NaN))
        assertFalse(valid(latitude = Double.NaN))
        assertFalse(valid(latitude = 91.0))
        assertFalse(valid(longitude = 181.0))
    }
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
