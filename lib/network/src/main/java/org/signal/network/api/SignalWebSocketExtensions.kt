/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import kotlinx.coroutines.CancellationException
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.ServerSideErrorException
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebsocketResponse
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import java.io.IOException

/**
 * Issues a hand-rolled REST-over-websocket request, classifying the outcome the same way libsignal classifies its own. Never throws (aside from propagating
 * cancellation) -- every failure is reported as a [RequestResult] error variant.
 *
 * A null from [mapError] on any other code means the status isn't one this endpoint documents. It's mapped to [RequestResult.RetryableNetworkError].
 *
 * A 5xx or an unmapped status carries a [ServerSideErrorException], matching how libsignal reports server-side failures on the endpoints it owns.
 *
 * [isSuccess] decides which statuses are handed to [parseSuccess]. It defaults to 2xx; endpoints with a non-2xx success (e.g. a 304) can widen it.
 *
 * [parseSuccess] may throw an [IOException] for a 2xx body that can't be parsed; it is caught here and reported as a [RequestResult.RetryableNetworkError],
 * matching how a garbled response from the transport itself is classified.
 */
internal suspend fun <T, E : BadRequestError> SignalWebSocket.requestResult(
  request: WebSocketRequestMessage,
  isSuccess: (Int) -> Boolean = { it in 200..299 },
  parseSuccess: (WebsocketResponse) -> T,
  mapError: (WebsocketResponse) -> E?
): RequestResult<T, E> {
  return try {
    val response = requestSuspend(request)

    when {
      isSuccess(response.status) -> RequestResult.Success(parseSuccess(response))
      response.status in 500..599 -> RequestResult.RetryableNetworkError(ServerSideErrorException("Server error: ${response.status}"))
      else -> when (val error = mapError(response)) {
        null -> RequestResult.RetryableNetworkError(ServerSideErrorException("Unexpected response code: ${response.status}"))
        else -> RequestResult.NonSuccess(error)
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
