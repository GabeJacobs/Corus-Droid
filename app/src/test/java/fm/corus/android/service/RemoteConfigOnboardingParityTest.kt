package fm.corus.android.service

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
class RemoteConfigOnboardingParityTest {
    @Test fun `numeric minimum preserves zero and rejects invalid values`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("corus_rc_cache", 0).edit().clear().commit()
        val value = mock<FirebaseRemoteConfigValue> { on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_REMOTE }
        val remote = mock<FirebaseRemoteConfig> { on { getValue("onboarding_minimum_follows") } doReturn value }
        val service = RemoteConfigService(remote, mock(), context)
        for ((raw, count) in listOf("0" to 0, "3" to 3, " 5 " to 5, "-1" to 0, "invalid" to 0, "" to 0)) {
            whenever(value.asString()).thenReturn(raw)
            assertEquals(raw, count, service.onboardingMinimumFollows)
        }
        whenever(value.source).thenReturn(FirebaseRemoteConfig.VALUE_SOURCE_STATIC)
        context.getSharedPreferences("corus_rc_cache", 0).edit().putString("onboarding_minimum_follows", "2").commit()
        assertEquals(2, service.onboardingMinimumFollows)
    }
    @Test fun `same flag can preview both designs locally and keeps its Debug default`() {
        if (!BuildConfig.DEBUG) return
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("corus_dev_flags", 0).edit().clear().commit()
        val value = mock<FirebaseRemoteConfigValue> { on { source } doReturn FirebaseRemoteConfig.VALUE_SOURCE_REMOTE }
        val remote = mock<FirebaseRemoteConfig> { on { getValue(any()) } doReturn value; on { all } doReturn emptyMap() }
        val service = RemoteConfigService(remote, mock(), context)
        assertTrue(service.revisedOnboardingTasteMatches)
        service.setDebugOverride("Revised_onboarding_taste_matches", false)
        assertFalse(service.revisedOnboardingTasteMatches)
        service.setDebugOverride("Revised_onboarding_taste_matches", true)
        assertTrue(service.revisedOnboardingTasteMatches)
        service.setDebugOverride("Revised_onboarding_taste_matches", null)
        assertTrue(service.revisedOnboardingTasteMatches)
    }
}
