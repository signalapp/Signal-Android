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
import org.signal.libsignal.net.ChatService
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.util.whenComplete
import java.time.Instant
import java.util.Optional
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
  private val chatService: ChatService,
  private val healthMonitor: HealthMonitor,
  val isAuthenticated: Boolean
) : WebSocketConnection {

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
    Log.i(TAG, "$name Connecting...")
    state.onNext(WebSocketConnectionState.CONNECTING)
    val connect = if (isAuthenticated) {
      chatService::connectAuthenticated
    } else {
      chatService::connectUnauthenticated
    }
    connect()
      .whenComplete(
        onSuccess = { debugInfo ->
          Log.i(TAG, "$name Connected")
          Log.d(TAG, "$name $debugInfo")
          state.onNext(WebSocketConnectionState.CONNECTED)
        },
        onFailure = { throwable ->
          // TODO: [libsignal-net] Report WebSocketConnectionState.AUTHENTICATION_FAILED for 401 and 403 errors
          Log.d(TAG, "$name Connect failed", throwable)
          state.onNext(WebSocketConnectionState.FAILED)
        }
      )
    return state
  }

  override fun isDead(): Boolean = false

  override fun disconnect() {
    Log.i(TAG, "$name Disconnecting...")
    state.onNext(WebSocketConnectionState.DISCONNECTING)
    chatService.disconnect()
      .whenComplete(
        onSuccess = {
          Log.i(TAG, "$name Disconnected")
          state.onNext(WebSocketConnectionState.DISCONNECTED)
        },
        onFailure = { throwable ->
          Log.d(TAG, "$name Disconnect failed", throwable)
          state.onNext(WebSocketConnectionState.DISCONNECTED)
        }
      )
  }

  override fun sendRequest(request: WebSocketRequestMessage): Single<WebsocketResponse> {
    val single = SingleSubject.create<WebsocketResponse>()
    val internalRequest = request.toLibSignalRequest()
    val send = if (isAuthenticated) {
      throw NotImplementedError("Authenticated socket is not yet supported")
    } else {
      chatService::unauthenticatedSend
    }
    send(internalRequest)
      .whenComplete(
        onSuccess = { response ->
          when (response!!.status) {
            in 400..599 -> {
              healthMonitor.onMessageError(response.status, false)
            }
          }
          // Here success means "we received the response" even if it is reporting an error.
          // This is consistent with the behavior of the OkHttpWebSocketConnection.
          single.onSuccess(response.toWebsocketResponse(isUnidentified = !isAuthenticated))
        },
        onFailure = { throwable ->
          Log.i(TAG, "$name sendRequest failed", throwable)
          single.onError(throwable)
        }
      )
    return single.subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
  }

  override fun sendKeepAlive() {
    Log.i(TAG, "$name Sending keep alive...")
    val send = if (isAuthenticated) {
      throw NotImplementedError("Authenticated socket is not yet supported")
    } else {
      chatService::unauthenticatedSendAndDebug
    }
    send(KEEP_ALIVE_REQUEST)
      .whenComplete(
        onSuccess = { debugResponse ->
          Log.i(TAG, "$name Keep alive - success")
          Log.d(TAG, "$name $debugResponse")
          when (debugResponse!!.response.status) {
            in 200..299 -> {
              healthMonitor.onKeepAliveResponse(
                Instant.now().toEpochMilli(), // ignored. can be any value
                false
              )
            }

            in 400..599 -> {
              healthMonitor.onMessageError(debugResponse.response.status, isAuthenticated)
            }

            else -> {
              Log.w(TAG, "$name Unsupported keep alive response status: ${debugResponse.response.status}")
            }
          }
        },
        onFailure = { throwable ->
          Log.i(TAG, "$name Keep alive - failed")
          Log.d(TAG, "$name $throwable")
          state.onNext(WebSocketConnectionState.DISCONNECTED)
        }
      )
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
}
