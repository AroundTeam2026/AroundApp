// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.aroundteam2026.aroundapp.model.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A [ConnectivityRepository] backed by Android's [ConnectivityManager]. The device is online when
 * its default network has internet access that the system has validated, so a Wi-Fi behind a
 * captive portal, connected but not signed in yet, counts as offline. So does a handover to a
 * network the system hasn't validated yet, until it is: a screen may wait a moment before saying
 * the device is offline.
 */
class ConnectivityManagerRepository(context: Context) : ConnectivityRepository {
  private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

  override val isOnline: Flow<Boolean> = callbackFlow {
    // Android calls back on its own thread, while the first read below runs on the collector's
    val lock = Any()
    var reported = false
    fun report(online: Boolean) {
      synchronized(lock) {
        reported = true
        trySend(online)
      }
    }
    val callback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onCapabilitiesChanged(
              network: Network,
              capabilities: NetworkCapabilities,
          ) {
            report(capabilities.reachesInternet())
          }

          // For the default network, Android calls this only once no other network can replace it
          override fun onLost(network: Network) {
            report(false)
          }
        }
    // Registered first, so a network lost before the read below is still reported
    manager.registerDefaultNetworkCallback(callback)
    // The callback reports nothing while there is no network at all, so read the state once,
    // unless a callback came first: it is newer than this read
    synchronized(lock) {
      if (!reported) {
        trySend(manager.getNetworkCapabilities(manager.activeNetwork)?.reachesInternet() == true)
      }
    }
    awaitClose { manager.unregisterNetworkCallback(callback) }
  }
      .distinctUntilChanged()
}

private fun NetworkCapabilities.reachesInternet() =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
