/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.benchmark.network

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebSocketResponseMessage
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse
import java.net.SocketException
import java.util.LinkedList
import java.util.Optional
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A [WebSocketConnection] that provides a way to add "incoming" WebSocket payloads
 * and have client code pull them off the "wire" as they would a normal socket.
 *
 * Add messages with [addPendingMessages] and then can release them to the requestor via
 * [releaseMessages].
 */
class BenchmarkWebSocketConnection : WebSocketConnection {

  companion object {
    lateinit var instance: BenchmarkWebSocketConnection
      private set

    @Synchronized
    fun create(): WebSocketConnection {
      instance = BenchmarkWebSocketConnection()
      return instance
    }
  }

  override val name: String = "bench-${System.identityHashCode(this)}"

  private val state = BehaviorSubject.create<WebSocketConnectionState>()

  private val incomingRequests = LinkedList<WebSocketRequestMessage>()
  private val incomingSemaphore = Semaphore(0)

  @Volatile
  private var isShutdown = false

  override fun connect(): Observable<WebSocketConnectionState> {
    state.onNext(WebSocketConnectionState.CONNECTED)
    return state
  }

  override fun isDead(): Boolean {
    return false
  }

  override fun disconnect() {
    state.onNext(WebSocketConnectionState.DISCONNECTED)

    // Signal shutdown
    isShutdown = true

    val queuedThreads = incomingSemaphore.queueLength
    if (queuedThreads > 0) {
      incomingSemaphore.release(queuedThreads)
    }
  }

  override fun readRequest(timeoutMillis: Long): WebSocketRequestMessage {
    if (incomingSemaphore.tryAcquire(1, 10, TimeUnit.SECONDS)) {
      // Check if we were woken up due to shutdown
      if (isShutdown) {
        throw SocketException("WebSocket connection closed")
      }
      return incomingRequests.removeFirst()
    }

    throw TimeoutException("Timeout exceeded")
  }

  override fun readRequestIfAvailable(): Optional<WebSocketRequestMessage> {
    return if (incomingSemaphore.tryAcquire()) {
      Optional.of(incomingRequests.removeFirst())
    } else {
      Optional.empty()
    }
  }

  override fun sendResponse(response: WebSocketResponseMessage) = Unit

  fun addPendingMessages(messages: List<WebSocketRequestMessage>) {
    incomingRequests.addAll(messages)
  }

  fun releaseMessages() {
    incomingSemaphore.release(incomingRequests.size)
  }

  override fun sendRequest(
    request: WebSocketRequestMessage,
    timeoutSeconds: Long
  ): Single<WebsocketResponse> {
    error("Not yet implemented")
  }

  override fun sendKeepAlive() {
    error("Not yet implemented")
  }
}
