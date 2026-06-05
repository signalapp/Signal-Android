/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import org.signal.network.NetworkResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * A convenience method to convert a websocket request into a network result.
 * Common HTTP errors will be translated to [NetworkResult.StatusCodeError]s.
 */
fun NetworkResult.Companion.fromWebSocketRequest(
  signalWebSocket: SignalWebSocket,
  request: WebSocketRequestMessage,
  timeout: Duration = WebSocketConnection.DEFAULT_SEND_TIMEOUT
): NetworkResult<Unit> = fromWebSocketRequest(
  signalWebSocket = signalWebSocket,
  request = request,
  timeout = timeout,
  clazz = Unit::class
)

/**
 * A convenience method to convert a websocket request into a network result with simple conversion of the response body to the desired class.
 * Common HTTP errors will be translated to [NetworkResult.StatusCodeError]s.
 */
fun <T : Any> NetworkResult.Companion.fromWebSocketRequest(
  signalWebSocket: SignalWebSocket,
  request: WebSocketRequestMessage,
  clazz: KClass<T>,
  timeout: Duration = WebSocketConnection.DEFAULT_SEND_TIMEOUT
): NetworkResult<T> {
  return fromWebSocketRequest(
    signalWebSocket = signalWebSocket,
    request = request,
    timeout = timeout,
    webSocketResponseConverter = NetworkResult.DefaultWebSocketConverter(clazz)
  )
}

/**
 * A convenience method to convert a websocket request into a network result with the ability to fully customize the conversion of the response.
 * Common HTTP errors will be translated to [NetworkResult.StatusCodeError]s.
 */
fun <T : Any> NetworkResult.Companion.fromWebSocketRequest(
  signalWebSocket: SignalWebSocket,
  request: WebSocketRequestMessage,
  timeout: Duration = WebSocketConnection.DEFAULT_SEND_TIMEOUT,
  webSocketResponseConverter: NetworkResult.WebSocketResponseConverter<T>
): NetworkResult<T> {
  return NetworkResult.fromWebSocket(webSocketResponseConverter) { signalWebSocket.request(request, timeout) }
}
