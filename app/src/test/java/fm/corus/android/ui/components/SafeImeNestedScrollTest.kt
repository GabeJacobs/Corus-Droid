package fm.corus.android.ui.components

import android.app.Application
import android.graphics.Insets
import android.os.CancellationSignal
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimationController
import android.view.WindowInsetsController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SafeImeNestedScrollTest {
    private val platform = mock<WindowInsetsController>()
    private val view = mock<View> { on { windowInsetsController } doReturn platform }
    private val hidden = Insets.NONE
    private val shown = Insets.of(0, 0, 0, 300)

    private fun controller(bottom: Int = 300) = mock<WindowInsetsAnimationController> {
        on { isReady } doReturn true
        on { hiddenStateInsets } doReturn hidden
        on { shownStateInsets } doReturn shown
        on { currentInsets } doReturn Insets.of(0, 0, 0, bottom)
    }

    @Test fun disposalWhileWaitingThenLateReadyDoesNotResumeTwice() = runTest {
        val connection = ImeNestedScrollConnection(view, Density(1f)) { true }
        val fling = async(start = CoroutineStart.UNDISPATCHED) {
            connection.onPreFling(Velocity(0f, 1000f))
        }
        assertFalse(fling.isCompleted)
        val signal = argumentCaptor<CancellationSignal>()
        verify(platform).controlWindowInsetsAnimation(
            eq(WindowInsets.Type.ime()), eq(-1L), isNull(), signal.capture(), same(connection),
        )
        connection.dispose()
        assertEquals(Velocity.Zero, fling.await())
        assertTrue(signal.firstValue.isCanceled)
        // This is the callback order in the production crash: disposal resumes the waiter,
        // and the platform delivers readiness afterwards.
        val lateController = controller()
        connection.onReady(lateController, WindowInsets.Type.ime())
        connection.onCancelled(lateController)
        connection.dispose()
        verify(lateController, times(1)).finish(true)
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 40f), NestedScrollSource.UserInput))
    }

    @Test fun cancellationThenDisposalAndLateReadinessDoesNotResumeTwice() = runTest {
        val connection = ImeNestedScrollConnection(view, Density(1f)) { true }
        val fling = async(start = CoroutineStart.UNDISPATCHED) {
            connection.onPreFling(Velocity(0f, 1000f))
        }
        connection.onCancelled(null)
        assertEquals(Velocity.Zero, fling.await())
        connection.dispose()
        connection.onReady(controller(), WindowInsets.Type.ime())
    }

    @Test fun downwardDragKeepsFingerTrackingAndUpwardDragKeepsReverseTracking() {
        val connection = ImeNestedScrollConnection(view, Density(1f)) { true }
        val controller = controller(bottom = 200)
        connection.onReady(controller, WindowInsets.Type.ime())
        val down = Offset(0f, 40f)
        assertEquals(down, connection.onPreScroll(down, NestedScrollSource.UserInput))
        verify(controller).setInsetsAndAlpha(Insets.of(0, 0, 0, 160), 1f, 0f)
        val up = Offset(0f, -25f)
        assertEquals(up, connection.onPostScroll(Offset.Zero, up, NestedScrollSource.UserInput))
        verify(controller).setInsetsAndAlpha(Insets.of(0, 0, 0, 225), 1f, 0f)
        connection.dispose()
    }
}
