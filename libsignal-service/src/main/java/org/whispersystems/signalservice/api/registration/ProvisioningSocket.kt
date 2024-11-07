/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.registration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.registration.proto.RegistrationProvisionEnvelope
import org.whispersystems.signalservice.api.buildOkHttpClient
import org.whispersystems.signalservice.api.chooseUrl
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.crypto.SecondaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.ProvisioningAddress
import org.whispersystems.signalservice.internal.websocket.WebSocketMessage
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebSocketResponseMessage
import java.io.Closeable
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

/**
 * A provisional web socket for communicating with a primary device during registration.
 */
class ProvisioningSocket private constructor(
  identityKeyPair: IdentityKeyPair,
  configuration: SignalServiceConfiguration,
  private val scope: CoroutineScope
) {
  companion object {
    private val TAG = Log.tag(ProvisioningSocket::class)

    fun start(
      identityKeyPair: IdentityKeyPair,
      configuration: SignalServiceConfiguration,
      handler: CoroutineExceptionHandler,
      block: suspend CoroutineScope.(ProvisioningSocket) -> Unit
    ): Closeable {
      val scope = CoroutineScope(Dispatchers.IO) + SupervisorJob() + handler

      scope.launch {
        var socket: ProvisioningSocket? = null
        try {
          socket = ProvisioningSocket(identityKeyPair, configuration, scope)
          socket.connect()
          block(socket)
        } catch (e: CancellationException) {
          val rootCause = e.getRootCause()
          if (rootCause == null) {
            Log.i(TAG, "Scope canceled expectedly, fail silently, ${e.toMinimalString()}")
            throw e
          } else {
            Log.w(TAG, "Unable to maintain web socket, ${rootCause.toMinimalString()}", rootCause)
            throw rootCause
          }
        } finally {
          Log.d(TAG, "Closing web socket")
          socket?.close()
        }
      }

      return Closeable { scope.cancel("scope closed") }
    }

    /**
     * Get non-cancellation exception cause to determine if something legitimately failed.
     */
    private fun CancellationException.getRootCause(): Throwable? {
      var cause: Throwable? = cause
      while (cause != null && cause is CancellationException) {
        cause = cause.cause
      }
      return cause
    }

    /**
     * Generates a minimal throwable informational string since stack traces aren't always logged.
     */
    private fun Throwable.toMinimalString(): String {
      return "${javaClass.simpleName}[$message]"
    }
  }

  private val serviceUrl = configuration.signalServiceUrls.chooseUrl()
  private val okhttp = serviceUrl.buildOkHttpClient(configuration)

  private val cipher = SecondaryProvisioningCipher(identityKeyPair)
  private var webSocket: WebSocket? = null

  private val provisioningUrlDeferral: CompletableDeferred<String> = CompletableDeferred()
  private val provisioningMessageDeferral: CompletableDeferred<SecondaryProvisioningCipher.RegistrationProvisionResult> = CompletableDeferred()

  suspend fun getProvisioningUrl(): String {
    return provisioningUrlDeferral.await()
  }

  suspend fun getRegistrationProvisioningMessage(): SecondaryProvisioningCipher.RegistrationProvisionResult {
    return provisioningMessageDeferral.await()
  }

  private fun connect() {
    val uri = serviceUrl.url.replace("https://", "wss://").replace("http://", "ws://")

    val openRequest = Request.Builder()
      .url("$uri/v1/websocket/provisioning/")

    if (serviceUrl.hostHeader.isPresent) {
      openRequest.addHeader("Host", serviceUrl.hostHeader.get())
      Log.w(TAG, "Using alternate host: ${serviceUrl.hostHeader.get()}")
    }

    webSocket = okhttp.newWebSocket(openRequest.build(), ProvisioningWebSocketListener())
  }

  private fun close() {
    webSocket?.close(1000, "Manual shutdown")
  }

  private inner class ProvisioningWebSocketListener : WebSocketListener() {
    private var keepAliveJob: Job? = null

    @Volatile
    private var lastKeepAliveId: Long = 0

    override fun onOpen(webSocket: WebSocket, response: Response) {
      Log.d(TAG, "[onOpen]")
      keepAliveJob = scope.launch { keepAlive(webSocket) }

      val timeoutJob = scope.launch {
        delay(10.seconds)
        scope.cancel("Did not receive device id within 10 seconds", SocketTimeoutException("No device id received"))
      }

      scope.launch {
        provisioningUrlDeferral.await()
        timeoutJob.cancel()
      }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
      val message: WebSocketMessage = WebSocketMessage.ADAPTER.decode(bytes)

      if (message.response != null && message.response.id == lastKeepAliveId) {
        Log.d(TAG, "[onMessage] Keep alive received")
        return
      }

      if (message.request == null) {
        Log.w(TAG, "[onMessage] Received null request")
        return
      }

      val success = webSocket.send(message.request.toResponse().encode().toByteString())

      if (!success) {
        Log.w(TAG, "[onMessage] Failed to send response")
        webSocket.close(1000, "OK")
        return
      }

      Log.d(TAG, "[onMessage] Processing request")

      if (message.request.verb == "PUT" && message.request.body != null) {
        when (message.request.path) {
          "/v1/address" -> {
            val address = ProvisioningAddress.ADAPTER.decode(message.request.body).address
            if (address != null) {
              provisioningUrlDeferral.complete(generateProvisioningUrl(address))
            } else {
              throw IOException("Device address is null")
            }
          }

          "/v1/message" -> {
            val result = cipher.decrypt(RegistrationProvisionEnvelope.ADAPTER.decode(message.request.body))
            provisioningMessageDeferral.complete(result)
          }

          else -> Log.w(TAG, "Unknown path requested")
        }
      } else {
        Log.w(TAG, "Invalid data")
      }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      scope.launch {
        Log.i(TAG, "[onClosing] code: $code reason: $reason")

        if (code != 1000) {
          Log.w(TAG, "Remote side is closing with non-normal code $code")
          webSocket.close(1000, "Remote closed with code $code")
        }

        scope.cancel()
      }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      scope.launch {
        Log.w(TAG, "[onFailure] Failed", t)
        webSocket.close(1000, "Failed ${t.message}")

        scope.cancel(CancellationException("WebSocket Failure", t))
      }
    }

    private fun generateProvisioningUrl(deviceAddress: String): String {
      val encodedDeviceId = URLEncoder.encode(deviceAddress, "UTF-8")
      val encodedPubKey: String = URLEncoder.encode(Base64.encodeWithoutPadding(cipher.secondaryDevicePublicKey.serialize()), "UTF-8")
      return "sgnl://rereg?uuid=$encodedDeviceId&pub_key=$encodedPubKey"
    }

    private suspend fun keepAlive(webSocket: WebSocket) {
      Log.i(TAG, "[keepAlive] Starting")
      while (true) {
        delay(30.seconds)
        Log.i(TAG, "[keepAlive] Sending...")

        val id = System.currentTimeMillis()
        val message = WebSocketMessage(
          type = WebSocketMessage.Type.REQUEST,
          request = WebSocketRequestMessage(
            id = id,
            path = "/v1/keepalive",
            verb = "GET"
          )
        )

        if (!webSocket.send(message.encodeByteString())) {
          Log.w(TAG, "[keepAlive] Send failed")
        } else {
          lastKeepAliveId = id
        }
      }
    }

    private fun WebSocketRequestMessage.toResponse(): WebSocketMessage {
      return WebSocketMessage(
        type = WebSocketMessage.Type.RESPONSE,
        response = WebSocketResponseMessage(
          id = id,
          status = 200,
          message = "OK"
        )
      )
    }
  }
}
