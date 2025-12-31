/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.websocket

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.libsignal.internal.CompletableFuture
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.UnauthenticatedChatConnection
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.messages.EnvelopeResponse
import org.whispersystems.signalservice.api.util.SleepTimer
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebSocketResponseMessage
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private typealias Listener = () -> Unit

/**
 * Base wrapper around a [WebSocketConnection] to provide a more developer friend interface to websocket
 * interactions.
 */
sealed class SignalWebSocket(
  private val connectionFactory: WebSocketFactory,
  private val canConnect: CanConnect,
  val sleepTimer: SleepTimer,
  private val disconnectTimeout: Duration
) {

  companion object {
    private val TAG = Log.tag(SignalWebSocket::class)

    const val SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp"

    const val FOREGROUND_KEEPALIVE = "Foregrounded"
  }

  private var connection: WebSocketConnection? = null
  val connectionName
    get() = connection?.name ?: "[null]"

  private val _state: BehaviorSubject<WebSocketConnectionState> = BehaviorSubject.createDefault(WebSocketConnectionState.DISCONNECTED)
  protected var disposable: CompositeDisposable = CompositeDisposable()

  private val keepAliveTokens: MutableSet<String> = CopyOnWriteArraySet()
  private val keepAliveChangeListeners: MutableSet<Listener> = CopyOnWriteArraySet()

  private var delayedDisconnectThread: DelayedDisconnectThread? = null

  val state: Observable<WebSocketConnectionState> = _state
  val stateSnapshot: WebSocketConnectionState
    get() = _state.value!!

  /**
   * Indicate that WebSocketConnection can now be made and attempt to connect.
   */
  @Synchronized
  @Throws(WebSocketUnavailableException::class)
  fun connect() {
    getWebSocket()
  }

  /**
   * Indicate that WebSocketConnection can no longer be made and disconnect.
   */
  @Synchronized
  fun disconnect() {
    if (connection != null) {
      disposable.dispose()

      connection!!.disconnect()
      connection = null

      if (!_state.value!!.isFailure) {
        _state.onNext(WebSocketConnectionState.DISCONNECTED)
      }
    }
  }

  @Synchronized
  @Throws(IOException::class)
  fun sendKeepAlive() {
    if (canConnect.canConnect()) {
      Log.v(TAG, "$connectionName keepAliveTokens: $keepAliveTokens")
      getWebSocket().sendKeepAlive()
    }
  }

  fun shouldSendKeepAlives(): Boolean {
    return keepAliveTokens.isNotEmpty()
  }

  fun registerKeepAliveToken(token: String) {
    val changed = keepAliveTokens.add(token)
    if (changed) {
      Log.v(TAG, "$connectionName Adding keepAliveToken: $token, current: $keepAliveTokens")
    }

    synchronized(this) {
      delayedDisconnectThread?.abort()
      delayedDisconnectThread = null

      if (canConnect.canConnect()) {
        try {
          connect()
        } catch (e: WebSocketUnavailableException) {
          Log.w(TAG, "$connectionName Keep alive requested, but connection not available", e)
        }
      } else {
        Log.w(TAG, "$connectionName Keep alive requested, but connection not available")
      }
    }

    if (changed) {
      keepAliveChangeListeners.forEach { it() }
    }
  }

  fun removeKeepAliveToken(token: String) {
    if (keepAliveTokens.remove(token)) {
      Log.v(TAG, "$connectionName Removing keepAliveToken: $token, remaining: $keepAliveTokens")
      synchronized(this) {
        startDelayedDisconnectIfNecessary()
      }
    }

    keepAliveChangeListeners.forEach { it() }
  }

  fun addKeepAliveChangeListener(listener: Listener) {
    keepAliveChangeListeners.add(listener)
  }

  fun request(request: WebSocketRequestMessage): Single<WebsocketResponse> {
    return try {
      delayedDisconnectThread?.resetLastInteractionTime()
      getWebSocket().sendRequest(request)
    } catch (e: IOException) {
      Single.error(e)
    }
  }

  fun request(request: WebSocketRequestMessage, timeout: Duration): Single<WebsocketResponse> {
    return try {
      delayedDisconnectThread?.resetLastInteractionTime()
      getWebSocket().sendRequest(request, timeout.inWholeSeconds)
    } catch (e: IOException) {
      Single.error(e)
    }
  }

  @Throws(IOException::class)
  fun sendAck(response: EnvelopeResponse) {
    getWebSocket().sendResponse(response.websocketRequest.getWebSocketResponse())
  }

  /**
   * Executes the given callback with the underlying libsignal chat connection when available.
   *
   * This is only supported for LibSignal-based connections.
   *
   * @param callback The callback to execute with the connection. Should be very quick and
   *                 non-blocking, because it may block other operations on that connection.
   */
  suspend fun <T> runWithChatConnection(callback: (org.signal.libsignal.net.ChatConnection) -> T): T {
    return getWebSocket().runWithChatConnection(callback)
  }

  @Synchronized
  @Throws(WebSocketUnavailableException::class)
  protected fun getWebSocket(): WebSocketConnection {
    if (!canConnect.canConnect()) {
      throw WebSocketUnavailableException()
    }

    if (connection == null || connection?.isDead() == true) {
      disposable.dispose()

      disposable = CompositeDisposable()
      val newConnection = connectionFactory.createConnection()

      newConnection
        .connect()
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .subscribeBy { _state.onNext(it) }
        .addTo(disposable)

      this.connection = newConnection

      startDelayedDisconnectIfNecessary()
    }

    return connection!!
  }

  private fun startDelayedDisconnectIfNecessary() {
    if (connection.isAlive() && keepAliveTokens.isEmpty()) {
      delayedDisconnectThread?.abort()
      delayedDisconnectThread = DelayedDisconnectThread().also { it.start() }
    }
  }

  @Synchronized
  fun forceNewWebSocket() {
    Log.i(TAG, "$connectionName Forcing new WebSocket, canConnect: ${canConnect.canConnect()}")
    disconnect()
  }

  /**
   * Allow the WebSocket to self destruct if there are no keep alive tokens and it's been longer
   * than [disconnectTimeout] since the last request was made.
   */
  private inner class DelayedDisconnectThread : Thread() {
    private var abort = false

    @Volatile
    private var lastInteractionTime = Duration.ZERO

    fun abort() {
      if (!abort && isAlive) {
        Log.v(TAG, "$connectionName Scheduled disconnect aborted.")
        abort = true
        interrupt()
      }
    }

    fun resetLastInteractionTime() {
      lastInteractionTime = System.currentTimeMillis().milliseconds
    }

    override fun run() {
      lastInteractionTime = System.currentTimeMillis().milliseconds
      try {
        while (!abort && (lastInteractionTime + disconnectTimeout) > System.currentTimeMillis().milliseconds) {
          val now = System.currentTimeMillis().milliseconds
          if (lastInteractionTime > now) {
            lastInteractionTime = now
          }
          val sleepDuration = (lastInteractionTime + disconnectTimeout) - now
          if (sleepDuration.isPositive()) {
            Log.v(TAG, "$connectionName Disconnect scheduled in $sleepDuration")
            sleepTimer.sleep(sleepDuration.inWholeMilliseconds)
          }
        }
      } catch (_: InterruptedException) { }

      if (!abort && !shouldSendKeepAlives()) {
        disconnect()
      }
    }
  }

  private fun WebSocketConnection?.isAlive(): Boolean {
    return this?.isDead() == false
  }

  protected fun WebSocketRequestMessage.isSignalServiceEnvelope(): Boolean {
    return "PUT" == this.verb && "/api/v1/message" == this.path
  }

  protected fun WebSocketRequestMessage.isSocketEmptyRequest(): Boolean {
    return "PUT" == this.verb && "/api/v1/queue/empty" == this.path
  }

  private fun WebSocketRequestMessage.getWebSocketResponse(): WebSocketResponseMessage {
    return if (this.isSignalServiceEnvelope()) {
      WebSocketResponseMessage.Builder()
        .id(this.id)
        .status(200)
        .message("OK")
        .build()
    } else {
      WebSocketResponseMessage.Builder()
        .id(this.id)
        .status(400)
        .message("Unknown")
        .build()
    }
  }

  /**
   * WebSocket type for communicating with the server without authenticating. Also known as "unidentified".
   */
  class UnauthenticatedWebSocket(connectionFactory: WebSocketFactory, canConnect: CanConnect, sleepTimer: SleepTimer, disconnectTimeoutMs: Long) : SignalWebSocket(connectionFactory, canConnect, sleepTimer, disconnectTimeoutMs.milliseconds) {
    fun request(requestMessage: WebSocketRequestMessage, sealedSenderAccess: SealedSenderAccess): Single<WebsocketResponse> {
      val headers: MutableList<String> = requestMessage.headers.toMutableList()
      if (sealedSenderAccess.applyHeader()) {
        headers.add(sealedSenderAccess.header)
      }

      val message = requestMessage
        .newBuilder()
        .headers(headers)
        .build()

      try {
        return request(message)
          .flatMap<WebsocketResponse> { response ->
            if (response.status == 401) {
              val fallback = sealedSenderAccess.switchToFallback()
              if (fallback != null) {
                return@flatMap request(requestMessage, fallback)
              }
            }
            Single.just(response)
          }
      } catch (e: IOException) {
        return Single.error(e)
      }
    }

    suspend fun <Result, Error : BadRequestError> runCatchingWithUnauthChatConnection(
      callback: (UnauthenticatedChatConnection) -> CompletableFuture<RequestResult<Result, Error>>
    ): CompletableFuture<RequestResult<Result, Error>> {
      val requestFuture = try {
        getWebSocket().runWithChatConnection { chatConnection ->
          val unauthenticatedConnection = chatConnection as? UnauthenticatedChatConnection
            ?: throw IllegalStateException("Expected unauthenticated chat connection but got ${chatConnection::class.java.simpleName}")
          callback(unauthenticatedConnection)
        }
      } catch (throwable: Throwable) {
        return CompletableFuture.completedFuture(throwable.toNetworkRequestResult())
      }

      return requestFuture.handle { result, throwable ->
        when {
          throwable != null -> throwable.toNetworkRequestResult()
          result != null -> result
          else -> RequestResult.ApplicationError(IllegalStateException("RequestResult was null"))
        }
      }
    }
  }

  /**
   * WebSocket type for communicating with the server with authentication. Also known as "identified".
   */
  class AuthenticatedWebSocket(connectionFactory: WebSocketFactory, canConnect: CanConnect, sleepTimer: SleepTimer, disconnectTimeoutMs: Long) : SignalWebSocket(connectionFactory, canConnect, sleepTimer, disconnectTimeoutMs.milliseconds) {

    /**
     * The reads a batch of messages off of the websocket.
     *
     * Rather than just provide you the batch as a return value, it will invoke the provided callback with the
     * batch as an argument. If you are able to successfully process them, this method will then ack all of the
     * messages so that they won't be re-delivered in the future.
     *
     * The return value of this method is a boolean indicating whether or not there are more messages in the
     * queue to be read (true if there's still more, or false if you've drained everything).
     *
     * However, this return value is only really useful the first time you read from the websocket. That's because
     * the websocket will only ever let you know if it's drained *once* for any given connection. So if this method
     * returns false, a subsequent call while using the same websocket connection will simply block until we either
     * get a new message or hit the timeout.
     *
     * Concerning the requested batch size, it's worth noting that this is simply an upper bound. This method will
     * not wait extra time until the batch has "filled up". Instead, it will wait for a single message, and then
     * take any extra messages that are also available up until you've hit your batch size.
     */
    @Throws(TimeoutException::class, WebSocketUnavailableException::class, IOException::class)
    fun readMessageBatch(timeout: Long, batchSize: Int, callback: MessageReceivedCallback): Boolean {
      val responses: MutableList<EnvelopeResponse> = ArrayList()
      var hitEndOfQueue = false

      val firstEnvelope: EnvelopeResponse? = waitForSingleMessage(timeout)

      if (firstEnvelope != null) {
        responses.add(firstEnvelope)
      } else {
        hitEndOfQueue = true
      }

      if (!hitEndOfQueue) {
        for (i in 1 until batchSize) {
          val request = getWebSocket().readRequestIfAvailable().orNull()

          if (request != null) {
            if (request.isSignalServiceEnvelope()) {
              responses.add(request.toEnvelopeResponse())
            } else if (request.isSocketEmptyRequest()) {
              hitEndOfQueue = true
              break
            }
          } else {
            break
          }
        }
      }

      if (responses.size > 0) {
        callback.onMessageBatch(responses)
      }

      return !hitEndOfQueue
    }

    @Throws(TimeoutException::class, WebSocketUnavailableException::class, IOException::class)
    private fun waitForSingleMessage(timeout: Long): EnvelopeResponse? {
      while (true) {
        val request = getWebSocket().readRequest(timeout)

        if (request.isSignalServiceEnvelope()) {
          return request.toEnvelopeResponse()
        } else if (request.isSocketEmptyRequest()) {
          return null
        }
      }
    }

    @Throws(IOException::class)
    private fun WebSocketRequestMessage.toEnvelopeResponse(): EnvelopeResponse {
      val timestamp = this.findHeader()

      if (timestamp == null) {
        Log.w(TAG, "Failed to parse $SERVER_DELIVERED_TIMESTAMP_HEADER")
      }

      val envelope = Envelope.ADAPTER.decode(this.body!!.toByteArray())

      return EnvelopeResponse(envelope, timestamp ?: 0, this)
    }

    private fun WebSocketRequestMessage.findHeader(): Long? {
      if (this.headers.isEmpty()) {
        return null
      }

      return this.headers
        .asSequence()
        .filter { it.startsWith(SERVER_DELIVERED_TIMESTAMP_HEADER) }
        .map { it.split(":") }
        .filter { it.size == 2 && it[0].trim().lowercase() == SERVER_DELIVERED_TIMESTAMP_HEADER.lowercase() }
        .map { it[1].trim() }
        .filter { it.isNotEmpty() }
        .firstOrNull()
        ?.toLongOrNull()
    }

    /**
     * For receiving a callback when a new message has been
     * received.
     */
    fun interface MessageReceivedCallback {
      /** Called with the batch of envelopes. You are responsible for sending acks.  */
      fun onMessageBatch(envelopeResponses: List<EnvelopeResponse>)
    }
  }

  fun interface CanConnect {
    fun canConnect(): Boolean
  }
}

private fun <T, E : BadRequestError> Throwable.toNetworkRequestResult(): RequestResult<T, E> {
  val cause = if (this is CompletionException && this.cause != null) {
    this.cause!!
  } else {
    this
  }
  return when (cause) {
    is IOException -> RequestResult.RetryableNetworkError(cause)
    is CancellationException -> RequestResult.RetryableNetworkError(IOException("Request cancelled", cause))
    else -> RequestResult.ApplicationError(cause)
  }
}
