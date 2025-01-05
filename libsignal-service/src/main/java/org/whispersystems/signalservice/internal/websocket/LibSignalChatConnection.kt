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
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.AuthenticatedChatService
import org.signal.libsignal.net.ChatListener
import org.signal.libsignal.net.ChatService
import org.signal.libsignal.net.ChatServiceException
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.UnauthenticatedChatService
import org.whispersystems.signalservice.api.util.CredentialsProvider
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.util.whenComplete
import java.io.IOException
import java.time.Instant
import java.util.Optional
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

  private val CHAT_SERVICE_LOCK = ReentrantLock()
  private var chatService: ChatService? = null

  companion object {
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
            // TODO[libsignal-net]: Report AUTHENTICATION_FAILED for 401 and 403 errors
            Log.w(TAG, "$name Connect failed", throwable)
            state.onNext(WebSocketConnectionState.FAILED)
          }
        )
      }
      return state
    }
  }

  override fun isDead(): Boolean = false

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
        return Single.error(IOException("[$name] is closed!"))
      }
      val single = SingleSubject.create<WebsocketResponse>()
      val internalRequest = request.toLibSignalRequest()
      chatService!!.send(internalRequest)
        .whenComplete(
          onSuccess = { response ->
            when (response!!.status) {
              in 400..599 -> {
                healthMonitor.onMessageError(response.status, false)
              }
            }
            // Here success means "we received the response" even if it is reporting an error.
            // This is consistent with the behavior of the OkHttpWebSocketConnection.
            single.onSuccess(response.toWebsocketResponse(isUnidentified = (chatService is UnauthenticatedChatService)))
          },
          onFailure = { throwable ->
            Log.w(TAG, "$name sendRequest failed", throwable)
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
            Log.d(TAG, "$name Keep alive - success")
            when (debugResponse!!.response.status) {
              in 200..299 -> {
                healthMonitor.onKeepAliveResponse(
                  Instant.now().toEpochMilli(), // ignored. can be any value
                  false
                )
              }

              in 400..599 -> {
                healthMonitor.onMessageError(debugResponse.response.status, (chatService is AuthenticatedChatService))
              }

              else -> {
                Log.w(TAG, "$name Unsupported keep alive response status: ${debugResponse.response.status}")
              }
            }
          },
          onFailure = { throwable ->
            Log.w(TAG, "$name Keep alive - failed", throwable)
            state.onNext(WebSocketConnectionState.DISCONNECTED)
          }
        )
    }
  }

  override fun readRequestIfAvailable(): Optional<WebSocketRequestMessage> {
    throw NotImplementedError()
  }

  override fun readRequest(timeoutMillis: Long): WebSocketRequestMessage {
    throw NotImplementedError()
  }

  override fun sendResponse(response: WebSocketResponseMessage?) {
    throw NotImplementedError()
  }

  private val listener = object : ChatListener {
    override fun onIncomingMessage(chat: ChatService?, envelope: ByteArray?, serverDeliveryTimestamp: Long, sendAck: ChatListener.ServerMessageAck?) {
      throw NotImplementedError()
    }

    override fun onConnectionInterrupted(chat: ChatService?, disconnectReason: ChatServiceException?) {
      CHAT_SERVICE_LOCK.withLock {
        Log.i(TAG, "connection interrupted", disconnectReason)
        state.onNext(WebSocketConnectionState.DISCONNECTED)
        chatService = null
      }
    }
  }
}
