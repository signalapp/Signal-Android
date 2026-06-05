/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.network.NetworkResult
import org.signal.network.util.JsonUtil
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebsocketResponse
import org.signal.network.websocket.get
import org.whispersystems.signalservice.api.fromWebSocketRequest
import org.whispersystems.signalservice.api.remoteconfig.RemoteConfigResult
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.util.Locale

/**
 * Remote configuration is a list of namespaced keys that clients may use for consistent configuration or behavior.
 *
 * Configuration values change over time, and the list should be refreshed periodically.
 */
class RemoteConfigApi(val authWebSocket: SignalWebSocket.AuthenticatedWebSocket, val pushServiceSocket: PushServiceSocket) {

  /**
   * Get remote config data from the server.
   *
   * GET /v2/config
   * - 200: Success
   * - 304: No changes since the last fetch
   * - 401: Requires authentication
   */
  fun getRemoteConfig(eTag: String = ""): NetworkResult<RemoteConfigResult> {
    val headers = if (eTag.isNotEmpty()) mapOf("If-None-Match" to eTag) else mapOf()
    val request = WebSocketRequestMessage.get("/v2/config", headers = headers)
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
        val transformed = remoteConfigResponse.config.map { it.key to (it.value.lowercase(Locale.getDefault()).toBooleanStrictOrNull() ?: it.value) }.toMap()

        NetworkResult.Success(
          RemoteConfigResult(
            config = transformed,
            serverEpochTimeMilliseconds = response.getHeader(SignalWebSocket.SERVER_DELIVERED_TIMESTAMP_HEADER).toLongOrNull() ?: System.currentTimeMillis(),
            eTag = response.headers["etag"]
          )
        )
      }
    }
  }
}

private data class RemoteConfigResponse(
  @JsonProperty
  val config: Map<String, String> = emptyMap(),
  var serverEpochTime: Long = 0
)
