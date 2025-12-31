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
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.signal.libsignal.internal.CompletableFuture
import org.signal.libsignal.net.AppExpiredException
import org.signal.libsignal.net.AuthenticatedChatConnection
import org.signal.libsignal.net.ChatConnection
import org.signal.libsignal.net.ChatConnectionListener
import org.signal.libsignal.net.ChatServiceException
import org.signal.libsignal.net.ConnectionInvalidatedException
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
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

  private data class PendingAction(
    val onConnectionSuccess: (ChatConnection) -> Unit,
    val onFailure: (Throwable) -> Unit
  )

  // CHAT_SERVICE_LOCK: Protects state, stateChangedOrMessageReceivedCondition, chatConnection,
  //    chatConnectionFuture, and requestsAwaitingConnection.
  // stateChangedOrMessageReceivedCondition: derived from CHAT_SERVICE_LOCK, used by readRequest(),
  //    exists to emulate idiosyncratic behavior of OkHttpWebSocketConnection for readRequest()
  // chatConnection: Set only when state == CONNECTED
  // chatConnectionFuture: Set only when state == CONNECTING
  private val CHAT_SERVICE_LOCK = ReentrantLock()
  private val stateChangedOrMessageReceivedCondition = CHAT_SERVICE_LOCK.newCondition()
  private var chatConnection: ChatConnection? = null
  private var chatConnectionFuture: CompletableFuture<out ChatConnection>? = null

  // pendingCallbacks should only have contents when we are transitioning to, out of, or are
  // in the CONNECTING state.
  private val pendingCallbacks = mutableListOf<PendingAction>()

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

  val stateMonitor = state
    .skip(1) // Skip the transition to the initial DISCONNECTED state
    .subscribe { nextState ->
      CHAT_SERVICE_LOCK.withLock {
        if (nextState == WebSocketConnectionState.DISCONNECTED) {
          cleanup()
        }

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

    // This is a belt-and-suspenders check, because the transition handler leaving the CONNECTING
    // state should always cleanup the pendingCallbacks, but in case we miss one, log it
    // as an error and clean it up gracefully
    if (pendingCallbacks.isNotEmpty()) {
      Log.w(TAG, "$name [cleanup] ${pendingCallbacks.size} pendingCallbacks during cleanup! This is probably a bug.")
      pendingCallbacks.forEach { pending ->
        pending.onFailure(SocketException("Connection terminated unexpectedly"))
      }
      pendingCallbacks.clear()
    }
  }

  init {
    if (credentialsProvider != null) {
      check(!credentialsProvider.username.isNullOrEmpty())
      check(!credentialsProvider.password.isNullOrEmpty())
    }
  }

  private fun sendRequestInternal(request: WebSocketRequestMessage, timeoutSeconds: Long, single: SingleSubject<WebsocketResponse>) {
    CHAT_SERVICE_LOCK.withLock {
      check(state.value == WebSocketConnectionState.CONNECTED)

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
            val downstreamThrowable = when (throwable) {
              is ConnectionInvalidatedException -> NonSuccessfulResponseCodeException(4401)
              // The clients of WebSocketConnection are often sensitive to the exact type of exception returned.
              // This is the exception that OkHttpWebSocketConnection throws in the closest scenario to this, when
              //   the connection fails before the request completes.
              else -> SocketException("Failed to get response for request")
            }
            single.onError(downstreamThrowable)
          }
        )
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
          handleConnectionSuccess(connection!!)
        },
        onFailure = { throwable ->
          handleConnectionFailure(throwable)
        }
      )
      return state
    }
  }

  private fun handleConnectionSuccess(connection: ChatConnection) {
    CHAT_SERVICE_LOCK.withLock {
      when (state.value) {
        WebSocketConnectionState.CONNECTING -> {
          chatConnection = connection
          chatConnection?.start()
          Log.i(TAG, "$name Connected")
          state.onNext(WebSocketConnectionState.CONNECTED)

          pendingCallbacks.forEach { pending ->
            runCatching { pending.onConnectionSuccess(connection) }
              .onFailure { e ->
                Log.w(TAG, "$name [handleConnectionSuccess] Failed to execute pending action", e)
                pending.onFailure(e)
              }
          }

          pendingCallbacks.clear()
        }
        else -> {
          Log.i(TAG, "$name Dropped successful connection because we are now ${state.value}")
          disconnect()
        }
      }
    }
  }

  private fun handleConnectionFailure(throwable: Throwable) {
    CHAT_SERVICE_LOCK.withLock {
      if (throwable is CancellationException) {
        // We should have transitioned to DISCONNECTED immediately after we canceled chatConnectionFuture
        check(state.value == WebSocketConnectionState.DISCONNECTED)
        Log.i(TAG, "$name [connect] cancelled")
        return
      }

      Log.w(TAG, "$name [connect] Failure:", throwable)
      chatConnection = null

      // Internally, libsignal-net will throw this DeviceDeregisteredException when the HTTP CONNECT
      // request returns HTTP 403.
      // The chat service currently does not return HTTP 401 on /v1/websocket.
      // Thus, this currently matches the implementation in OkHttpWebSocketConnection.
      when (throwable) {
        is DeviceDeregisteredException -> {
          state.onNext(WebSocketConnectionState.AUTHENTICATION_FAILED)
        }
        is AppExpiredException -> {
          state.onNext(WebSocketConnectionState.REMOTE_DEPRECATED)
        }
        else -> {
          Log.w(TAG, "Unknown connection failure reason", throwable)
          state.onNext(WebSocketConnectionState.FAILED)
        }
      }

      val downstreamThrowable = when (throwable) {
        is DeviceDeregisteredException -> NonSuccessfulResponseCodeException(403)
        // This is just to match what OkHttpWebSocketConnection does in the case a pending request fails
        // due to the underlying transport refusing to open.
        else -> SocketException("Closed unexpectedly")
      }

      pendingCallbacks.forEach { pending ->
        pending.onFailure(downstreamThrowable)
      }
      pendingCallbacks.clear()
    }
  }

  override fun isDead(): Boolean {
    CHAT_SERVICE_LOCK.withLock {
      return when (state.value) {
        WebSocketConnectionState.DISCONNECTED,
        WebSocketConnectionState.DISCONNECTING,
        WebSocketConnectionState.FAILED,
        WebSocketConnectionState.AUTHENTICATION_FAILED,
        WebSocketConnectionState.REMOTE_DEPRECATED -> true

        WebSocketConnectionState.CONNECTING,
        WebSocketConnectionState.CONNECTED -> false

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
        Log.i(TAG, "$name Cancelling connection attempt...")
        // This is safe because we just checked that state == CONNECTING
        chatConnectionFuture!!.cancel(true)
        state.onNext(WebSocketConnectionState.DISCONNECTED)
        return
      }

      Log.i(TAG, "$name Disconnecting...")
      state.onNext(WebSocketConnectionState.DISCONNECTING)
      chatConnection!!.disconnect()
        .whenComplete(
          onSuccess = {
            // This future completion means the WebSocket close frame has been sent off, but we
            //   have not yet received a close frame back from the server.
            // To match the behavior of OkHttpWebSocketConnection, we should transition to DISCONNECTED
            //   only when we get the close frame back from the server, which happens when
            //   onConnectionInterrupted is called.
          },
          onFailure = { throwable ->
            // We failed to write the close frame to the server? Something is very wrong, give up and tear down.
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
        // Match OkHttpWebSocketConnection by throwing here.
        throw IOException("$name is closed!")
      }

      val single = SingleSubject.create<WebsocketResponse>()

      return when (state.value) {
        WebSocketConnectionState.CONNECTING -> {
          Log.i(TAG, "[sendRequest] Enqueuing request send for after connection")
          pendingCallbacks.add(
            PendingAction(
              onConnectionSuccess = { _ -> sendRequestInternal(request, timeoutSeconds, single) },
              onFailure = { error -> single.onError(error) }
            )
          )
          single
        }
        WebSocketConnectionState.CONNECTED -> {
          sendRequestInternal(request, timeoutSeconds, single)
          single
        }
        else -> {
          throw IllegalStateException("LibSignalChatConnection.state was neither dead, CONNECTING, or CONNECTED.")
        }
      }.subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
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

  @Throws(IOException::class)
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

  @OptIn(InternalCoroutinesApi::class)
  override suspend fun <T> runWithChatConnection(callback: (ChatConnection) -> T): T = suspendCancellableCoroutine { continuation ->
    CHAT_SERVICE_LOCK.withLock {
      when (state.value) {
        WebSocketConnectionState.CONNECTED -> {
          try {
            val result = callback(chatConnection!!)
            continuation.resume(result)
          } catch (e: Exception) {
            continuation.resumeWithException(e)
          }
        }

        WebSocketConnectionState.CONNECTING -> {
          val action = PendingAction(
            onConnectionSuccess = { connection ->
              CHAT_SERVICE_LOCK.withLock {
                try {
                  val result = callback(connection)
                  // NB: We use the experimental tryResume* methods here to avoid crashing if the continuation is
                  // canceled before we finish the connection attempt, but the PendingAction cannot be removed from
                  // pendingActions before we get to executing it.
                  continuation.tryResume(result)?.let(continuation::completeResume)
                } catch (e: Throwable) {
                  continuation.tryResumeWithException(e)?.let(continuation::completeResume)
                }
              }
            },
            onFailure = { error ->
              continuation.tryResumeWithException(error)?.let(continuation::completeResume)
            }
          )
          pendingCallbacks.add(action)

          continuation.invokeOnCancellation {
            CHAT_SERVICE_LOCK.withLock {
              pendingCallbacks.removeIf { it === action }
            }
          }
        }
        else -> {
          continuation.resumeWithException(IOException("WebSocket is not connected (state: ${state.value})"))
        }
      }
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
        if (disconnectReason == null) {
          // disconnectReason = null means we requested this disconnect earlier, and this is confirmation
          //   that disconnection is complete.
          Log.i(TAG, "$name disconnected")
        } else {
          Log.i(TAG, "$name connection unexpectedly closed", disconnectReason)
        }
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
