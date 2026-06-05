/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import org.signal.network.NetworkResult
import org.signal.network.websocket.WebSocketRequestMessage
import org.signal.network.websocket.get
import org.whispersystems.signalservice.api.fromWebSocketRequest
import org.whispersystems.signalservice.api.payments.CurrencyConversions
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.push.AuthCredentials

/**
 * Provide payments specific network apis.
 */
class PaymentsApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  /**
   * GET /v1/payments/auth
   * - 200: Success
   */
  fun getAuthorization(): NetworkResult<AuthCredentials> {
    val request = WebSocketRequestMessage.get("/v1/payments/auth")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, AuthCredentials::class)
  }

  /**
   * GET /v1/payments/conversions
   * - 200: Success
   */
  fun getCurrencyConversions(): NetworkResult<CurrencyConversions> {
    val request = WebSocketRequestMessage.get("/v1/payments/conversions")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, CurrencyConversions::class)
  }
}
