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
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.util.CredentialsProvider
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.util.whenComplete
import java.io.IOException
import java.net.SocketException
import java.time.Instant
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.signal.libsignal.net.ChatConnection.Request as LibSignalRequest

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

  // CHAT_SERVICE_LOCK: Protects state, stateChangedOrMessageReceivedCondition, chatConnection, and
  //    chatConnectionFuture
  // stateChangedOrMessageReceivedCondition: derived from CHAT_SERVICE_LOCK, used by readRequest(),
  //    exists to emulate idiosyncratic behavior of OkHttpWebSocketConnection for readRequest()
  // chatConnection: Set only when state == CONNECTED
  // chatConnectionFuture: Set only when state == CONNECTING
  private val CHAT_SERVICE_LOCK = ReentrantLock()
  private val stateChangedOrMessageReceivedCondition = CHAT_SERVICE_LOCK.newCondition()
  private var chatConnection: ChatConnection? = null
  private var chatConnectionFuture: CompletableFuture<out ChatConnection>? = null

  companion object {
    const val SERVICE_ENVELOPE_REQUEST_VERB = "PUT"
    const val SERVICE_ENVELOPE_REQUEST_PATH = "/api/v1/message"
    const val SOCKET_EMPTY_REQUEST_VERB = "PUT"
    const val SOCKET_EMPTY_REQUEST_PATH = "/api/v1/queue/empty"
    const val SIGNAL_SERVICE_ENVELOPE_TIMESTAMP_HEADER_KEY = "X-Signal-Timestamp"

    private val TAG = Log.tag(LibSignalChatConnection::class.java)

    private val KEEP_ALIVE_REQUEST = LibSignalRequest(
      "GET",
      "/v1/keepalive",
      emptyMap(),
      ByteArray(0),
      WebSocketConnection.DEFAULT_SEND_TIMEOUT.inWholeMilliseconds.toInt()
    )

    private fun WebSocketRequestMessage.toLibSignalRequest(timeout: Duration = WebSocketConnection.DEFAULT_SEND_TIMEOUT): LibSignalRequest {
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
        timeout.inWholeMilliseconds.toInt()
      )
    }
  }

  override val name = "[$name:${System.identityHashCode(this)}]"

  val state = BehaviorSubject.createDefault(WebSocketConnectionState.DISCONNECTED)

  val stateMonitor = state.subscribe { nextState ->
    if (nextState == WebSocketConnectionState.DISCONNECTED) {
      cleanup()
    }

    CHAT_SERVICE_LOCK.withLock {
      stateChangedOrMessageReceivedCondition.signalAll()
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
      chatConnectionFuture = if (credentialsProvider == null) {
        network.connectUnauthChat(listener)
      } else {
        network.connectAuthChat(credentialsProvider.username, credentialsProvider.password, receiveStories, listener)
      }
      state.onNext(WebSocketConnectionState.CONNECTING)
      // We are now in the CONNECTING state, so chatConnectionFuture should be set, and there is no
      //   nullability concern here.
      chatConnectionFuture!!.whenComplete(
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

      // OkHttpWebSocketConnection will terminate a connection if disconnect() is called while
      //   the connection itself is still CONNECTING, so we carry forward that behavior here.
      if (state.value == WebSocketConnectionState.CONNECTING) {
        // The right way to do this is to cancel the CompletableFuture returned by connectChat().
        // This will terminate forward progress on the connection attempt, and mostly closely match
        //   what OkHttpWebSocketConnection does.
        // Unfortunately, libsignal's CompletableFuture does not yet support cancellation.
        // So, instead, we set a flag to disconnect() as soon as the connection completes.
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

  override fun sendRequest(request: WebSocketRequestMessage, timeoutSeconds: Long): Single<WebsocketResponse> {
    CHAT_SERVICE_LOCK.withLock {
      if (isDead()) {
        return Single.error(IOException("$name is closed!"))
      }

      val single = SingleSubject.create<WebsocketResponse>()

      if (state.value == WebSocketConnectionState.CONNECTING) {
        // In OkHttpWebSocketConnection, if a client calls sendRequest while we are still
        //   connecting to the Chat service, we queue the request to be sent after the
        //   the connection is established.
        // We carry forward that behavior here, except we have to use future chaining
        //   rather than directly writing to the connection for it to buffer for us,
        //   because libsignal-net does not expose a connection handle until the connection
        //   is established.
        Log.i(TAG, "[sendRequest] Enqueuing request send for after connection")
        // We are in the CONNECTING state, so our invariant says that chatConnectionFuture should
        //   be set, so we should not have to worry about nullability here.
        chatConnectionFuture!!.whenComplete(
          onSuccess = {
            // We depend on the libsignal's CompletableFuture's synchronization guarantee to
            //   keep this implementation simple. If another CompletableFuture implementation is
            //   used, we'll need to add some logic here to be ensure this completion handler
            //   fires after the one enqueued in connect().
            sendRequest(request)
              .subscribe(
                { response -> single.onSuccess(response) },
                { error -> single.onError(error) }
              )
          },
          onFailure = { throwable ->
            // This matches the behavior of OkHttpWebSocketConnection when the connection fails
            //   before the buffered request can be sent.
            val downstreamThrowable = when (throwable) {
              is DeviceDeregisteredException -> NonSuccessfulResponseCodeException(403)
              else -> SocketException("Closed unexpectedly")
            }
            single.onError(downstreamThrowable)
          }
        )
        return single.subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
      }

      val internalRequest = request.toLibSignalRequest(timeout = timeoutSeconds.seconds)
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
            // The clients of WebSocketConnection are often sensitive to the exact type of exception returned.
            // This is the exception that OkHttpWebSocketConnection throws in the closest scenario to this, when
            //   the connection fails before the request completes.
            single.onError(SocketException("Failed to get response for request"))
          }
        )
      return single.subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
    }
  }

  override fun sendKeepAlive() {
    CHAT_SERVICE_LOCK.withLock {
      if (isDead()) {
        // This matches the behavior of OkHttpWebSocketConnection, where if a keep alive is sent
        //   while we are not connected, we simply drop the keep alive.
        return
      }

      if (state.value == WebSocketConnectionState.CONNECTING) {
        // Handle the special case where we are connecting, so we cannot (yet) send the keep-alive.
        // OkHttpWebSocketConnection buffers the keep alive request, and sends it when the connection
        //   completes.
        // We just checked that we are in the CONNECTING state, and we hold the CHAT_SERVICE_LOCK, so
        //   our state cannot change, thus there is no nullability concern with chatConnectionFuture.
        Log.i(TAG, "$name Buffering keep alive to send after connection establishment")
        chatConnectionFuture!!.whenComplete(
          onSuccess = {
            Log.i(TAG, "$name Sending buffered keep alive")
            // sendKeepAlive() will internally grab the CHAT_SERVICE_LOCK and check to ensure we are
            //   still in the CONNECTED state when this callback runs, so we do not need to worry about
            //   any state here.
            sendKeepAlive()
          },
          onFailure = {
            // OkHttpWebSocketConnection did not report a keep alive failure to the healthMonitor
            //   when a buffered keep alive failed to send because the underlying connection
            //   establishment failed, so neither do we.
          }
        )
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

  /**
   * Blocks until a request is received from the underlying ChatConnection.
   *
   * This method’s behavior is critical for message retrieval and must adhere to the following:
   *
   * - Blocks until a request is available.
   * - If no message is received within the specified [timeoutMillis], a [TimeoutException] is thrown.
   * - If the ChatConnection becomes disconnected while waiting, an [IOException] is thrown immediately.
   * - If invoked when the ChatConnection is dead (i.e. disconnected or failed), an [IOException] is thrown.
   * - If the ChatConnection is still in the process of connecting, the method will block until the connection
   *   is established and a message is received. The time spent waiting for the connection is counted towards
   *   the [timeoutMillis]. Should the connection attempt eventually fail, an [IOException] is thrown promptly.
   *
   * **Note:** This method is used by the MessageRetrievalThread to receive updates about the connection state
   * from other threads. Any delay in throwing exceptions could block this thread, resulting in prolonged holding
   * of the Foreground Service and wake lock, which may lead to adverse behavior by the operating system.
   *
   * @param timeoutMillis the maximum time in milliseconds to wait for a request.
   * @return the received [WebSocketRequestMessage].
   * @throws TimeoutException if the timeout elapses without receiving a message.
   * @throws IOException if the ChatConnection becomes disconnected, is dead, or if the connection attempt fails.
   */
  override fun readRequest(timeoutMillis: Long): WebSocketRequestMessage {
    if (timeoutMillis <= 0) {
      // OkHttpWebSocketConnection throws a TimeoutException in this case, so we do too.
      throw TimeoutException("Invalid timeoutMillis")
    }

    val startTime = System.currentTimeMillis()

    CHAT_SERVICE_LOCK.withLock {
      if (isDead()) {
        // Matches behavior of OkHttpWebSocketConnection
        throw IOException("Connection closed!")
      }

      var remainingTimeoutMillis = timeoutMillis

      fun couldGetRequest(): Boolean {
        return state.value == WebSocketConnectionState.CONNECTED || state.value == WebSocketConnectionState.CONNECTING
      }

      while (couldGetRequest() && incomingRequestQueue.isEmpty()) {
        if (remainingTimeoutMillis <= 0) {
          throw TimeoutException("Timeout exceeded after $timeoutMillis ms")
        }

        try {
          // This condition variable is created from CHAT_SERVICE_LOCK, and thus releases CHAT_SERVICE_LOCK
          //   while we await the condition variable.
          stateChangedOrMessageReceivedCondition.await(remainingTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) { }
        val elapsedTimeMillis = System.currentTimeMillis() - startTime
        remainingTimeoutMillis = timeoutMillis - elapsedTimeMillis
      }

      if (!incomingRequestQueue.isEmpty()) {
        return incomingRequestQueue.poll()
      } else if (!couldGetRequest()) {
        throw IOException("Connection closed!")
      } else {
        //  This happens if we somehow break out of the loop but incomingRequestQueue is empty
        //    and we were still in a state where we could get a request.
        // This *could* theoretically happen if two different threads call readRequest at the same time,
        //   this thread is the one that loses the race to take the request off the queue.
        // (NB: I don't think this is a practical issue, because readRequest() should only be called from
        //   the MessageRetrievalThread, but OkHttpWebSocketConnection treated this as a TimeoutException, so
        //   this class also dutifully treats it as a TimeoutException.)
        throw TimeoutException("Incoming request queue was empty!")
      }
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
    private val executor = Executors.newSingleThreadExecutor()

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
      // Try to not block the ChatConnectionListener callback context if we can help it.
      executor.submit {
        CHAT_SERVICE_LOCK.withLock {
          stateChangedOrMessageReceivedCondition.signalAll()
        }
      }
    }

    override fun onConnectionInterrupted(chat: ChatConnection, disconnectReason: ChatServiceException?) {
      CHAT_SERVICE_LOCK.withLock {
        Log.i(TAG, "$name connection interrupted", disconnectReason)
        chatConnection = null
        state.onNext(WebSocketConnectionState.DISCONNECTED)
      }
    }

    override fun onQueueEmpty(chat: ChatConnection) {
      Log.i(TAG, "$name queue empty")
      val internalPseudoId = nextIncomingMessageInternalPseudoId.getAndIncrement()
      val queueEmptyRequest = WebSocketRequestMessage(
        verb = SOCKET_EMPTY_REQUEST_VERB,
        path = SOCKET_EMPTY_REQUEST_PATH,
        body = ByteString.EMPTY,
        headers = listOf(),
        id = internalPseudoId
      )
      incomingRequestQueue.put(queueEmptyRequest)
      // Try to not block the ChatConnectionListener callback context if we can help it.
      executor.submit {
        CHAT_SERVICE_LOCK.withLock {
          stateChangedOrMessageReceivedCondition.signalAll()
        }
      }
    }

    override fun onReceivedAlerts(chat: ChatConnection, alerts: Array<out String>) {
      if (alerts.isNotEmpty()) {
        Log.i(TAG, "$name Received ${alerts.size} alerts from the server")
      }
    }
  }
}
