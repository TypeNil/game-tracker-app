package io.github.typenil.gametracker.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Device connectivity fact derived from the Android default network.
 *
 * Deliberately models the *device*, not any specific endpoint: an endpoint becoming
 * unreachable (stopped local BFF, dead adb reverse port) produces no transition here
 * and must not be reported as connectivity loss.
 */
enum class NetworkStatus {
    /** Not evaluated yet: the very first capability read has not completed. */
    Unknown,

    /** No default network route with validated internet access. */
    Unavailable,

    /** Default network has INTERNET and VALIDATED capabilities. */
    Available,
}

/**
 * Process-lifetime connectivity monitor.
 *
 * Ownership contract: a single [ConnectivityManager.NetworkCallback] is registered in
 * [init] and intentionally never unregistered. The owner is an application-scoped
 * singleton holding only the application context (never an Activity), the OS removes
 * the registration on process death, and there is no coroutine scope to leak because
 * callback events are pushed straight into the [StateFlow].
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private val mutableStatus = MutableStateFlow(NetworkStatus.Unknown)

    /** Late collectors observe the current state and future transitions; edges are not replayed. */
    val status: StateFlow<NetworkStatus> = mutableStatus.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        // onAvailable intentionally has no effect: capability delivery follows availability
        // on this API floor, so onCapabilitiesChanged is the only authoritative online
        // signal. The override exists to satisfy the abstract member contract.
        override fun onAvailable(network: Network) = Unit

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            mutableStatus.value = capabilities.toNetworkStatus()
        }

        override fun onLost(network: Network) {
            // A lost network is not necessarily global offline: the default route may have
            // handed over to another transport. Recompute instead of assuming Unavailable.
            recomputeDefaultNetwork()
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
        recomputeDefaultNetwork()
    }

    private fun recomputeDefaultNetwork() {
        val capabilities = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
        mutableStatus.value = capabilities.toNetworkStatus()
    }
}

/**
 * VALIDATED is required in addition to INTERNET: a captive portal advertises internet
 * capability without validated public reachability.
 */
internal fun NetworkCapabilities?.toNetworkStatus(): NetworkStatus =
    if (
        this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    ) {
        NetworkStatus.Available
    } else {
        NetworkStatus.Unavailable
    }

/**
 * Emits on every Unavailable -> Available transition. Unknown -> Available is the
 * first observation, not a recovery, and emits nothing.
 */
fun Flow<NetworkStatus>.reconnects(): Flow<Unit> = flow {
    var previous = NetworkStatus.Unknown
    collect { current ->
        if (previous == NetworkStatus.Unavailable && current == NetworkStatus.Available) {
            emit(Unit)
        }
        previous = current
    }
}

/** Suspends until the device network is Available; cancellation propagates normally. */
suspend fun NetworkMonitor.awaitOnline() {
    status.first { it == NetworkStatus.Available }
}
