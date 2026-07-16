/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing.endpoints

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.WebSocketResponseMessage
import org.signal.network.websocket.WebsocketResponse
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection
import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

/**
 * A [WebSocketConnection] that answers outbound requests from the shared [EndpointResponder] instead
 * of a real socket, so the production [org.whispersystems.signalservice.api.donations.DonationsApi]
 * (and any other websocket-backed API) runs for real against canned responses — real request
 * building and JSON parsing included.
 *
 * There is no server-push side in the responder model: [readRequest] blocks (capped at
 * [MAX_READ_BLOCK_MS] so the read thread stays responsive to disconnect during a test rather than
 * parking for the caller's full keep-alive timeout) and then reports a timeout (the normal "no
 * incoming messages" path), and [readRequestIfAvailable] returns empty. Model of the send side
 * mirrors [org.whispersystems.signalservice.internal.websocket.BenchmarkWebSocketConnection].
 */
class ResponderWebSocketConnection(
  private val responder: EndpointResponder
) : WebSocketConnection {

  override val name: String = "responder-${System.identityHashCode(this)}"

  private val state = BehaviorSubject.create<WebSocketConnectionState>()

  override fun connect(): Observable<WebSocketConnectionState> {
    state.onNext(WebSocketConnectionState.CONNECTED)
    return state
  }

  override fun isDead(): Boolean = false

  override fun disconnect() {
    state.onNext(WebSocketConnectionState.DISCONNECTED)
  }

  override fun sendRequest(request: WebSocketRequestMessage, timeoutSeconds: Long): Single<WebsocketResponse> {
    val response = responder.respond(request.toEndpointRequest())
    return if (response.simulateIoFailure) {
      Single.error(IOException("Simulated websocket failure for ${request.path}"))
    } else {
      Single.just(response.toWebsocketResponse())
    }
  }

  /**
   * The coroutine counterpart to [sendRequest]. The suspend API backs, among others,
   * [org.whispersystems.signalservice.api.profiles.ProfileApi]; background profile fetches use it, so
   * without this override the interface default throws and takes the whole app process down — invisible
   * on a warm device whose profile cache is populated, but fatal on a clean Firebase device where the
   * orchestrator wipes app data before every test.
   */
  override suspend fun sendRequestSuspend(request: WebSocketRequestMessage, timeout: Duration): WebsocketResponse {
    val response = responder.respond(request.toEndpointRequest())
    if (response.simulateIoFailure) {
      throw IOException("Simulated websocket failure for ${request.path}")
    }
    return response.toWebsocketResponse()
  }

  private fun WebSocketRequestMessage.toEndpointRequest(): EndpointRequest {
    return EndpointRequest(
      method = verb ?: "GET",
      path = path ?: "",
      host = null,
      bodyBytes = body?.toByteArray() ?: ByteArray(0),
      headers = parseHeaders(headers),
      transport = EndpointTransport.WEBSOCKET
    )
  }

  private fun EndpointResponse.toWebsocketResponse(): WebsocketResponse {
    return WebsocketResponse(status, body, headers, false)
  }

  override fun sendKeepAlive() = Unit

  override fun readRequestIfAvailable(): Optional<WebSocketRequestMessage> = Optional.empty()

  override fun readRequest(timeoutMillis: Long): WebSocketRequestMessage {
    Thread.sleep(timeoutMillis.coerceAtMost(MAX_READ_BLOCK_MS))
    throw TimeoutException("No incoming requests")
  }

  override fun sendResponse(response: WebSocketResponseMessage) = Unit

  private fun parseHeaders(headers: List<String>): Map<String, String> {
    return headers.mapNotNull { header ->
      val separator = header.indexOf(':')
      if (separator < 0) null else header.substring(0, separator).trim() to header.substring(separator + 1).trim()
    }.toMap()
  }

  companion object {
    private const val MAX_READ_BLOCK_MS = 1_000L
  }
}
