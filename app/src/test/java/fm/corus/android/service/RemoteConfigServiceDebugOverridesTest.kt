package fm.corus.android.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import fm.corus.android.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class RemoteConfigServiceDebugOverridesTest {
    @Test fun `future flags and variants are discovered without registration`() {
        val value = mock<FirebaseRemoteConfigValue> {
            on { asString() } doReturn "false"
        }
        val variant = mock<FirebaseRemoteConfigValue> {
            on { asString() } doReturn "b"
        }
        val remote = mock<FirebaseRemoteConfig> {
            on { all } doReturn mapOf("future_experiment_enabled" to value, "future_experiment_variant" to variant)
        }
        val service = RemoteConfigService(remote, mock(), RuntimeEnvironment.getApplication())
        if (BuildConfig.DEBUG) {
            assertTrue(service.debugFeatureFlags.any { it.key == "future_experiment_enabled" })
            assertTrue(service.debugFeatureFlags.any { it.key == "future_experiment_variant" })
            val future = service.debugFeatureFlags.single { it.key == "future_experiment_enabled" }
            assertEquals("false", future.rawValue)
            assertFalse(future.allowsLocalOverride)
            service.setDebugOverride(future.key, true)
            assertNull(service.debugOverride(future.key))
            val server = service.debugFeatureFlags.single { it.namespace == "server" && it.key == "for_you_prototype_enabled" }
            assertFalse(server.allowsLocalOverride)
            assertNull(server.rawValue)
            assertEquals("Your Mix / For You", server.title)
            service.setDebugOverride(server.key, true)
            assertNull(service.debugOverride(server.key))
        }
    }

    @Test fun `local values persist and reset without polluting the RC cache`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("corus_dev_flags", 0).edit().clear().commit()
        val remoteValue = mock<FirebaseRemoteConfigValue> {
            on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
            on { asBoolean() } doReturn false
        }
        val remote = mock<FirebaseRemoteConfig> {
            on { getValue(any()) } doReturn remoteValue
            on { getBoolean(any()) } doReturn false
            on { getString(any()) } doReturn ""
        }
        val auth = mock<FirebaseAuth>()
        val service = RemoteConfigService(remote, auth, context)
        val revision = service.revision.value
        service.setDebugOverride("profile_sharing_v2", true)
        service.setDebugOverride("map_enabled", true)
        service.setDebugOverride("post_success_others_enabled", false)
        if (BuildConfig.DEBUG) {
            assertTrue(service.profileSharingV2)
            assertTrue(service.mapEnabled)
            assertFalse(service.postSuccessOthersEnabled)
            assertEquals(3, service.debugOverrideCount)
            assertTrue(service.revision.value > revision)
            val restarted = RemoteConfigService(remote, auth, context)
            assertTrue(restarted.profileSharingV2)
            assertFalse(restarted.postSuccessOthersEnabled)
            RemoteConfigService::class.java.getDeclaredMethod("cacheFeedFlags").apply {
                isAccessible = true
                invoke(service)
            }
            assertFalse(context.getSharedPreferences("corus_rc_cache", 0).getBoolean("profile_sharing_v2", true))
            assertTrue(service.profileSharingV2)
            service.setDebugOverride("profile_sharing_v2", null)
            assertFalse(service.profileSharingV2)
            assertEquals(2, service.debugOverrideCount)
            service.resetDebugOverrides()
            assertEquals(0, service.debugOverrideCount)
            assertFalse(service.mapEnabled)
            assertTrue(service.postSuccessOthersEnabled) // Original Debug preset.
        } else {
            // Even a preexisting debug preference cannot affect Release reads.
            context.getSharedPreferences("corus_dev_flags", 0).edit()
                .putBoolean("profile_sharing_v2", true).putBoolean("map_enabled", true).commit()
            assertFalse(service.profileSharingV2)
            assertFalse(service.mapEnabled)
            assertFalse(service.postSuccessOthersEnabled)
            assertTrue(service.debugFeatureFlags.isEmpty())
            assertNull(service.debugOverride("map_enabled"))
            assertEquals(revision, service.revision.value)
        }
    }
}
