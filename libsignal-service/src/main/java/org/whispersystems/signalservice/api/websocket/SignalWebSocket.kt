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
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.messages.EnvelopeResponse
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebSocketResponseMessage
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse
import java.io.IOException
import java.util.concurrent.TimeoutException

/**
 * Base wrapper around a [WebSocketConnection] to provide a more developer friend interface to websocket
 * interactions.
 */
sealed class SignalWebSocket(
  private val createConnection: () -> WebSocketConnection
) {

  companion object {
    private val TAG = Log.tag(SignalWebSocket::class)

    const val SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp"
  }

  private var connection: WebSocketConnection? = null
  private val _state: BehaviorSubject<WebSocketConnectionState> = BehaviorSubject.createDefault(WebSocketConnectionState.DISCONNECTED)
  protected var disposable: CompositeDisposable = CompositeDisposable()

  private var canConnect = false

  var shouldSendKeepAlives: Boolean = true
    set(value) {
      field = value
      keepAliveChangedListener?.invoke()
    }
  var keepAliveChangedListener: (() -> Unit)? = null

  val state: Observable<WebSocketConnectionState> = _state

  /**
   * Indicate that WebSocketConnection can now be made and attempt to connect.
   */
  @Synchronized
  fun connect() {
    canConnect = true
    getWebSocket()
  }

  /**
   * Indicate that WebSocketConnection can no longer be made and disconnect.
   */
  @Synchronized
  fun disconnect() {
    canConnect = false
    disconnectInternal()
  }

  private fun disconnectInternal() {
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
    if (canConnect) {
      getWebSocket().sendKeepAlive()
    }
  }

  fun request(request: WebSocketRequestMessage): Single<WebsocketResponse> {
    return try {
      getWebSocket().sendRequest(request)
    } catch (e: IOException) {
      Single.error(e)
    }
  }

  @Throws(IOException::class)
  fun sendAck(response: EnvelopeResponse) {
    getWebSocket().sendResponse(response.websocketRequest.getWebSocketResponse())
  }

  @Synchronized
  @Throws(WebSocketUnavailableException::class)
  protected fun getWebSocket(): WebSocketConnection {
    if (!canConnect) {
      throw WebSocketUnavailableException()
    }

    if (connection == null || connection?.isDead() == true) {
      disposable.dispose()

      disposable = CompositeDisposable()
      val newConnection = createConnection()

      newConnection
        .connect()
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .subscribeBy { _state.onNext(it) }
        .addTo(disposable)

      this.connection = newConnection
    }

    return connection!!
  }

  @Synchronized
  fun forceNewWebSocket() {
    Log.i(TAG, "Forcing new WebSockets  connection: ${connection?.name ?: "[null]"} canConnect: $canConnect")
    disconnectInternal()
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
  class UnauthenticatedWebSocket(createConnection: () -> WebSocketConnection) : SignalWebSocket(createConnection) {
    fun request(requestMessage: WebSocketRequestMessage, sealedSenderAccess: SealedSenderAccess): Single<WebsocketResponse> {
      val headers: MutableList<String> = requestMessage.headers.toMutableList()
      headers.add(sealedSenderAccess.header)

      val message = requestMessage
        .newBuilder()
        .headers(headers)
        .build()

      try {
        return getWebSocket()
          .sendRequest(message)
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
  }

  /**
   * WebSocket type for communicating with the server with authentication. Also known as "identified".
   */
  class AuthenticatedWebSocket(createConnection: () -> WebSocketConnection) : SignalWebSocket(createConnection) {

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
}
