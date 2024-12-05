/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.websocket

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.SingleSubject
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.AuthenticatedChatService
import org.signal.libsignal.net.ChatListener
import org.signal.libsignal.net.ChatService
import org.signal.libsignal.net.ChatServiceException
import org.signal.libsignal.net.DeviceDeregisteredException
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.UnauthenticatedChatService
import org.whispersystems.signalservice.api.util.CredentialsProvider
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.util.whenComplete
import java.io.IOException
import java.time.Instant
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds
import org.signal.libsignal.net.ChatService.Request as LibSignalRequest
import org.signal.libsignal.net.ChatService.Response as LibSignalResponse

/**
 * Implements the WebSocketConnection interface via libsignal-net
 *
 * Notable implementation choices:
 * - [chatService] contains both the authenticated and unauthenticated connections,
 *   which one to use for [sendRequest]/[sendResponse] is based on [isAuthenticated].
 * - keep-alive requests always use the [org.signal.libsignal.net.ChatService.unauthenticatedSendAndDebug]
 *   API, and log the debug info on success.
 * - regular sends use [org.signal.libsignal.net.ChatService.unauthenticatedSend] and don't create any overhead.
 * - [org.whispersystems.signalservice.api.websocket.WebSocketConnectionState] reporting is implemented
 *   as close as possible to the original implementation in
 *   [org.whispersystems.signalservice.internal.websocket.OkHttpWebSocketConnection].
 */
class LibSignalChatConnection(
  name: String,
  private val network: Network,
  private val credentialsProvider: CredentialsProvider?,
  private val receiveStories: Boolean,
  private val healthMonitor: HealthMonitor
) : WebSocketConnection {
  private val incomingRequestQueue = LinkedBlockingQueue<WebSocketRequestMessage>()

  // One of the more nasty parts of this is that libsignal-net does not expose, nor does it ever
  // intend to expose, the ID of the incoming "request" to the app layer. Instead, the app layer
  // is given a callback for each message it should call when it wants to ack that message.
  // The layer above this, SignalWebSocket.java, is written to handle HTTP Requests and Responses
  // that have responses embedded within them.
  // The goal of this stage of the project is to try and change as little as possible to isolate
  // any bugs with underlying libsignal-net layer.
  // So, we lie.
  // We assign our own "pseudo IDs" for each incoming request in this layer, provide that ID
  // up the stack to the SignalWebSocket, and then we store it. Eventually, SignalWebSocket will
  // tell us to send a response for that ID, and then we use the pseudo ID as a handle to find
  // the callback given to us earlier by libsignal-net, and we call that callback.
  private val nextIncomingMessageInternalPseudoId = AtomicLong(1)
  val ackSenderForInternalPseudoId = ConcurrentHashMap<Long, ChatListener.ServerMessageAck>()

  private val CHAT_SERVICE_LOCK = ReentrantLock()
  private var chatService: ChatService? = null

  companion object {
    const val SERVICE_ENVELOPE_REQUEST_VERB = "PUT"
    const val SERVICE_ENVELOPE_REQUEST_PATH = "/api/v1/message"
    const val SOCKET_EMPTY_REQUEST_VERB = "PUT"
    const val SOCKET_EMPTY_REQUEST_PATH = "/api/v1/queue/empty"
    const val SIGNAL_SERVICE_ENVELOPE_TIMESTAMP_HEADER_KEY = "X-Signal-Timestamp"

    private val TAG = Log.tag(LibSignalChatConnection::class.java)
    private val SEND_TIMEOUT: Long = 10.seconds.inWholeMilliseconds

    private val KEEP_ALIVE_REQUEST = LibSignalRequest(
      "GET",
      "/v1/keepalive",
      emptyMap(),
      ByteArray(0),
      SEND_TIMEOUT.toInt()
    )

    private fun WebSocketRequestMessage.toLibSignalRequest(timeout: Long = SEND_TIMEOUT): LibSignalRequest {
      return LibSignalRequest(
        this.verb?.uppercase() ?: "GET",
        this.path ?: "",
        this.headers.associate {
          val parts = it.split(':', limit = 2)
          if (parts.size != 2) {
            throw IllegalArgumentException("Headers must contain at least one colon")
          }
          parts[0] to parts[1]
        },
        this.body?.toByteArray() ?: byteArrayOf(),
        timeout.toInt()
      )
    }

    private fun LibSignalResponse.toWebsocketResponse(isUnidentified: Boolean): WebsocketResponse {
      return WebsocketResponse(
        this.status,
        this.body.decodeToString(),
        this.headers,
        isUnidentified
      )
    }
  }

  override val name = "[$name:${System.identityHashCode(this)}]"

  val state = BehaviorSubject.createDefault(WebSocketConnectionState.DISCONNECTED)

  val cleanupMonitor = state.subscribe { nextState ->
    if (nextState == WebSocketConnectionState.DISCONNECTED) {
      cleanup()
    }
  }

  private fun cleanup() {
    Log.i(TAG, "$name [cleanup]")
    incomingRequestQueue.clear()
    // There's a race condition here where someone has a request with an ack outstanding
    // when we clear the ackSender table, but it's benign because we handle the case where
    // there is no ackSender for a pseudoId gracefully in sendResponse.
    ackSenderForInternalPseudoId.clear()
    // There's no sense in resetting nextIncomingMessageInternalPseudoId.
  }

  override fun connect(): Observable<WebSocketConnectionState> {
    CHAT_SERVICE_LOCK.withLock {
      if (chatService != null) {
        return state
      }

      Log.i(TAG, "$name Connecting...")
      chatService = network.createChatService(credentialsProvider, receiveStories, listener).apply {
        state.onNext(WebSocketConnectionState.CONNECTING)
        connect().whenComplete(
          onSuccess = { debugInfo ->
            Log.i(TAG, "$name Connected")
            Log.d(TAG, "$name $debugInfo")
            state.onNext(WebSocketConnectionState.CONNECTED)
          },
          onFailure = { throwable ->
            Log.w(TAG, "$name [connect] Failure:", throwable)
            // Internally, libsignal-net will throw this DeviceDeregisteredException when the HTTP CONNECT
            // request returns HTTP 403.
            // The chat service currently does not return HTTP 401 on /v1/websocket.
            // Thus, this currently matches the implementation in OkHttpWebSocketConnection.
            if (throwable is DeviceDeregisteredException) {
              state.onNext(WebSocketConnectionState.AUTHENTICATION_FAILED)
            } else {
              state.onNext(WebSocketConnectionState.FAILED)
            }
          }
        )
      }
      return state
    }
  }

  override fun isDead(): Boolean {
    CHAT_SERVICE_LOCK.withLock {
      return chatService == null
    }
  }

  override fun disconnect() {
    CHAT_SERVICE_LOCK.withLock {
      if (chatService == null) {
        return
      }

      Log.i(TAG, "$name Disconnecting...")
      state.onNext(WebSocketConnectionState.DISCONNECTING)
      chatService!!.disconnect()
        .whenComplete(
          onSuccess = {
            Log.i(TAG, "$name Disconnected")
            state.onNext(WebSocketConnectionState.DISCONNECTED)
          },
          onFailure = { throwable ->
            Log.w(TAG, "$name Disconnect failed", throwable)
            state.onNext(WebSocketConnectionState.DISCONNECTED)
          }
        )
      chatService = null
    }
  }

  override fun sendRequest(request: WebSocketRequestMessage): Single<WebsocketResponse> {
    CHAT_SERVICE_LOCK.withLock {
      if (chatService == null) {
        return Single.error(IOException("$name is closed!"))
      }
      val single = SingleSubject.create<WebsocketResponse>()
      val internalRequest = request.toLibSignalRequest()
      chatService!!.send(internalRequest)
        .whenComplete(
          onSuccess = { response ->
            Log.d(TAG, "$name [sendRequest] Success: ${response!!.status}")
            when (response.status) {
              in 400..599 -> {
                healthMonitor.onMessageError(
                  status = response.status,
                  isIdentifiedWebSocket = chatService is AuthenticatedChatService
                )
              }
            }
            // Here success means "we received the response" even if it is reporting an error.
            // This is consistent with the behavior of the OkHttpWebSocketConnection.
            single.onSuccess(response.toWebsocketResponse(isUnidentified = (chatService is UnauthenticatedChatService)))
          },
          onFailure = { throwable ->
            Log.w(TAG, "$name [sendRequest] Failure:", throwable)
            single.onError(throwable)
          }
        )
      return single.subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
    }
  }

  override fun sendKeepAlive() {
    CHAT_SERVICE_LOCK.withLock {
      if (chatService == null) {
        return
      }

      Log.i(TAG, "$name Sending keep alive...")
      chatService!!.sendAndDebug(KEEP_ALIVE_REQUEST)
        .whenComplete(
          onSuccess = { debugResponse ->
            Log.d(TAG, "$name [sendKeepAlive] Success")
            when (debugResponse!!.response.status) {
              in 200..299 -> {
                healthMonitor.onKeepAliveResponse(
                  sentTimestamp = Instant.now().toEpochMilli(), // ignored. can be any value
                  isIdentifiedWebSocket = chatService is AuthenticatedChatService
                )
              }

              in 400..599 -> {
                healthMonitor.onMessageError(debugResponse.response.status, (chatService is AuthenticatedChatService))
              }

              else -> {
                Log.w(TAG, "$name [sendKeepAlive] Unsupported keep alive response status: ${debugResponse.response.status}")
              }
            }
          },
          onFailure = { throwable ->
            Log.w(TAG, "$name [sendKeepAlive] Failure:", throwable)
            state.onNext(WebSocketConnectionState.DISCONNECTED)
          }
        )
    }
  }

  override fun readRequestIfAvailable(): Optional<WebSocketRequestMessage> {
    val incomingMessage = incomingRequestQueue.poll()
    return Optional.ofNullable(incomingMessage)
  }

  override fun readRequest(timeoutMillis: Long): WebSocketRequestMessage {
    return readRequestInternal(timeoutMillis, timeoutMillis)
  }

  private fun readRequestInternal(timeoutMillis: Long, originalTimeoutMillis: Long): WebSocketRequestMessage {
    if (timeoutMillis < 0) {
      throw TimeoutException("No message available after $originalTimeoutMillis ms")
    }

    val startTime = System.currentTimeMillis()
    try {
      return incomingRequestQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS) ?: throw TimeoutException("No message available after $originalTimeoutMillis ms")
    } catch (e: InterruptedException) {
      val elapsedTimeMillis = System.currentTimeMillis() - startTime
      val timeoutRemainingMillis = timeoutMillis - elapsedTimeMillis
      return readRequestInternal(timeoutRemainingMillis, originalTimeoutMillis)
    }
  }

  override fun sendResponse(response: WebSocketResponseMessage) {
    if (response.status == 200 && response.message.equals("OK")) {
      ackSenderForInternalPseudoId[response.id]?.send() ?: Log.w(TAG, "$name [sendResponse] Silently dropped response without available ackSend {id: ${response.id}}")
      ackSenderForInternalPseudoId.remove(response.id)
      Log.d(TAG, "$name [sendResponse] sent ack [${response.id}]")
    } else {
      // libsignal-net only supports sending {200: OK} responses
      Log.w(TAG, "$name [sendResponse] Silently dropped unsupported response {status: ${response.status}, id: ${response.id}}")
      ackSenderForInternalPseudoId.remove(response.id)
    }
  }

  private val listener = LibSignalChatListener()

  private inner class LibSignalChatListener : ChatListener {
    override fun onIncomingMessage(chat: ChatService, envelope: ByteArray, serverDeliveryTimestamp: Long, sendAck: ChatListener.ServerMessageAck?) {
      // NB: The order here is intentional to ensure concurrency-safety, so that when a request is pulled off the queue, its sendAck is
      // already in the ackSender map, if it exists.
      val internalPseudoId = nextIncomingMessageInternalPseudoId.getAndIncrement()
      val incomingWebSocketRequest = WebSocketRequestMessage(
        verb = SERVICE_ENVELOPE_REQUEST_VERB,
        path = SERVICE_ENVELOPE_REQUEST_PATH,
        body = envelope.toByteString(),
        headers = listOf("$SIGNAL_SERVICE_ENVELOPE_TIMESTAMP_HEADER_KEY: $serverDeliveryTimestamp"),
        id = internalPseudoId
      )
      if (sendAck != null) {
        ackSenderForInternalPseudoId[internalPseudoId] = sendAck
      }
      incomingRequestQueue.put(incomingWebSocketRequest)
    }

    override fun onConnectionInterrupted(chat: ChatService, disconnectReason: ChatServiceException) {
      CHAT_SERVICE_LOCK.withLock {
        Log.i(TAG, "$name connection interrupted", disconnectReason)
        chatService = null
        state.onNext(WebSocketConnectionState.DISCONNECTED)
      }
    }

    override fun onQueueEmpty(chat: ChatService) {
      val internalPseudoId = nextIncomingMessageInternalPseudoId.getAndIncrement()
      val queueEmptyRequest = WebSocketRequestMessage(
        verb = SOCKET_EMPTY_REQUEST_VERB,
        path = SOCKET_EMPTY_REQUEST_PATH,
        body = ByteString.EMPTY,
        headers = listOf(),
        id = internalPseudoId
      )
      incomingRequestQueue.put(queueEmptyRequest)
    }
  }
}
