package fm.corus.android.service

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.CustomSignals
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regression: the Android app must push the signed-in user's UID into Remote
 * Config as the `user_id` custom signal so per-user targeting conditions
 * (e.g. `app.customSignal['user_id']`) resolve. Without this, conditional
 * flags gated on specific UIDs (e.g. favorites_enabled) never light up on
 * Android — the toggle silently stays hidden. Mirrors iOS
 * RemoteConfigService.setCurrentUserSignal.
 */
class RemoteConfigServiceCustomSignalTest {

    private fun service(remoteConfig: FirebaseRemoteConfig) =
        RemoteConfigService(remoteConfig, mock<FirebaseAuth>(), mock<Context>())

    @Test
    fun `setCurrentUserSignal forwards a signed-in uid to setCustomSignals`() = runTest {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.setCustomSignals(any())).thenReturn(Tasks.forResult(null))

        service(remoteConfig).setCurrentUserSignal("uid-123")

        verify(remoteConfig).setCustomSignals(any<CustomSignals>())
    }

    @Test
    fun `setCurrentUserSignal clears the signal on sign-out (null uid)`() = runTest {
        // Passing null clears the signal — this is the sign-out path.
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.setCustomSignals(any())).thenReturn(Tasks.forResult(null))

        service(remoteConfig).setCurrentUserSignal(null)

        verify(remoteConfig).setCustomSignals(any<CustomSignals>())
    }

    /**
     * The first apply and any UID change must report "changed" so fetchAndActivate
     * can bypass the 1h fetch throttle — otherwise the cached config (evaluated
     * for a different/absent user) keeps per-user flags like favorites_enabled
     * dark for up to an hour after login.
     */
    @Test
    fun `setCurrentUserSignal reports changed on first apply and on uid switch, not on repeat`() = runTest {
        val remoteConfig = mock<FirebaseRemoteConfig>()
        whenever(remoteConfig.setCustomSignals(any())).thenReturn(Tasks.forResult(null))
        val service = service(remoteConfig)

        assertTrue(service.setCurrentUserSignal("uid-A"))   // first apply -> changed
        assertFalse(service.setCurrentUserSignal("uid-A"))  // same user   -> unchanged
        assertTrue(service.setCurrentUserSignal("uid-B"))   // switch user -> changed
        assertTrue(service.setCurrentUserSignal(null))      // sign out     -> changed
    }
}
