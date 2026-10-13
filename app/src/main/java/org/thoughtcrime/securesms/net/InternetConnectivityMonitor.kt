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
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Proxy
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Monitors internet connectivity and proxy settings changes.
 *
 * [onConnectivityUpdated] is invoked when the [ConnectivityState] or the set of tracked networks changes (a
 * transport swap leaves the old transport stale), and on the first state of every callback re-registration.
 * The current state is delivered immediately upon registration. If the OS reports no matching networks at all,
 * [ConnectivityState.OFFLINE] is reported after [INITIAL_STATE_TIMEOUT], since no callbacks will ever arrive.
 * If we lose the ability to observe connectivity entirely, we fail open and report
 * [ConnectivityState.MONITORING_UNAVAILABLE] (treated as online) while retrying in the background.
 *
 * [onProxyChanged] is invoked when the system reports a proxy change, and additionally whenever the set of
 * tracked networks or the aggregate connectivity state changes, since the proxy we read can lag the broadcast.
 * Those triggers are debounced by [PROXY_SETTLE_TIMEOUT].
 *
 * Both callbacks are invoked on [SignalDispatchers.IO] but may run concurrently.
 * Callers are responsible for thread safety.
 *
 * The callbacks run inside the monitor's own coroutine job. They must be non-suspending, and must not assume
 * the monitor keeps running if they trigger [unregister] (e.g. via a teardown/reset): cancellation is
 * cooperative, so a synchronous callback finishes and cleanup runs, but the monitor is gone afterward.
 */
class InternetConnectivityMonitor(
  private val context: Context,
  private val onConnectivityUpdated: (ConnectivityState) -> Unit,
  private val onProxyChanged: () -> Unit
) {
  companion object {
    private val TAG = Log.tag(InternetConnectivityMonitor::class.java)

    /**
     * How long a new network waits for an authoritative onBlockedStatusChanged (API 29+) before we notify
     * anyway. Keeps us from depending on OS callback ordering while still avoiding a premature ONLINE that
     * later corrects to BLOCKED.
     */
    private val NEW_NETWORK_BLOCKED_TIMEOUT = 500.milliseconds

    /** How long an online state must survive before we report it, so a burst of per-network callbacks can't read as online mid-flight. Offline is reported immediately. */
    private val ONLINE_SETTLE_TIMEOUT = 50.milliseconds

    /** How long proxy triggers must go quiet before we re-resolve, so a burst of network changes reads the settled system properties once instead of mid-transition. */
    private val PROXY_SETTLE_TIMEOUT = 500.milliseconds

    /** The OS reports existing networks immediately on registration, so silence past this means nothing matched the request and we are offline. */
    private val INITIAL_STATE_TIMEOUT = 500.milliseconds

    private val MAX_RETRY_BACKOFF = 1.minutes
  }

  private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.w(TAG, "Unexpected failure in the connectivity monitor.", throwable)
  }

  private val scope = CoroutineScope(SignalDispatchers.IO + exceptionHandler)
  private var monitorJob: Job? = null
  private val connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)!!

  // The proxy broadcast and the system properties we read the proxy from are delivered independently, so the
  // broadcast can beat the properties. Connectivity changes re-check to bound how long a stale config sticks.
  private val proxyRechecks = Channel<Unit>(Channel.CONFLATED)

  @Synchronized
  fun register() {
    if (monitorJob != null) return

    monitorJob = scope.launch {
      supervisorScope {
        launch {
          var previous: ConnectivityState? = null
          resilientConnectivityStateFlow()
            .collect { snapshot ->
              val state = snapshot.state
              Log.i(TAG, "Connectivity state changed: ${previous ?: "[none]"} -> $state  ${snapshot.summary()}")
              previous = state

              // Guard onConnectivityUpdated so a failure here can't stop connectivity updates.
              try {
                onConnectivityUpdated(state)
              } catch (e: CancellationException) {
                throw e
              } catch (e: Exception) {
                Log.w(TAG, "Failed to handle connectivity change.", e)
              }
              proxyRechecks.trySend(Unit)
            }
        }
        launch {
          merge(resilientProxyChangesFlow(), proxyRechecks.receiveAsFlow())
            .debounce(PROXY_SETTLE_TIMEOUT)
            .collect {
              // Guard onProxyChanged so a failure here can't stop proxy updates.
              try {
                onProxyChanged()
              } catch (e: CancellationException) {
                throw e
              } catch (e: Exception) {
                Log.w(TAG, "Failed to handle proxy change.", e)
              }
            }
        }
      }
    }
  }

  @Synchronized
  fun unregister() {
    monitorJob?.cancel()
    monitorJob = null
  }

  /**
   * Wraps [connectivityStateFlow] so a failure to observe connectivity never permanently stops monitoring:
   * an expected [NetworkStateStaleException] re-subscribes immediately, while any other failure fails open
   * ([ConnectivityState.MONITORING_UNAVAILABLE]) and retries with capped backoff.
   */
  private fun resilientConnectivityStateFlow(): Flow<ConnectivitySnapshot> = flow {
    var failures = 0
    var reportedUnavailable = false
    emitAll(
      connectivityStateFlow()
        .onEach {
          failures = 0
          reportedUnavailable = false
        }
        .retryWhen { cause, _ ->
          when (cause) {
            is CancellationException -> false
            is NetworkStateStaleException -> {
              Log.i(TAG, "Re-registering connectivity callback: ${cause.message}")
              true
            }
            else -> {
              failures++
              Log.w(TAG, "Connectivity monitoring failed (attempt $failures). Assuming online until it recovers.", cause)
              // Latched so a long outage emits MONITORING_UNAVAILABLE once instead of once per retry. Reset on the next successful collect.
              if (!reportedUnavailable) {
                emit(ConnectivitySnapshot(ConnectivityState.MONITORING_UNAVAILABLE, emptyMap()))
                reportedUnavailable = true
              }
              delay(retryBackoff(failures))
              true
            }
          }
        }
    )
  }

  private fun retryBackoff(attempt: Int): Duration {
    return BackoffUtil.exponentialBackoff(attempt, MAX_RETRY_BACKOFF.inWholeMilliseconds).milliseconds
  }

  /**
   * Tracks the state of every available network via [ConnectivityManager.NetworkCallback] and emits an
   * aggregated [ConnectivityState] (via [ConnectivityStateCalculator]) whenever it changes.
   *
   * All callback work and the pending-notify timers run on [scope], a single-parallelism dispatcher, so the
   * tracked state is only ever touched by one coroutine at a time, in callback order, without a lock.
   *
   * [scope] limits parallelism, not atomicity: a suspension inside a launched block releases the lane and lets
   * another callback interleave. Every block below must therefore mutate [networks]/[pendingNotifies]/[pendingBlocked] without
   * suspending. The only [delay] is at the head of the settle job, before it touches that state.
   */
  private class NetworkAggregationCallback(
    private val scope: CoroutineScope,
    private val connectivityManager: ConnectivityManager,
    private val onStateChanged: (ConnectivitySnapshot) -> Unit,
    private val onNetworksChanged: () -> Unit,
    private val onVpnLoss: () -> Unit
  ) : ConnectivityManager.NetworkCallback() {

    private val networks = mutableMapOf<Network, NetworkState>()
    private val pendingNotifies = mutableMapOf<Network, Job>()
    private val pendingBlocked = mutableMapOf<Network, Boolean>()

    // Written by the registering coroutine, cancelled from the callback lane.
    @Volatile
    private var bootstrapJob: Job? = null

    fun bootstrap() {
      bootstrapJob = scope.launch {
        delay(INITIAL_STATE_TIMEOUT)
        notifyState()
      }
    }

    private fun cancelBootstrap() {
      bootstrapJob?.cancel()
      bootstrapJob = null
    }

    override fun onAvailable(network: Network) {
      // Below API 26 a network that is already up at callback registration only gets onAvailable, never onCapabilitiesChanged.
      if (Build.VERSION.SDK_INT < 26) {
        connectivityManager.getNetworkCapabilities(network)?.let { onCapabilitiesChanged(network, it) }
      }
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
      val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
      val vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
      scope.launch {
        cancelBootstrap()
        val earlyBlocked = pendingBlocked.remove(network)
        val existing = networks[network]
        val updated = (existing ?: NetworkState(validated = false, blocked = earlyBlocked ?: false, onVpn = vpn, blockedKnown = earlyBlocked != null))
          .copy(validated = validated, onVpn = vpn)
        networks[network] = updated

        if (existing == null) {
          onNetworksChanged()
        }

        when {
          // Blocked status already known: notify now.
          updated.blockedKnown -> notifyNow(network)
          // API 29+: wait for the authoritative onBlockedStatusChanged (or the timeout).
          Build.VERSION.SDK_INT >= 29 -> scheduleNotify(network)
          // Below API 29 onBlockedStatusChanged is never delivered, so notify now.
          else -> notifyNow(network)
        }
      }
    }

    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
      scope.launch {
        val existing = networks[network]
        if (existing == null) {
          pendingBlocked[network] = blocked
          return@launch
        }

        cancelBootstrap()
        networks[network] = existing.copy(blocked = blocked, blockedKnown = true)
        notifyNow(network)
      }
    }

    override fun onLost(network: Network) {
      scope.launch {
        cancelBootstrap()
        cancelPending(network)
        pendingBlocked.remove(network)
        val lossState = networks.remove(network)
        if (lossState != null) {
          onNetworksChanged()
        }

        if (lossState?.onVpn == true) {
          onVpnLoss()
        } else {
          notifyState()
        }
      }
    }

    private fun scheduleNotify(network: Network) {
      cancelPending(network)
      pendingNotifies[network] = scope.launch {
        delay(NEW_NETWORK_BLOCKED_TIMEOUT)
        pendingNotifies.remove(network)
        networks[network]?.let { networks[network] = it.copy(blockedKnown = true) }
        notifyState()
      }
    }

    private fun notifyNow(network: Network) {
      cancelPending(network)
      notifyState()
    }

    private fun cancelPending(network: Network) {
      pendingNotifies.remove(network)?.cancel()
    }

    private fun notifyState() {
      onStateChanged(ConnectivitySnapshot(ConnectivityStateCalculator.calculate(networks.values), networks.toMap()))
    }
  }

  private fun connectivityStateFlow(): Flow<ConnectivitySnapshot> = callbackFlow {
    val callbackScope = CoroutineScope(
      SignalDispatchers.IO.limitedParallelism(1, "InternetConnectivityMonitor") + CoroutineExceptionHandler { _, throwable -> close(throwable) }
    )
    val callback = NetworkAggregationCallback(
      scope = callbackScope,
      connectivityManager = connectivityManager,
      onStateChanged = { snapshot ->
        // Should not block as we conflate the flow
        trySendBlocking(snapshot)
      },
      onNetworksChanged = {
        proxyRechecks.trySend(Unit)
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

    callback.bootstrap()
    try {
      connectivityManager.registerNetworkCallback(request, callback)
    } catch (e: Throwable) {
      callbackScope.cancel()
      throw e
    }

    awaitClose {
      connectivityManager.unregisterNetworkCallback(callback)
      callbackScope.cancel()
    }
  }.conflate()
    .debounce { if (it.state.isAssumedOnline) ONLINE_SETTLE_TIMEOUT else Duration.ZERO }
    // Keyed on networks too: a swap can hold the state (WiFi drops, cellular stays ONLINE) while the old transport goes stale.
    .distinctUntilChanged { old, new -> old.state == new.state && old.networks.keys == new.networks.keys }

  private fun resilientProxyChangesFlow(): Flow<Unit> = proxyChangesFlow()
    .retryWhen { cause, attempt ->
      if (cause is CancellationException) {
        false
      } else {
        val failures = (attempt + 1).toInt()
        Log.w(TAG, "Proxy monitoring failed (attempt $failures). Retrying.", cause)
        delay(retryBackoff(failures))
        true
      }
    }

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

    // A broadcast may have been missed while unregistered, so re-resolve on every registration.
    trySend(Unit)

    awaitClose {
      context.unregisterReceiver(changeReceiver)
    }
  }.conflate()

  /**
   * Thrown when the tracked network state is no longer reliable.
   */
  class NetworkStateStaleException(message: String) : Exception(message)

  private data class ConnectivitySnapshot(val state: ConnectivityState, val networks: Map<Network, NetworkState>) {
    fun summary(): String {
      return networks.entries.joinToString(prefix = "[", postfix = "]") { (network, state) ->
        buildString {
          append(network)
          append(if (state.validated) "=validated" else "=unvalidated")
          if (state.blocked) append(",blocked")
          if (state.onVpn) append(",vpn")
        }
      }
    }
  }
}
