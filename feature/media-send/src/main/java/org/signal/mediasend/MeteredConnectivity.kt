/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object MeteredConnectivity {

  /**
   * @return A cold [Flow] that emits `true` when the active network is metered.
   */
  @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
  fun isMetered(context: Context): Flow<Boolean> {
    val appContext = context.applicationContext

    return callbackFlow {
      val cm: ConnectivityManager = requireNotNull(ContextCompat.getSystemService(appContext, ConnectivityManager::class.java))

      fun currentMetered(): Boolean = cm.isActiveNetworkMetered

      trySend(currentMetered())

      val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          trySend(currentMetered())
        }

        override fun onLost(network: Network) {
          trySend(currentMetered())
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
          val metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
          trySend(metered)
        }
      }

      val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

      cm.registerNetworkCallback(request, callback)

      awaitClose {
        cm.unregisterNetworkCallback(callback)
      }
    }.distinctUntilChanged()
  }
}
