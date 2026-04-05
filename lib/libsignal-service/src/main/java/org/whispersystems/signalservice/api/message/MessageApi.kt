/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.message

import kotlinx.coroutines.runBlocking
import org.signal.core.models.ServiceId
import org.signal.libsignal.net.MultiRecipientMessageResponse
import org.signal.libsignal.net.MultiRecipientSendAuthorization
import org.signal.libsignal.net.MultiRecipientSendFailure
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.UnauthMessagesService
import org.signal.libsignal.net.getOrError
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.post
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList
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
   * Sends a common message to multiple recipients using the libsignal-net [UnauthMessagesService].
   */
  fun sendGroupMessage(body: ByteArray, auth: MultiRecipientSendAuthorization, timestamp: Long, online: Boolean, urgent: Boolean): RequestResult<MultiRecipientMessageResponse, MultiRecipientSendFailure> {
    return runBlocking {
      unauthWebSocket.runCatchingWithUnauthChatConnection { chatConnection ->
        UnauthMessagesService(chatConnection).sendMultiRecipientMessage(body, timestamp, auth, online, urgent)
      }.getOrError()
    }
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

fun MultiRecipientMessageResponse.unsentTargets(): Set<ServiceId> {
  return unregisteredIds.mapTo(HashSet(unregisteredIds.size)) { ServiceId.fromLibSignal(it) }
}
