package fr.zyviotv.player.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Device-level connectivity, deliberately independent from catalog or
 * Supabase results: a failed table request or a catalog restored from disk
 * does not mean the device is offline (bloc #207).
 */
enum class NetworkAvailability {
    Available,
    Unavailable,

    /** No callback received yet, or the platform refused the query. */
    Unknown,
    ;

    /** Only a confirmed absence of network may block network-only actions. */
    val allowsNetworkActions: Boolean get() = this != Unavailable

    companion object {
        fun fromCapabilities(hasInternetCapability: Boolean?): NetworkAvailability = when (hasInternetCapability) {
            true -> Available
            false -> Unavailable
            null -> Unknown
        }
    }
}

internal object NetworkAvailabilityMonitor {
    private const val TAG = "ZyvioNetwork"

    fun current(context: Context): NetworkAvailability {
        return try {
            val manager = context.getSystemService(ConnectivityManager::class.java)
                ?: return NetworkAvailability.Unknown
            val network = manager.activeNetwork ?: return NetworkAvailability.Unavailable
            val capabilities = manager.getNetworkCapabilities(network)
                ?: return NetworkAvailability.Unavailable
            NetworkAvailability.fromCapabilities(
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            )
        } catch (error: SecurityException) {
            NetworkAvailability.Unknown
        }
    }

    /** Returns an unregister action; never throws. */
    fun observe(context: Context, onChanged: (NetworkAvailability) -> Unit): () -> Unit {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return NO_OP
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                onChanged(
                    NetworkAvailability.fromCapabilities(
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    ),
                )
            }

            override fun onLost(network: Network) {
                onChanged(current(context))
            }

            override fun onUnavailable() {
                onChanged(NetworkAvailability.Unavailable)
            }
        }
        return try {
            manager.registerDefaultNetworkCallback(callback)
            val unregister: () -> Unit = {
                runCatching { manager.unregisterNetworkCallback(callback) }
            }
            unregister
        } catch (error: RuntimeException) {
            // SecurityException / TooManyRequestsException: keep the last
            // known state rather than reporting a false offline.
            Log.w(TAG, "connectivity monitor=unavailable reason=${error.javaClass.simpleName}")
            NO_OP
        }
    }

    private val NO_OP: () -> Unit = {}
}

@Composable
fun rememberNetworkAvailability(): State<NetworkAvailability> {
    val appContext = LocalContext.current.applicationContext
    val state = remember(appContext) {
        mutableStateOf(NetworkAvailabilityMonitor.current(appContext))
    }
    DisposableEffect(appContext) {
        val unregister = NetworkAvailabilityMonitor.observe(appContext) { next ->
            if (state.value != next) {
                Log.i("ZyvioNetwork", "connectivity state=${next.name.lowercase()}")
            }
            state.value = next
        }
        onDispose { unregister() }
    }
    return state
}
