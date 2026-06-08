package fm.corus.android.service

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.CustomSignals
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.test.runTest
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
}
