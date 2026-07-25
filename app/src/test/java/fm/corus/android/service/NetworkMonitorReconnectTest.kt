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
import org.mockito.kotlin.never
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

    private fun caps(hasInternet: Boolean) = mock<NetworkCapabilities> {
        on { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } doReturn hasInternet
    }

    private fun defaultCallback(): ConnectivityManager.NetworkCallback {
        val captor = argumentCaptor<ConnectivityManager.NetworkCallback>()
        verify(connectivityManager).registerDefaultNetworkCallback(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `tracks the default network, never every internet network`() {
        NetworkMonitor(context)
        verify(connectivityManager).registerDefaultNetworkCallback(any())
        verify(connectivityManager, never())
            .registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>())
    }

    @Test
    fun `default network available reports online`() {
        whenever(connectivityManager.activeNetwork) doReturn null
        val monitor = NetworkMonitor(context)
        assertFalse(monitor.isConnected.value)

        defaultCallback().onAvailable(mock())

        assertTrue(monitor.isConnected.value)
    }

    @Test
    fun `losing the default network reports offline`() {
        val network = mock<Network>()
        val internetCaps = caps(true)
        whenever(connectivityManager.activeNetwork) doReturn network
        whenever(connectivityManager.getNetworkCapabilities(network)) doReturn internetCaps
        val monitor = NetworkMonitor(context)
        assertTrue(monitor.isConnected.value)

        // Genuine disconnection with no replacement: the framework drops the
        // default network, so activeNetwork becomes null.
        whenever(connectivityManager.activeNetwork) doReturn null
        defaultCallback().onLost(network)

        assertFalse(monitor.isConnected.value)
    }

    @Test
    fun `make-before-break handover stays online when a new default is already active`() {
        val oldNetwork = mock<Network>()
        val newNetwork = mock<Network>()
        val internetCaps = caps(true)
        // Before the handover, the old network is the active default with internet.
        whenever(connectivityManager.activeNetwork) doReturn oldNetwork
        whenever(connectivityManager.getNetworkCapabilities(oldNetwork)) doReturn internetCaps
        whenever(connectivityManager.getNetworkCapabilities(newNetwork)) doReturn internetCaps
        val monitor = NetworkMonitor(context)
        val callback = defaultCallback()
        assertTrue(monitor.isConnected.value)

        // Waking from sleep, Android brings up a new default network first...
        whenever(connectivityManager.activeNetwork) doReturn newNetwork
        callback.onAvailable(newNetwork)
        assertTrue(monitor.isConnected.value)

        // ...then tears down the old one (make-before-break). A spurious onLost
        // for the replaced network must NOT pin us offline while newNetwork is up.
        callback.onLost(oldNetwork)

        assertTrue(
            "onLost for the replaced network pinned isConnected=false despite an active default",
            monitor.isConnected.value,
        )
    }

    @Test
    fun `default network without internet reports offline`() {
        val monitor = NetworkMonitor(context)

        defaultCallback().onCapabilitiesChanged(mock(), caps(false))

        assertFalse(monitor.isConnected.value)
    }
}
