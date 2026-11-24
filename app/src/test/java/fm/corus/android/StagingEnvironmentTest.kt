package fm.corus.android

import fm.corus.android.domain.SpotifyAuthService
import org.junit.Assert.*
import org.junit.Test

class StagingEnvironmentTest {
    @Test fun compiledEnvironmentKeepsStagingAndProductionSeparate() {
        if (BuildConfig.CORUS_STAGING) {
            assertEquals("fm.corus.android.staging", BuildConfig.APPLICATION_ID)
            assertEquals("237e99bc39e14255aae3eb460c748fa9", SpotifyAuthService.CLIENT_ID)
            assertEquals("corus-staging://spotify-auth", SpotifyAuthService.REDIRECT_URI)
            assertEquals("corus-staging", BuildConfig.CORUS_URL_SCHEME)
            assertEquals("", BuildConfig.APP_CHECK_DEBUG_TOKEN)
            assertFalse(TestEnvironment.isActive)
        } else {
            assertEquals("fm.corus.android", BuildConfig.APPLICATION_ID)
            assertEquals("6c95dc15990f43b0a3ed93f71f7d9689", SpotifyAuthService.CLIENT_ID)
            assertEquals("corus://spotify-auth", SpotifyAuthService.REDIRECT_URI)
            assertEquals("corus", BuildConfig.CORUS_URL_SCHEME)
        }
    }
}
