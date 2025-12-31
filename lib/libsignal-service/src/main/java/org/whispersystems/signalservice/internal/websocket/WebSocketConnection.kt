package org.whispersystems.signalservice.internal.websocket

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds

/**
 * Common interface for the web socket connection API
 *
 * At the time of this writing there are two implementations available:
 *  - OkHttpWebSocketConnection - the original Android client implementation in Java using OkHttp library
 *  - LibSignalChatConnection - the wrapper around libsignal's [org.signal.libsignal.net.ChatService]
 */
interface WebSocketConnection {
  companion object {
    val DEFAULT_SEND_TIMEOUT = 10.seconds
  }

  val name: String

  fun connect(): Observable<WebSocketConnectionState>

  fun isDead(): Boolean

  fun disconnect()

  @Throws(IOException::class)
  fun sendRequest(request: WebSocketRequestMessage): Single<WebsocketResponse> {
    return sendRequest(request, DEFAULT_SEND_TIMEOUT.inWholeSeconds)
  }

  @Throws(IOException::class)
  fun sendRequest(request: WebSocketRequestMessage, timeoutSeconds: Long): Single<WebsocketResponse>

  @Throws(IOException::class)
  fun sendKeepAlive()

  fun readRequestIfAvailable(): Optional<WebSocketRequestMessage>

  @Throws(TimeoutException::class, IOException::class)
  fun readRequest(timeoutMillis: Long): WebSocketRequestMessage

  @Throws(IOException::class)
  fun sendResponse(response: WebSocketResponseMessage)

  /**
   * Executes the given callback with the underlying chat connection when it becomes available.
   * This is specifically for LibSignal-based connections to access the native connection.
   *
   * @param callback Function to execute with the chat connection
   * @return The result of the callback
   * @throws UnsupportedOperationException if this connection doesn't support chat connection access
   */
  suspend fun <T> runWithChatConnection(callback: (org.signal.libsignal.net.ChatConnection) -> T): T {
    // Default implementation for non-LibSignal connections
    throw UnsupportedOperationException("This connection does not support chat connection access")
  }
}
