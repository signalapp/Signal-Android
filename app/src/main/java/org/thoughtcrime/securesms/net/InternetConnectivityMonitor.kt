/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Proxy
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import org.signal.core.util.ServiceUtil
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.core.util.zipWithPrevious

enum class ConnectivityState {
  OFFLINE,
  ONLINE,
  ONLINE_VPN,
  BLOCKED,
  BLOCKED_VPN;

  /** Returns true if network traffic expected to reach the Internet. */
  val isOnline: Boolean
    get() = this == ONLINE || this == ONLINE_VPN
}

/**
 * Monitors internet connectivity and proxy settings changes.
 *
 * [onConnectivityUpdated] is invoked when the [ConnectivityState] changes.
 * The current state is delivered immediately upon registration if the device already has network access.
 *
 * [onProxyChanged] is invoked either the default network has changed or any network's proxy has changed.
 *
 * Both callbacks are invoked on [SignalDispatchers.IO] but may run concurrently.
 * Callers are responsible for thread safety.
 */
class InternetConnectivityMonitor(
  private val context: Context,
  private val onConnectivityUpdated: (ConnectivityState) -> Unit,
  private val onProxyChanged: () -> Unit
) {
  companion object {
    private val TAG = Log.tag(InternetConnectivityMonitor::class.java)
  }

  private val scope = CoroutineScope(SignalDispatchers.IO)
  private var monitorJob: Job? = null

  @Synchronized
  fun register() {
    if (monitorJob != null) return

    monitorJob = scope.launch {
      launch {
        connectivityStateFlow()
          .retryWhen { cause, _ ->
            val retrying = cause is NetworkStateStaleException
            Log.i(TAG, "Re-registering callback ($retrying): ${cause.message}")
            retrying
          }
          .distinctUntilChanged()
          .zipWithPrevious { prevState, state ->
            val log = buildString {
              append("Connectivity state changed: ")
              prevState?.let { append("$it -> ") }
              append(state)
            }
            Log.i(TAG, log)
            state
          }.collect { state ->
            onConnectivityUpdated(state)
          }
      }
      launch {
        proxyChangesFlow()
          .collect {
            Log.i(TAG, "Default network or system proxy config changed")
            onProxyChanged()
          }
      }
    }
  }

  @Synchronized
  fun unregister() {
    monitorJob?.cancel()
    monitorJob = null
  }

  private data class NetworkState(
    val validated: Boolean,
    val blocked: Boolean,
    val onVpn: Boolean
  ) {
    val isReachable: Boolean get() = validated && !blocked

    companion object {
      val DOWN = NetworkState(validated = false, blocked = false, onVpn = false)
    }
  }

  /**
   * Aggregates the state of all available networks to determine true Internet reachability.
   *
   * Android's default network callback only reports the single "best" network. However, to correctly
   * handle VPNs (especially VPNs with kill-switch) we must track all networks.
   *
   * Basically, this callback tracks the list of active networks with and without a VPN:
   * - No VPN / No kill-switch: Internet is available when we have a non-blocked, validated
   *   underlying network (e.g., WiFi or Cellular).
   * - With a VPN kill-switch: The system blocks direct access to underlying networks.
   *   Internet is only available if we have BOTH an underlying network AND a valid,
   *   non-blocked VPN network. If either fails, Internet access is lost.
   */
  private class NetworkAggregationCallback(
    private val onNetworkStateChanged: (NetworkState) -> Unit,
    private val onVpnLoss: () -> Unit
  ) : ConnectivityManager.NetworkCallback() {

    private val networks = mutableMapOf<Network, NetworkState>()

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
      val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
      val vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
      Log.d(TAG, "onCapabilitiesChanged($network, validated=$validated, vpn=$vpn)")
      val existing = networks[network]
      if (existing == null) {
        // For new networks, we initially assume blocked = false:
        // On API 29+, the actual status will be set by an incoming onBlockedStatusChanged callback.
        // Otherwise, onBlockedStatusChanged is unsupported and isReachable rely only on `validated`.
        networks[network] = NetworkState(validated = validated, blocked = false, onVpn = vpn)

        // API 26+ guarantees that onLinkPropertiesChanged is always called next for new networks,
        // followed by onBlockedStatusChanged (on API 29+).
        // We skip notifying here for newer APIs to avoid emitting a fast, incorrect ONLINE -> BLOCKED
        // sequence if the network was already blocked.
        if (Build.VERSION.SDK_INT < 26) {
          notifyAggregatedState()
        }
      } else {
        // For existing networks, this is a capability update, so we notify immediately.
        // onBlockedStatusChanged isn't necessary called afterward.
        networks[network] = existing.copy(validated = validated, onVpn = vpn)
        notifyAggregatedState()
      }
    }

    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
      // onBlockedStatusChanged will handle the notification on API 29+
      if (Build.VERSION.SDK_INT < 29) {
        notifyAggregatedState()
      }
    }

    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
      Log.d(TAG, "onBlockedStatusChanged($network, blocked=$blocked)")
      val existing = networks[network] ?: return
      networks[network] = existing.copy(blocked = blocked)
      notifyAggregatedState()
    }

    override fun onLost(network: Network) {
      Log.d(TAG, "onLost($network)")
      val lossState = networks.remove(network)
      if (lossState?.onVpn == true) {
        onVpnLoss()
      } else {
        notifyAggregatedState()
      }
    }

    private fun notifyAggregatedState() {
      onNetworkStateChanged(networks.bestNetworkState())
    }

    private fun Map<Network, NetworkState>.bestNetworkState(): NetworkState {
      return if (isEmpty()) {
        NetworkState.DOWN
      } else {
        // A VPN network is only considered validated if there's also an underlying
        // non-VPN network.
        val hasUnderlyingNet = values.any { !it.onVpn }
        val eligibleStates = values.map { state ->
          if (state.onVpn) {
            state.copy(validated = state.validated && hasUnderlyingNet)
          } else state
        }
        eligibleStates.maxBy { it.rank }
      }
    }

    private val NetworkState.rank: Int
      get() = when {
        isReachable && onVpn -> 4
        isReachable -> 3
        blocked && onVpn -> 2
        blocked -> 1
        else -> 0
      }
  }

  private fun connectivityStateFlow(): Flow<ConnectivityState> = callbackFlow {
    val connectivityManager = ServiceUtil.getConnectivityManager(context)
    val callback = NetworkAggregationCallback(
      onNetworkStateChanged = {
        val connectivityState = when {
          it.isReachable && it.onVpn -> ConnectivityState.ONLINE_VPN
          it.isReachable -> ConnectivityState.ONLINE
          it.blocked && it.onVpn -> ConnectivityState.BLOCKED_VPN
          it.blocked -> ConnectivityState.BLOCKED
          else -> ConnectivityState.OFFLINE
        }
        // Should not block as we conflate the flow
        trySendBlocking(connectivityState)
      },
      onVpnLoss = {
        // VPN transport disconnected. For always-on VPNs with a kill switch,
        // the underlying network may still appear "UP" but traffic is blocked.
        // Restart the flow to re-evaluate connectivity.
        close(NetworkStateStaleException("VPN loss"))
      }
    )

    val request = NetworkRequest.Builder()
      .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) // VPNs are excluded by default
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()

    connectivityManager.registerNetworkCallback(request, callback)

    awaitClose {
      connectivityManager.unregisterNetworkCallback(callback)
    }
  }.conflate()

  private fun proxyChangesFlow(): Flow<Unit> = callbackFlow {
    // Rely on the system-wide PROXY_CHANGE_ACTION sticky broadcast rather than
    // per-network LinkProperties in the NetworkCallback. This ensures we catch
    // proxy changes that occur when the system switches the default active network
    // even if the new network's properties haven't changed.
    val changeReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        trySendBlocking(Unit)
      }
    }

    ContextCompat.registerReceiver(
      context,
      changeReceiver,
      IntentFilter(Proxy.PROXY_CHANGE_ACTION),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )

    awaitClose {
      context.unregisterReceiver(changeReceiver)
    }
  }.conflate()

  /**
   * Thrown when the tracked network state is no longer reliable.
   */
  class NetworkStateStaleException(message: String) : Exception(message)
}
