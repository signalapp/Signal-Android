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
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.AppForegroundObserver
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.util.SleepTimer
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.websocket.OkHttpWebSocketConnection
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SignalWebSocketHealthMonitor(
  private val sleepTimer: SleepTimer,
  private val sendKeepAlives: Boolean = true
) : HealthMonitor {

  companion object {
    private val TAG = Log.tag(SignalWebSocketHealthMonitor::class)

    private val KEEP_ALIVE_SEND_CADENCE: Duration = OkHttpWebSocketConnection.KEEPALIVE_FREQUENCY_SECONDS.seconds
    private val KEEP_ALIVE_SEND_CADENCE_BACKGROUND: Duration = 60.seconds
  }

  private val executor: Executor = Executors.newSingleThreadExecutor()

  private var webSocket: SignalWebSocket? = null

  private var keepAliveSender: KeepAliveSender? = null
  private var needsKeepAlive = false
  private var lastKeepAliveReceived: Duration = 0.seconds

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

      if (sendKeepAlives) {
        webSocket.addKeepAliveChangeListener { executor.execute(this::updateKeepAliveSenderStatus) }
      }
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
            TextSecurePreferences.setUnauthorizedReceived(AppDependencies.application, false)
          }
          failedInConnecting = false
        }
        WebSocketConnectionState.AUTHENTICATION_FAILED -> {
          if (webSocket is SignalWebSocket.AuthenticatedWebSocket) {
            TextSecurePreferences.setUnauthorizedReceived(AppDependencies.application, true)
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

      needsKeepAlive = connectionState == WebSocketConnectionState.CONNECTED && sendKeepAlives

      if (connectionState != WebSocketConnectionState.CONNECTING) {
        connectingTimeoutJob?.let {
          it.cancel()
          connectingTimeoutJob = null
        }
      }

      updateKeepAliveSenderStatus()
    }
  }

  override fun onKeepAliveResponse(sentTimestamp: Long, isIdentifiedWebSocket: Boolean) {
    val keepAliveTime = System.currentTimeMillis().milliseconds
    executor.execute {
      lastKeepAliveReceived = keepAliveTime
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

  private fun onConnectingTimeout() {
    executor.execute {
      webSocket?.forceNewWebSocket()
      failedInConnecting = true
    }
  }

  private fun updateKeepAliveSenderStatus() {
    if (keepAliveSender == null && sendKeepAlives()) {
      keepAliveSender = KeepAliveSender().also { it.start() }
    } else if (keepAliveSender != null && !sendKeepAlives()) {
      keepAliveSender?.shutdown()
      keepAliveSender = null
    }
  }

  private fun sendKeepAlives(): Boolean {
    return needsKeepAlive && webSocket?.shouldSendKeepAlives() == true
  }

  /**
   * Sends periodic heartbeats/keep-alives over the WebSocket to prevent connection timeouts. If
   * the WebSocket fails to get a return heartbeat before the next keep alive is sent, it is forced to be recreated.
   */
  private inner class KeepAliveSender : Thread() {

    @Volatile
    private var shouldKeepRunning = true

    override fun run() {
      Log.d(TAG, "[KeepAliveSender($id)] started")
      lastKeepAliveReceived = System.currentTimeMillis().milliseconds

      var keepAliveSentTime = System.currentTimeMillis().milliseconds
      var hasSentKeepAlive = false
      while (shouldKeepRunning && sendKeepAlives()) {
        try {
          val cadence = if (AppForegroundObserver.isForegrounded()) KEEP_ALIVE_SEND_CADENCE else KEEP_ALIVE_SEND_CADENCE_BACKGROUND
          sleepUntil(keepAliveSentTime + cadence)

          if (shouldKeepRunning && sendKeepAlives()) {
            if (hasSentKeepAlive && lastKeepAliveReceived < keepAliveSentTime) {
              Log.w(TAG, "Missed keep alive, last: ${lastKeepAliveReceived.inWholeMilliseconds} needed by: ${keepAliveSentTime.inWholeMilliseconds}")
              webSocket?.forceNewWebSocket()
            }

            keepAliveSentTime = System.currentTimeMillis().milliseconds
            webSocket?.sendKeepAlive()
            hasSentKeepAlive = true
          }
        } catch (e: InterruptedException) {
          // Stopped
        } catch (e: Throwable) {
          Log.w(TAG, e)
        }
      }
      Log.d(TAG, "[KeepAliveSender($id)] ended")
    }

    fun sleepUntil(time: Duration) {
      while (shouldKeepRunning && System.currentTimeMillis().milliseconds < time) {
        val waitTime = time - System.currentTimeMillis().milliseconds
        if (waitTime.isPositive()) {
          try {
            sleepTimer.sleep(waitTime.inWholeMilliseconds)
          } catch (e: InterruptedException) {
            Log.w(TAG, e)
          }
        }
      }
    }

    fun shutdown() {
      shouldKeepRunning = false
      interrupt()
    }
  }
}
