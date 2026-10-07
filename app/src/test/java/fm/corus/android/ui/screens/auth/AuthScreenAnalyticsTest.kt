package fm.corus.android.ui.screens.auth

import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.firebase.analytics.FirebaseAnalytics
import fm.corus.android.domain.HapticManager
import fm.corus.android.service.AnalyticsService
import fm.corus.android.ui.LocalHapticManager
import fm.corus.android.ui.theme.CorusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AuthScreenAnalyticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `auth screen emits its name once even when loading state recomposes it`() {
        val firebase = mock<FirebaseAnalytics>()
        val analytics = AnalyticsService(firebase)
        val loading = MutableStateFlow(false)
        val viewModel = mock<AuthViewModel> {
            on { isLoading } doReturn loading
            on { busyProvider } doReturn MutableStateFlow<String?>(null)
            on { error } doReturn MutableStateFlow<String?>(null)
            on { verificationSent } doReturn MutableStateFlow(false)
            on { analyticsService } doReturn analytics
        }
        compose.setContent {
            CorusTheme {
                CompositionLocalProvider(LocalHapticManager provides mock<HapticManager>()) {
                    AuthScreen(viewModel = viewModel)
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { loading.value = true }
        compose.waitForIdle()

        val params = argumentCaptor<Bundle>()
        verify(firebase, times(1)).logEvent(eq("screen_view"), params.capture())
        assertEquals("Auth", params.firstValue.getString("screen_name"))
    }
}
