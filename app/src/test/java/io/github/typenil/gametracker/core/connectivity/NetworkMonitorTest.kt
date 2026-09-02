package io.github.typenil.gametracker.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Deterministic state-machine tests: the real system service is replaced by mocks,
 * the production callback is captured at registration and driven by hand. No radio,
 * no production test seams.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NetworkMonitorTest {

    private val connectivityManager = mockk<ConnectivityManager>()
    private val context = mockk<Context>()
    private val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
    private val network = mockk<Network>()

    @Before
    fun setUp() {
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivityManager
        justRun { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) }
    }

    private fun capabilities(internet: Boolean, validated: Boolean): NetworkCapabilities =
        mockk {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns internet
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns validated
        }

    private fun noActiveNetwork() {
        every { connectivityManager.activeNetwork } returns null
        every { connectivityManager.getNetworkCapabilities(any()) } returns null
    }

    private fun activeNetworkWith(caps: NetworkCapabilities?) {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns caps
    }

    @Test
    fun `init recompute with null active network emits Unavailable`() {
        noActiveNetwork()
        val monitor = NetworkMonitor(context)
        assertEquals(NetworkStatus.Unavailable, monitor.status.value)
    }

    @Test
    fun `init recompute with validated active network emits Available without a reconnect edge`() {
        activeNetworkWith(capabilities(internet = true, validated = true))
        val monitor = NetworkMonitor(context)
        assertEquals(NetworkStatus.Available, monitor.status.value)
    }

    @Test
    fun `capabilities change to validated emits Available`() {
        noActiveNetwork()
        val monitor = NetworkMonitor(context)
        callbackSlot.captured.onAvailable(network)
        // onAvailable alone never marks online; capabilities are authoritative.
        assertEquals(NetworkStatus.Unavailable, monitor.status.value)

        callbackSlot.captured.onCapabilitiesChanged(
            network,
            capabilities(internet = true, validated = true)
        )
        assertEquals(NetworkStatus.Available, monitor.status.value)
    }

    @Test
    fun `captive portal with internet but no validation stays Unavailable`() {
        noActiveNetwork()
        val monitor = NetworkMonitor(context)
        callbackSlot.captured.onCapabilitiesChanged(
            network,
            capabilities(internet = true, validated = false)
        )
        assertEquals(NetworkStatus.Unavailable, monitor.status.value)

        callbackSlot.captured.onCapabilitiesChanged(
            network,
            capabilities(internet = true, validated = true)
        )
        assertEquals(NetworkStatus.Available, monitor.status.value)
    }

    @Test
    fun `validated loss without onLost emits Unavailable`() {
        noActiveNetwork()
        val monitor = NetworkMonitor(context)
        callbackSlot.captured.onCapabilitiesChanged(
            network,
            capabilities(internet = true, validated = true)
        )
        callbackSlot.captured.onCapabilitiesChanged(
            network,
            capabilities(internet = true, validated = false)
        )
        assertEquals(NetworkStatus.Unavailable, monitor.status.value)
    }

    @Test
    fun `handover onLost while another validated default exists does not dip`() {
        noActiveNetwork()
        val monitor = NetworkMonitor(context)
        assertEquals(NetworkStatus.Unavailable, monitor.status.value)

        val validated = capabilities(internet = true, validated = true)
        activeNetworkWith(validated)
        callbackSlot.captured.onLost(network)
        assertEquals(NetworkStatus.Available, monitor.status.value)
    }

    @Test
    fun `onLost with no remaining active network emits Unavailable`() {
        activeNetworkWith(capabilities(internet = true, validated = true))
        val monitor = NetworkMonitor(context)
        assertEquals(NetworkStatus.Available, monitor.status.value)

        noActiveNetwork()
        callbackSlot.captured.onLost(network)
        assertEquals(NetworkStatus.Unavailable, monitor.status.value)
    }

    @Test
    fun `reconnects emits exactly on Unavailable to Available edges`() = runTest {
        val status = MutableStateFlow(NetworkStatus.Unknown)
        val edges = mutableListOf<Unit>()
        val job = launch { status.reconnects().toList(edges) }

        runCurrent()
        status.value = NetworkStatus.Available // Unknown -> Available: first observation.
        runCurrent()
        assertEquals(0, edges.size)

        status.value = NetworkStatus.Unavailable
        runCurrent()
        status.value = NetworkStatus.Available
        runCurrent()
        assertEquals(1, edges.size)

        status.value = NetworkStatus.Available // No edge for a repeated state.
        runCurrent()
        assertEquals(1, edges.size)

        status.value = NetworkStatus.Unavailable
        runCurrent()
        status.value = NetworkStatus.Available
        runCurrent()
        assertEquals(2, edges.size)

        status.value = NetworkStatus.Unknown
        runCurrent()
        status.value = NetworkStatus.Available
        runCurrent()
        // Unknown interrupts a recovery sequence: Unavailable -> Unknown -> Available
        // is not an Unavailable -> Available edge.
        assertEquals(2, edges.size)

        job.cancel()
    }

    @Test
    fun `zero connectivity callbacks emit no reconnect edge`() = runTest {
        noActiveNetwork()
        val monitor = NetworkMonitor(context)
        val edges = mutableListOf<Unit>()
        val job = launch { monitor.status.reconnects().toList(edges) }
        runCurrent()
        job.cancel()
        assertEquals(0, edges.size)
    }

    @Test
    fun `awaitOnline returns once Available`() = runTest {
        noActiveNetwork()
        val monitor = NetworkMonitor(context)
        val job = launch { monitor.awaitOnline() }
        runCurrent()
        assertEquals(NetworkStatus.Unavailable, monitor.status.value)

        callbackSlot.captured.onCapabilitiesChanged(
            network,
            capabilities(internet = true, validated = true)
        )
        job.join()
        assertEquals(NetworkStatus.Available, monitor.status.value)
    }
}
