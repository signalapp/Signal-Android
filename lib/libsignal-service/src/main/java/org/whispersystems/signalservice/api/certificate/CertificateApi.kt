/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.certificate

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.push.SenderCertificate
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage

/**
 * Endpoints to get [SenderCertificate]s.
 */
class CertificateApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket) {

  /**
   * GET /v1/certificate/delivery
   * - 200: Success
   */
  fun getSenderCertificate(): NetworkResult<ByteArray> {
    val request = WebSocketRequestMessage.get("/v1/certificate/delivery")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, SenderCertificate::class)
      .map { it.certificate }
  }

  /**
   * GET /v1/certificate/delivery?includeE164=false
   * - 200: Success
   */
  fun getSenderCertificateForPhoneNumberPrivacy(): NetworkResult<ByteArray> {
    val request = WebSocketRequestMessage.get("/v1/certificate/delivery?includeE164=false")
    return NetworkResult.fromWebSocketRequest(authWebSocket, request, SenderCertificate::class)
      .map { it.certificate }
  }
}
