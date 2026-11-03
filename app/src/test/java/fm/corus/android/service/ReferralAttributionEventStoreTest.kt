package fm.corus.android.service

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReferralAttributionEventStoreTest {
    @Test fun `pre-login attribution survives recreation and is acknowledged only by its ID`() {
        val context = RuntimeEnvironment.getApplication()
        val store = ReferralAttributionEventStore(context)
        store.capture("recovered", true)
        val first = requireNotNull(store.pending())
        assertEquals(first, ReferralAttributionEventStore(context).pending())
        store.capture("not_found", false)
        store.acknowledge(first.id)
        val second = requireNotNull(store.pending())
        assertNotEquals(first.id, second.id)
        assertFalse(second.deferred)
        store.acknowledge(second.id)
        assertNull(store.pending())
    }

    @Test fun `expired and future attribution results are discarded`() {
        val context = RuntimeEnvironment.getApplication()
        var time = 1000L
        val store = ReferralAttributionEventStore(context) { time }
        store.capture("recovered", true)
        time += 7 * 86400000L
        assertNull(store.pending())
        store.capture("error", false)
        time--
        assertNull(store.pending())
    }
}
