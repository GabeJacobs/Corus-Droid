package fm.corus.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileMapCityDisplayOwnTest {

    private val nyc = ProfileMapCity("nyc", "New York, New York")
    private val rio = ProfileMapCity("rio", "Rio de Janeiro, Brazil")

    @Test
    fun liveNullNeverPaintsCache() {
        assertNull(ProfileMapCity.displayOwn(live = null, cached = nyc))
    }

    @Test
    fun liveCityWinsEvenWhenCacheDiffers() {
        assertEquals(rio, ProfileMapCity.displayOwn(live = rio, cached = nyc))
    }

    @Test
    fun matchingCacheStillShowsLive() {
        assertEquals(nyc, ProfileMapCity.displayOwn(live = nyc, cached = nyc))
    }
}
