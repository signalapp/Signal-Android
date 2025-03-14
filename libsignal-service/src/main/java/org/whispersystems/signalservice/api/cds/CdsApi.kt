/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.cds

import org.signal.libsignal.net.Network
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.NetworkResult.StatusCodeError
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.exceptions.CdsiInvalidTokenException
import org.whispersystems.signalservice.api.push.exceptions.CdsiResourceExhaustedException
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.push.CdsiAuthResponse
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Consumer

/**
 * Contact Discovery Service API endpoint.
 */
class CdsApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  /**
   * Get CDS authentication and then request registered users for the provided e164s.
   *
   * GET /v2/directory/auth
   * - 200: Success
   * - 401: Not authenticated
   *
   * And then CDS websocket communications, can return the following within [StatusCodeError]
   * - [CdsiResourceExhaustedException]: Rate limited
   * - [CdsiInvalidTokenException]: Token no longer valid
   */
  fun getRegisteredUsers(
    previousE164s: Set<String>,
    newE164s: Set<String>,
    serviceIds: Map<ServiceId, ProfileKey>,
    token: Optional<ByteArray>,
    timeoutMs: Long?,
    libsignalNetwork: Network,
    useLibsignalRouteBasedCDSIConnectionLogic: Boolean,
    tokenSaver: Consumer<ByteArray>
  ): NetworkResult<CdsiV2Service.Response> {
    val authRequest = WebSocketRequestMessage.get("/v2/directory/auth")

    return NetworkResult.fromWebSocketRequest(authWebSocket, authRequest, CdsiAuthResponse::class)
      .then { auth ->
        val service = CdsiV2Service(libsignalNetwork, useLibsignalRouteBasedCDSIConnectionLogic)
        val request = CdsiV2Service.Request(previousE164s, newE164s, serviceIds, token)

        val single = service.getRegisteredUsers(auth.username, auth.password, request, tokenSaver)

        return@then try {
          if (timeoutMs == null) {
            single
              .blockingGet()
          } else {
            single
              .timeout(timeoutMs, TimeUnit.MILLISECONDS)
              .blockingGet()
          }
        } catch (e: RuntimeException) {
          when (val cause = e.cause) {
            is InterruptedException -> NetworkResult.NetworkError(IOException("Interrupted", cause))
            is TimeoutException -> NetworkResult.NetworkError(IOException("Timed out"))
            else -> throw e
          }
        }
      }
  }
}
