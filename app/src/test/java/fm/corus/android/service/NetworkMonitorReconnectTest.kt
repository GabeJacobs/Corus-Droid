package fm.corus.android.service

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NetworkMonitorReconnectTest {

    private val connectivityManager = mock<ConnectivityManager>()
    private val context = mock<Context> {
        on { getSystemService(Context.CONNECTIVITY_SERVICE) } doReturn connectivityManager
    }

    private fun internetNetwork(): Network {
        val caps = mock<NetworkCapabilities> {
            on { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } doReturn true
        }
        val network = mock<Network>()
        whenever(connectivityManager.getNetworkCapabilities(network)) doReturn caps
        return network
    }

    private fun setActive(network: Network?) {
        whenever(connectivityManager.activeNetwork) doReturn network
    }

    private fun monitorWithCallback(): Pair<NetworkMonitor, ConnectivityManager.NetworkCallback> {
        val monitor = NetworkMonitor(context)
        val captor = argumentCaptor<ConnectivityManager.NetworkCallback>()
        verify(connectivityManager).registerNetworkCallback(any<NetworkRequest>(), captor.capture())
        return monitor to captor.firstValue
    }

    @Test
    fun `losing a redundant network stays online while another provides internet`() {
        val wifi = internetNetwork()
        val cellular = internetNetwork()
        setActive(wifi)

        val (monitor, callback) = monitorWithCallback()
        assertTrue(monitor.isConnected.value)

        callback.onLost(cellular)

        assertTrue(monitor.isConnected.value)
    }

    @Test
    fun `reconnect after a drop clears offline`() {
        setActive(null)
        val (monitor, callback) = monitorWithCallback()
        assertFalse(monitor.isConnected.value)

        val wifi = internetNetwork()
        setActive(wifi)
        callback.onAvailable(wifi)

        assertTrue(monitor.isConnected.value)
    }

    @Test
    fun `losing the only network goes offline`() {
        val wifi = internetNetwork()
        setActive(wifi)
        val (monitor, callback) = monitorWithCallback()
        assertTrue(monitor.isConnected.value)

        setActive(null)
        callback.onLost(wifi)

        assertFalse(monitor.isConnected.value)
    }
}
