/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.remoteconfig

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse

/**
 * Remote configuration is a list of namespaced keys that clients may use for consistent configuration or behavior.
 *
 * Configuration values change over time, and the list should be refreshed periodically.
 */
class RemoteConfigApi(val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  /**
   * Get remote config data from the server.
   *
   * GET /v1/config
   * - 200: Success
   */
  fun getRemoteConfig(): NetworkResult<RemoteConfigResult> {
    val request = WebSocketRequestMessage.get("/v1/config")
    return NetworkResult.fromWebSocketRequest(signalWebSocket = authWebSocket, request = request, webSocketResponseConverter = RemoteConfigResultWebSocketResponseConverter())
  }

  /**
   * Custom converter for [RemoteConfigResult] as it needs the value of the timestamp header to construct the
   * complete result, not just the JSON body.
   */
  private class RemoteConfigResultWebSocketResponseConverter : NetworkResult.WebSocketResponseConverter<RemoteConfigResult> {
    override fun convert(response: WebsocketResponse): NetworkResult<RemoteConfigResult> {
      return if (response.status < 200 || response.status > 299) {
        response.toStatusCodeError()
      } else {
        val remoteConfigResponse = JsonUtil.fromJson(response.body, RemoteConfigResponse::class.java)
        val transformed = remoteConfigResponse.config.associate { it.name to (it.value ?: it.isEnabled) }

        NetworkResult.Success(
          RemoteConfigResult(
            config = transformed,
            serverEpochTimeMilliseconds = response.getHeader(SignalWebSocket.SERVER_DELIVERED_TIMESTAMP_HEADER).toLongOrNull() ?: System.currentTimeMillis()
          )
        )
      }
    }
  }
}
