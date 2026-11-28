/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.clockskew.ClockSkewDetector
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

class SignalWebSocketHealthMonitor : HealthMonitor {

  companion object {
    private val TAG = Log.tag(SignalWebSocketHealthMonitor::class)

    private const val ALERT_IDLE_PRIMARY_DEVICE = "idle-primary-device"
  }

  private val executor: Executor = Executors.newSingleThreadExecutor()

  private var webSocket: SignalWebSocket? = null

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var connectingTimeoutJob: Job? = null
  private var failedInConnecting: Boolean = false

  @Suppress("CheckResult")
  fun monitor(webSocket: SignalWebSocket) {
    executor.execute {
      check(this.webSocket == null)

      this.webSocket = webSocket

      webSocket
        .state
        .subscribeOn(Schedulers.computation())
        .observeOn(Schedulers.computation())
        .distinctUntilChanged()
        .subscribeBy { onStateChanged(it) }
    }
  }

  private fun onStateChanged(connectionState: WebSocketConnectionState) {
    executor.execute {
      Log.v(TAG, "${webSocket?.connectionName} onStateChange($connectionState)")

      when (connectionState) {
        WebSocketConnectionState.CONNECTING -> {
          connectingTimeoutJob?.cancel()
          connectingTimeoutJob = scope.launch {
            delay(if (failedInConnecting) 60.seconds else 30.seconds)
            Log.w(TAG, "${webSocket?.connectionName} Did not leave CONNECTING state, starting over")
            onConnectingTimeout()
          }
        }
        WebSocketConnectionState.CONNECTED -> {
          if (webSocket is SignalWebSocket.AuthenticatedWebSocket) {
            SignalStore.account.isUnauthorizedReceived = false
          }
          failedInConnecting = false
        }
        WebSocketConnectionState.AUTHENTICATION_FAILED -> {
          if (webSocket is SignalWebSocket.AuthenticatedWebSocket) {
            SignalStore.account.isUnauthorizedReceived = true
          }
        }
        WebSocketConnectionState.REMOTE_DEPRECATED -> {
          if (!SignalStore.misc.isClientDeprecated) {
            Log.w(TAG, "Received remote deprecation. Client version is deprecated.", true)
            SignalStore.misc.isClientDeprecated = true
          }
        }
        else -> Unit
      }

      if (connectionState != WebSocketConnectionState.CONNECTING) {
        connectingTimeoutJob?.let {
          it.cancel()
          connectingTimeoutJob = null
        }
      }
    }
  }

  override fun onMessageError(status: Int, isIdentifiedWebSocket: Boolean) {
    executor.execute {
      if (status == 499 && !SignalStore.misc.isClientDeprecated) {
        Log.w(TAG, "Received 499. Client version is deprecated.", true)
        SignalStore.misc.isClientDeprecated = true
        webSocket?.forceNewWebSocket()
      }
    }
  }

  override fun onReceivedAlerts(alerts: Array<out String>, isIdentifiedWebSocket: Boolean) {
    if (!isIdentifiedWebSocket) {
      return
    }
    executor.execute {
      SignalStore.account.hasInactivePrimaryDeviceAlert = SignalStore.account.isLinkedDevice && alerts.contains(ALERT_IDLE_PRIMARY_DEVICE)
    }
  }

  override fun onServerTimestamp(serverTimestamp: Long, isIdentifiedWebSocket: Boolean) {
    if (!isIdentifiedWebSocket) {
      return
    }
    executor.execute {
      if (!SignalStore.account.isRegistered) {
        return@execute
      }
      ClockSkewDetector.onServerTimeReceived(serverTimestamp)
    }
  }

  private fun onConnectingTimeout() {
    executor.execute {
      webSocket?.forceNewWebSocket()
      failedInConnecting = true
    }
  }
}
