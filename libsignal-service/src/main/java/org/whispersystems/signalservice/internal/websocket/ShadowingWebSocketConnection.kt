/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.websocket

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import okhttp3.Response
import okhttp3.WebSocket
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.ChatConnection
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.UnauthenticatedChatConnection
import org.whispersystems.signalservice.api.util.CredentialsProvider
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.util.whenComplete
import java.util.Optional
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A wrapper on top of [OkHttpWebSocketConnection] that sends a keep alive via
 * libsignal-net for a configurable percentage of the _successful_ web socket requests.
 *
 * Stats are collected for the shadowed requests and persisted across app restarts
 * using [org.thoughtcrime.securesms.keyvalue.InternalValues].
 *
 * When a hardcoded error threshold is reached, the user is notified to submit debug logs.
 *
 * @see [org.thoughtcrime.securesms.util.RemoteConfig.libSignalWebSocketShadowingPercentage]
 */
class ShadowingWebSocketConnection(
  name: String,
  serviceConfiguration: SignalServiceConfiguration,
  credentialsProvider: Optional<CredentialsProvider>,
  signalAgent: String,
  healthMonitor: HealthMonitor,
  allowStories: Boolean,
  private val network: Network,
  private val shadowPercentage: Int,
  private val bridge: WebSocketShadowingBridge
) : OkHttpWebSocketConnection(
  name,
  serviceConfiguration,
  credentialsProvider,
  signalAgent,
  healthMonitor,
  allowStories
) {
  private var stats: Stats = try {
    bridge.readStatsSnapshot()?.let {
      Stats.fromSnapshot(it)
    } ?: Stats()
  } catch (ex: Exception) {
    Log.w(TAG, "Failed to restore Stats from a snapshot.", ex)
    Stats()
  }
  private val canShadow: AtomicBoolean = AtomicBoolean(false)
  private val executor: ExecutorService = Executors.newSingleThreadExecutor()
  private var chatConnection: UnauthenticatedChatConnection? = null
  private var shadowingConnectPending = false

  override fun connect(): Observable<WebSocketConnectionState> {
    // NB: The potential for race conditions here was introduced when we switched from ChatService's
    //   long lived connection model to the single-use ChatConnection model.
    // At this time, we do not intend to ever use this code in production again, so I'm deferring properly
    //  fixing it with a refactor, and instead just doing the bare minimum to avoid an obvious race.
    // If we do want to use this again in production, we should probably refactor to depend on the higher level
    //   LibSignalChatConnection, rather than the lower level ChatConnection API.
    if (chatConnection == null && !shadowingConnectPending) {
      shadowingConnectPending = true
      executor.submit {
        network.connectUnauthChat(null).whenComplete(
          onSuccess = { connection ->
            shadowingConnectPending = false
            chatConnection = connection
            canShadow.set(true)
            Log.i(TAG, "Shadow socket connected.")
          },
          onFailure = {
            shadowingConnectPending = false
            canShadow.set(false)
            Log.i(TAG, "Shadow socket failed to connect.")
          }
        )
      }
    }
    return super.connect()
  }

  override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
    saveSnapshot()
    super.onClosing(webSocket, code, reason)
  }

  override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    saveSnapshot()
    super.onFailure(webSocket, t, response)
  }

  override fun disconnect() {
    executor.submit {
      chatConnection?.disconnect()?.thenApply {
        canShadow.set(false)
        Log.i(TAG, "Shadow socket disconnected.")
      }
    }
    super.disconnect()
  }

  override fun sendRequest(request: WebSocketRequestMessage): Single<WebsocketResponse> {
    return super.sendRequest(request).doOnSuccess(::sendShadow)
  }

  private fun sendShadow(actualResponse: WebsocketResponse) {
    executor.submit {
      if (canShadow.get() && Random.nextInt(100) < this.shadowPercentage) {
        libsignalKeepAlive(actualResponse)
        val snapshotAge = System.currentTimeMillis() - stats.lastSnapshot.get()
        if (snapshotAge > SNAPSHOT_INTERVAL.inWholeMilliseconds) {
          saveSnapshot()
        }
      }
    }
  }

  private fun shouldSubmitLogs(): Boolean {
    val requestsCompared = stats.requestsCompared.get()
    // Should not happen in practice, but helps avoid a division by zero later if it does.
    if (requestsCompared == 0) {
      return false
    }
    val timeSinceLastNotificationMs = System.currentTimeMillis() - stats.lastNotified.get()
    val percentFailed = stats.failures.get() * 100 / requestsCompared
    return timeSinceLastNotificationMs > FULL_DAY.inWholeMilliseconds &&
      percentFailed > FAILURE_PERCENTAGE
  }

  private fun libsignalKeepAlive(actualResponse: WebsocketResponse) {
    val connection = chatConnection ?: return
    val request = ChatConnection.Request(
      "GET",
      "/v1/keepalive",
      emptyMap(),
      ByteArray(0),
      KEEP_ALIVE_TIMEOUT.inWholeMilliseconds.toInt()
    )
    connection.send(request)
      ?.whenComplete(
        onSuccess = { response ->
          stats.requestsCompared.incrementAndGet()
          val goodStatus = (response?.status ?: -1) in 200..299
          if (!goodStatus) {
            stats.badStatuses.incrementAndGet()
          }
          Log.i(TAG, response?.message)
        },
        onFailure = {
          stats.requestsCompared.incrementAndGet()
          stats.failures.incrementAndGet()
          Log.w(TAG, "Shadow request failed: reason=[$it]")
          Log.i(TAG, "Actual response status=${actualResponse.status}")
          if (shouldSubmitLogs()) {
            Log.i(TAG, "Notification to submit logs triggered.")
            bridge.triggerFailureNotification("Experimental websocket transport failures!")
            stats.reset()
          }
        }
      )
  }

  private fun saveSnapshot() {
    executor.submit {
      Log.d(TAG, "Persisting shadowing stats snapshot.")
      bridge.writeStatsSnapshot(stats.snapshot())
    }
  }

  companion object {
    private val TAG: String = Log.tag(ShadowingWebSocketConnection::class.java)
    private val FULL_DAY: Duration = 1.days

    // If more than this percentage of shadow requests fail, the
    // notification to submit logs will be triggered.
    private const val FAILURE_PERCENTAGE: Int = 10
    private val KEEP_ALIVE_TIMEOUT: Duration = 3.seconds
    private val SNAPSHOT_INTERVAL: Duration = 10.minutes
  }

  class Stats(
    requestsCompared: Int = 0,
    failures: Int = 0,
    badStatuses: Int = 0,
    lastNotified: Long = 0
  ) {
    val requestsCompared: AtomicInteger = AtomicInteger(requestsCompared)
    val failures: AtomicInteger = AtomicInteger(failures)
    val badStatuses: AtomicInteger = AtomicInteger(badStatuses)
    val lastNotified: AtomicLong = AtomicLong(lastNotified)
    val lastSnapshot: AtomicLong = AtomicLong(0)

    fun reset() {
      requestsCompared.set(0)
      failures.set(0)
      badStatuses.set(0)
      // Do not reset lastNotified nor lastSnapshot
    }

    companion object {
      fun fromSnapshot(bytes: ByteArray): Stats {
        val snapshot = Snapshot.ADAPTER.decode(bytes)
        return Stats(snapshot.requestsCompared, snapshot.failures, snapshot.badStatuses, snapshot.lastNotified)
      }
    }

    fun snapshot(): ByteArray {
      lastSnapshot.set(System.currentTimeMillis())
      return Snapshot.Builder()
        .requestsCompared(requestsCompared.get())
        .failures(failures.get())
        .badStatuses(badStatuses.get())
        .lastNotified(lastNotified.get())
        .build()
        .encode()
    }
  }
}
