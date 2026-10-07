package fm.corus.android.ui.components

import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.analytics.FirebaseAnalytics
import fm.corus.android.service.AnalyticsService
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScreenViewAnalyticsTest {
    @get:Rule val compose = createComposeRule()

    private class TestOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    @Test
    fun `retained feed only logs when its tab becomes visible`() {
        val firebase = mock<FirebaseAnalytics>()
        val analytics = AnalyticsService(firebase)
        val owner = TestOwner().apply { lifecycle.currentState = Lifecycle.State.RESUMED }
        var visible by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ScreenViewAnalytics("Feed", analytics, isVisible = visible)
            }
        }
        compose.waitForIdle()
        verify(firebase, never()).logEvent(eq("screen_view"), any<Bundle>())

        compose.runOnIdle { visible = true }
        compose.waitForIdle()
        verify(firebase, times(1)).logEvent(eq("screen_view"), any<Bundle>())
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        verify(firebase, times(1)).logEvent(eq("screen_view"), any<Bundle>())
        compose.runOnIdle { visible = true }
        compose.waitForIdle()

        val params = argumentCaptor<Bundle>()
        verify(firebase, times(2)).logEvent(eq("screen_view"), params.capture())
        assertEquals(listOf("Feed", "Feed"), params.allValues.map { it.getString("screen_name") })
    }

    @Test
    fun `background and covered destinations do not log until resumed`() {
        val firebase = mock<FirebaseAnalytics>()
        val analytics = AnalyticsService(firebase)
        val owner = TestOwner().apply { lifecycle.currentState = Lifecycle.State.CREATED }
        var visible by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ScreenViewAnalytics("Feed", analytics, isVisible = visible)
            }
        }
        compose.waitForIdle()
        verify(firebase, never()).logEvent(eq("screen_view"), any<Bundle>())

        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        verify(firebase, times(1)).logEvent(eq("screen_view"), any<Bundle>())
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.waitForIdle()
        verify(firebase, times(1)).logEvent(eq("screen_view"), any<Bundle>())
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        verify(firebase, times(2)).logEvent(eq("screen_view"), any<Bundle>())

        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        compose.runOnIdle {
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.waitForIdle()
        verify(firebase, times(2)).logEvent(eq("screen_view"), any<Bundle>())
    }
}
