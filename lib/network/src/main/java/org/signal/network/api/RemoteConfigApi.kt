/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.signal.core.util.serialization.SignalJson
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebsocketResponse
import org.signal.network.websocket.get
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import java.io.IOException
import java.util.Locale

/**
 * Remote configuration is a list of namespaced keys that clients may use for consistent configuration or behavior.
 *
 * Configuration values change over time, and the list should be refreshed periodically.
 */
class RemoteConfigApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  /**
   * Get remote config data from the server. If [eTag] is provided and the config hasn't changed since, the result is [RemoteConfigResult.NotModified].
   *
   * GET /v2/config
   * - 200: Success
   * - 304: No changes since the last fetch
   * - 401: Requires authentication
   */
  suspend fun getRemoteConfig(eTag: String? = null): RequestResult<RemoteConfigResult, GetRemoteConfigError> {
    val headers = if (!eTag.isNullOrEmpty()) mapOf("If-None-Match" to eTag) else emptyMap()
    val request = WebSocketRequestMessage.get("/v2/config", headers = headers)

    return try {
      val response = authWebSocket.requestSuspend(request)

      when (response.status) {
        200 -> {
          SignalJson.decode(RemoteConfigResponse.serializer(), response.body).fold(
            ifLeft = { RequestResult.ApplicationError(it.cause) },
            ifRight = { body -> RequestResult.Success(body.toResult(response.serverEpochTimeMilliseconds(), response.getHeader("etag"))) }
          )
        }
        304 -> {
          RequestResult.Success(RemoteConfigResult.NotModified(response.serverEpochTimeMilliseconds()))
        }
        401 -> {
          RequestResult.NonSuccess(GetRemoteConfigError.Unauthorized)
        }
        else -> {
          RequestResult.NonSuccess(GetRemoteConfigError.UnknownError(response.status))
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Throwable) {
      RequestResult.ApplicationError(e)
    }
  }

  private fun WebsocketResponse.serverEpochTimeMilliseconds(): Long {
    return getHeader(SignalWebSocket.SERVER_DELIVERED_TIMESTAMP_HEADER)?.toLongOrNull() ?: System.currentTimeMillis()
  }

  @Serializable
  private class RemoteConfigResponse(
    val config: Map<String, String> = emptyMap()
  ) {
    fun toResult(serverEpochTimeMilliseconds: Long, eTag: String?): RemoteConfigResult.Updated {
      return RemoteConfigResult.Updated(
        config = config.mapValues { (_, value) -> value.lowercase(Locale.getDefault()).toBooleanStrictOrNull() ?: value },
        serverEpochTimeMilliseconds = serverEpochTimeMilliseconds,
        eTag = eTag
      )
    }
  }

  sealed interface RemoteConfigResult {
    val serverEpochTimeMilliseconds: Long

    data class Updated(
      val config: Map<String, Any>,
      override val serverEpochTimeMilliseconds: Long,
      val eTag: String?
    ) : RemoteConfigResult

    data class NotModified(override val serverEpochTimeMilliseconds: Long) : RemoteConfigResult
  }

  sealed interface GetRemoteConfigError : BadRequestError {
    data object Unauthorized : GetRemoteConfigError
    data class UnknownError(val statusCode: Int) : GetRemoteConfigError
  }
}
