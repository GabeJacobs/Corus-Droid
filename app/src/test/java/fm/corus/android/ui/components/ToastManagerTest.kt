package fm.corus.android.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToastManagerTest {

    @Test
    fun `show emits non-loading event with fresh id`() = runTest {
        val received = mutableListOf<ToastManager.Event>()
        backgroundScope.launch { ToastManager.events.collect { received += it } }
        runCurrent()

        val id = ToastManager.show("hello")
        runCurrent()

        assertEquals(1, received.size)
        val event = received[0] as ToastManager.Event.Show
        assertEquals(id, event.id)
        assertEquals("hello", event.text)
        assertEquals(false, event.isLoading)
    }

    @Test
    fun `showLoading emits loading event`() = runTest {
        val received = mutableListOf<ToastManager.Event>()
        backgroundScope.launch { ToastManager.events.collect { received += it } }
        runCurrent()

        val id = ToastManager.showLoading("loading\u2026")
        runCurrent()

        val event = received.single() as ToastManager.Event.Show
        assertEquals(id, event.id)
        assertEquals("loading\u2026", event.text)
        assertEquals(true, event.isLoading)
    }

    @Test
    fun `update emits update event carrying the original id`() = runTest {
        val received = mutableListOf<ToastManager.Event>()
        backgroundScope.launch { ToastManager.events.collect { received += it } }
        runCurrent()

        val id = ToastManager.showLoading("muting\u2026")
        ToastManager.update(id, "muted")
        runCurrent()

        assertEquals(2, received.size)
        val show = received[0] as ToastManager.Event.Show
        val update = received[1] as ToastManager.Event.Update
        assertEquals(id, show.id)
        assertEquals(id, update.id)
        assertEquals("muted", update.text)
    }

    @Test
    fun `dismiss emits dismiss event for the original id`() = runTest {
        val received = mutableListOf<ToastManager.Event>()
        backgroundScope.launch { ToastManager.events.collect { received += it } }
        runCurrent()

        val id = ToastManager.showLoading("muting\u2026")
        ToastManager.dismiss(id)
        runCurrent()

        val dismiss = received.last() as ToastManager.Event.Dismiss
        assertEquals(id, dismiss.id)
    }

    @Test
    fun `each show call produces a unique id`() = runTest {
        val received = mutableListOf<ToastManager.Event>()
        backgroundScope.launch { ToastManager.events.collect { received += it } }
        runCurrent()

        val a = ToastManager.show("a")
        val b = ToastManager.show("b")
        runCurrent()

        assertNotEquals(a, b)
        assertTrue(b > a)
    }
}
