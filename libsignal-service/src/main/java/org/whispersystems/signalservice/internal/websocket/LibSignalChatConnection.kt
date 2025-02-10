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
import org.signal.libsignal.internal.CompletableFuture
import org.signal.libsignal.net.AuthenticatedChatConnection
import org.signal.libsignal.net.ChatConnection
import org.signal.libsignal.net.ChatConnectionListener
import org.signal.libsignal.net.ChatServiceException
import org.signal.libsignal.net.DeviceDeregisteredException
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.UnauthenticatedChatConnection
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
import org.signal.libsignal.net.ChatConnection.Request as LibSignalRequest
import org.signal.libsignal.net.ChatConnection.Response as LibSignalResponse

/**
 * Implements the WebSocketConnection interface via libsignal-net
 *
 * Notable implementation choices:
 * - [chatConnection] contains either an authenticated or an unauthenticated connections
 * - keep-alive requests are sent on both authenticated and unauthenticated connections, mirroring the existing OkHttp behavior
 * - [org.whispersystems.signalservice.api.websocket.WebSocketConnectionState] reporting is implemented
 *   as close as possible to the original implementation in
 *   [org.whispersystems.signalservice.internal.websocket.OkHttpWebSocketConnection].
 * - we expose fake "psuedo IDs" for incoming requests so the layer on top of ours can work with IDs, just
 *    like with the old OkHttp implementation, and internally we map these IDs to AckSenders
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
  val ackSenderForInternalPseudoId = ConcurrentHashMap<Long, ChatConnectionListener.ServerMessageAck>()

  private val CHAT_SERVICE_LOCK = ReentrantLock()
  private var chatConnection: ChatConnection? = null

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

  init {
    if (credentialsProvider != null) {
      check(!credentialsProvider.username.isNullOrEmpty())
      check(!credentialsProvider.password.isNullOrEmpty())
    }
  }

  override fun connect(): Observable<WebSocketConnectionState> {
    CHAT_SERVICE_LOCK.withLock {
      if (!isDead()) {
        return state
      }
      Log.i(TAG, "$name Connecting...")
      val chatConnectionFuture: CompletableFuture<out ChatConnection> = if (credentialsProvider == null) {
        network.connectUnauthChat(listener)
      } else {
        network.connectAuthChat(credentialsProvider.username, credentialsProvider.password, receiveStories, listener)
      }
      state.onNext(WebSocketConnectionState.CONNECTING)
      chatConnectionFuture.whenComplete(
        onSuccess = { connection ->
          CHAT_SERVICE_LOCK.withLock {
            if (state.value == WebSocketConnectionState.CONNECTING) {
              chatConnection = connection
              connection?.start()
              Log.i(TAG, "$name Connected")
              state.onNext(WebSocketConnectionState.CONNECTED)
            } else {
              Log.i(TAG, "$name Dropped successful connection because we are now ${state.value}")
              disconnect()
            }
          }
        },
        onFailure = { throwable ->
          CHAT_SERVICE_LOCK.withLock {
            Log.w(TAG, "$name [connect] Failure:", throwable)
            chatConnection = null
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
        }
      )
      return state
    }
  }

  override fun isDead(): Boolean {
    CHAT_SERVICE_LOCK.withLock {
      return when (state.value) {
        WebSocketConnectionState.DISCONNECTED,
        WebSocketConnectionState.DISCONNECTING,
        WebSocketConnectionState.FAILED,
        WebSocketConnectionState.AUTHENTICATION_FAILED -> true

        WebSocketConnectionState.CONNECTING,
        WebSocketConnectionState.CONNECTED,
        WebSocketConnectionState.RECONNECTING -> false

        null -> throw IllegalStateException("LibSignalChatConnection.state can never be null")
      }
    }
  }

  override fun disconnect() {
    CHAT_SERVICE_LOCK.withLock {
      if (isDead()) {
        return
      }

      // This avoids a crash when we get a connection lost event during a connection attempt and try
      //  to cancel a connection that has not yet been fully established.
      // TODO [andrew]: Figure out if this is the right long term behavior.
      if (state.value == WebSocketConnectionState.CONNECTING) {
        // The right way to do this is to cancel the CompletableFuture returned by connectChat()
        // Unfortunately, libsignal's CompletableFuture does not yet support cancellation.
        // Instead, we set a flag to disconnect() as soon as the connection completes.
        // TODO [andrew]: Add cancellation support to CompletableFuture and use it here
        state.onNext(WebSocketConnectionState.DISCONNECTING)
        return
      }

      Log.i(TAG, "$name Disconnecting...")
      state.onNext(WebSocketConnectionState.DISCONNECTING)
      chatConnection!!.disconnect()
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
      chatConnection = null
    }
  }

  override fun sendRequest(request: WebSocketRequestMessage): Single<WebsocketResponse> {
    CHAT_SERVICE_LOCK.withLock {
      if (isDead()) {
        return Single.error(IOException("$name is closed!"))
      }

      // This avoids a crash loop when we try to send queued messages on app open before the connection
      //  is fully established.
      // TODO [andrew]: Figure out if this is the right long term behavior.
      if (state.value == WebSocketConnectionState.CONNECTING) {
        return Single.error(IOException("$name is still connecting!"))
      }

      val single = SingleSubject.create<WebsocketResponse>()
      val internalRequest = request.toLibSignalRequest()
      chatConnection!!.send(internalRequest)
        .whenComplete(
          onSuccess = { response ->
            Log.d(TAG, "$name [sendRequest] Success: ${response!!.status}")
            when (response.status) {
              in 400..599 -> {
                healthMonitor.onMessageError(
                  status = response.status,
                  isIdentifiedWebSocket = chatConnection is AuthenticatedChatConnection
                )
              }
            }
            // Here success means "we received the response" even if it is reporting an error.
            // This is consistent with the behavior of the OkHttpWebSocketConnection.
            single.onSuccess(response.toWebsocketResponse(isUnidentified = (chatConnection is UnauthenticatedChatConnection)))
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
      if (isDead()) {
        return
      }

      Log.i(TAG, "$name Sending keep alive...")
      chatConnection!!.send(KEEP_ALIVE_REQUEST)
        .whenComplete(
          onSuccess = { response ->
            Log.d(TAG, "$name [sendKeepAlive] Success")
            when (response!!.status) {
              in 200..299 -> {
                healthMonitor.onKeepAliveResponse(
                  sentTimestamp = Instant.now().toEpochMilli(), // ignored. can be any value
                  isIdentifiedWebSocket = chatConnection is AuthenticatedChatConnection
                )
              }

              in 400..599 -> {
                healthMonitor.onMessageError(response.status, (chatConnection is AuthenticatedChatConnection))
              }

              else -> {
                Log.w(TAG, "$name [sendKeepAlive] Unsupported keep alive response status: ${response.status}")
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

  private inner class LibSignalChatListener : ChatConnectionListener {
    override fun onIncomingMessage(chat: ChatConnection, envelope: ByteArray, serverDeliveryTimestamp: Long, sendAck: ChatConnectionListener.ServerMessageAck?) {
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

    override fun onConnectionInterrupted(chat: ChatConnection, disconnectReason: ChatServiceException?) {
      CHAT_SERVICE_LOCK.withLock {
        Log.i(TAG, "$name connection interrupted", disconnectReason)
        chatConnection = null
        state.onNext(WebSocketConnectionState.DISCONNECTED)
      }
    }

    override fun onQueueEmpty(chat: ChatConnection) {
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
