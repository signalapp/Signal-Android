/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.websocket

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.SignalTrace
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.push.SendMessageResponse
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
    lateinit var authInstance: BenchmarkWebSocketConnection
      private set

    @Synchronized
    fun createAuthInstance(): WebSocketConnection {
      authInstance = BenchmarkWebSocketConnection()
      return authInstance
    }

    lateinit var unauthInstance: BenchmarkWebSocketConnection
      private set

    @Synchronized
    fun createUnauthInstance(): WebSocketConnection {
      unauthInstance = BenchmarkWebSocketConnection()
      return unauthInstance
    }
  }

  override val name: String = "bench-${System.identityHashCode(this)}"

  private val state = BehaviorSubject.create<WebSocketConnectionState>()

  private val incomingRequests = LinkedList<WebSocketRequestMessage>()
  private val incomingSemaphore = Semaphore(0)

  var startWholeBatchTrace = false

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
      return getNextRequest()
    }

    throw TimeoutException("Timeout exceeded")
  }

  override fun readRequestIfAvailable(): Optional<WebSocketRequestMessage> {
    return if (incomingSemaphore.tryAcquire()) {
      Optional.of(getNextRequest())
    } else {
      Optional.empty()
    }
  }

  private fun getNextRequest(): WebSocketRequestMessage {
    if (startWholeBatchTrace) {
      startWholeBatchTrace = false
      SignalTrace.beginSection("IncomingMessageObserver#totalProcessing")
    }

    return incomingRequests.removeFirst()
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
    if (request.verb != null && request.path != null) {
      if (request.verb == "PUT" && request.path!!.startsWith("/v1/messages/")) {
        return Single.just(WebsocketResponse(200, SendMessageResponse().toJson(), emptyList<String>(), true))
      }
    }

    return Single.error(okio.IOException("fake timeout"))
  }

  override fun sendKeepAlive() = Unit

  fun addQueueEmptyMessage() {
    addPendingMessages(
      listOf(
        WebSocketRequestMessage(
          verb = "PUT",
          path = "/api/v1/queue/empty"
        )
      )
    )
  }
}

private fun Any.toJson(): String {
  return JsonUtils.toJson(this)
}
