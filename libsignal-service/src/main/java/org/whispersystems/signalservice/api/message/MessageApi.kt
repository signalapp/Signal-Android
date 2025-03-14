/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.message

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.post
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList
import org.whispersystems.signalservice.internal.push.SendGroupMessageResponse
import org.whispersystems.signalservice.internal.push.SendMessageResponse
import org.whispersystems.signalservice.internal.put
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse

/**
 * Collection of endpoints for operating on messages.
 */
class MessageApi(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket
) {

  companion object {
    /**
     * Adjust the default parsing of [SendMessageResponse] to set the non-server returned [SendMessageResponse.sentUnidentfied]
     * flag on the model.
     */
    private val sendMessageResponseConverter = object : NetworkResult.WebSocketResponseConverter<SendMessageResponse> {
      override fun convert(response: WebsocketResponse): NetworkResult<SendMessageResponse> {
        return if (response.status == 200) {
          response.toSuccess(SendMessageResponse::class)
            .map { it.apply { setSentUnidentfied(response.isUnidentified) } }
        } else {
          response.toStatusCodeError()
        }
      }
    }
  }

  /**
   * Sends a message to a single recipient, using the appropriate initial authentication style based on presence of [sealedSenderAccess], but
   * will automatically fallback to auth if that fails specifically because of an invalid [sealedSenderAccess].
   *
   * PUT /v1/messages/[messageList]`.destination`?story=[story]
   * - 200: Success
   * - 401: Message is not a story and authorization or [sealedSenderAccess] is missing or incorrect
   * - 404: Message is not a story and recipient is not a registered Signal user
   * - 409: Mismatched devices
   * - 410: Stale devices
   * - 428: Sender proof required
   */
  fun sendMessage(messageList: OutgoingPushMessageList, sealedSenderAccess: SealedSenderAccess?, story: Boolean): NetworkResult<SendMessageResponse> {
    val request = WebSocketRequestMessage.put("/v1/messages/${messageList.destination}?story=${story.toQueryParam()}", messageList)

    return if (sealedSenderAccess == null) {
      NetworkResult.fromWebSocket(sendMessageResponseConverter) { authWebSocket.request(request) }
    } else {
      NetworkResult.fromWebSocket(sendMessageResponseConverter) { unauthWebSocket.request(request, sealedSenderAccess) }
        .fallback(
          predicate = { it is NetworkResult.StatusCodeError && it.code == 401 },
          fallback = { NetworkResult.fromWebSocket(sendMessageResponseConverter) { authWebSocket.request(request) } }
        )
    }
  }

  /**
   * Sends a common message to multiple recipients and requires some form of [sealedSenderAccess] unless it's a story.
   *
   * PUT /v1/messages/multi_recipient?ts=[timestamp]&online=[online]&urgent=[urgent]&story=[story]
   * - 200: Success
   * - 400: Message specified delivery to the same recipient device multiple times
   * - 401: Message is not a story and [sealedSenderAccess] is missing or incorrect
   * - 404: Message is not a story and some of the recipients are not registered Signal users
   * - 409: Incorrect set of devices supplied for some recipients
   * - 410: Stale devices supplied for some recipients
   */
  fun sendGroupMessage(body: ByteArray, sealedSenderAccess: SealedSenderAccess, timestamp: Long, online: Boolean, urgent: Boolean, story: Boolean): NetworkResult<SendGroupMessageResponse> {
    val request = WebSocketRequestMessage.put(
      path = "/v1/messages/multi_recipient?ts=$timestamp&online=${online.toQueryParam()}&urgent=${urgent.toQueryParam()}&story=${story.toQueryParam()}",
      body = body,
      headers = mapOf("content-type" to "application/vnd.signal-messenger.mrm")
    )

    return NetworkResult.fromWebSocket { unauthWebSocket.request(request, sealedSenderAccess) }
  }

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

  private fun Boolean.toQueryParam(): String = if (this) "true" else "false"
}
