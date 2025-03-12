/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.message

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.post
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage

/**
 * Collection of endpoints for operating on messages.
 */
class MessageApi(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket
) {

  /**
   * Report a message sender and message id as spam.
   *
   * POST /v1/messages/report/[serviceId]/[serverGuid]
   * - 200: Success
   */
  fun reportSpam(serviceId: ServiceId, serverGuid: String, reportingToken: String?): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.post("/v1/messages/report/$serviceId/$serverGuid", SpamTokenMessage(reportingToken))
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }
}
