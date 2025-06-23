/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.payments

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage

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
