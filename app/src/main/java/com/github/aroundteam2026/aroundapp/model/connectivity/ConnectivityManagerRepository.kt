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
 * captive portal, connected but not signed in yet, counts as offline.
 */
class ConnectivityManagerRepository(context: Context) : ConnectivityRepository {
  private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

  override val isOnline: Flow<Boolean> = callbackFlow {
    // The default network the callback last reported; callbacks arrive one at a time
    var current: Network? = null
    val callback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onCapabilitiesChanged(
              network: Network,
              capabilities: NetworkCapabilities,
          ) {
            current = network
            trySend(capabilities.reachesInternet())
          }

          override fun onLost(network: Network) {
            // Switching networks may report the old one lost after the new one arrived
            if (network == current) trySend(false)
          }
        }
    // The callback reports nothing while there is no network at all, so start from now
    trySend(manager.getNetworkCapabilities(manager.activeNetwork)?.reachesInternet() == true)
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
  }
      .distinctUntilChanged()
}

private fun NetworkCapabilities.reachesInternet() =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
