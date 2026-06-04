/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import org.signal.core.models.ServiceId
import org.signal.libsignal.net.AuthMessagesService
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.SealedSendFailure
import org.signal.libsignal.net.SingleOutboundSealedSenderMessage
import org.signal.libsignal.net.SingleOutboundUnsealedMessage
import org.signal.libsignal.net.SyncSendFailure
import org.signal.libsignal.net.UnauthMessagesService
import org.signal.libsignal.net.UnsealedSendFailure
import org.signal.libsignal.net.UserBasedSendAuthorization
import org.whispersystems.signalservice.api.websocket.SignalWebSocket

/**
 * Collection of message-related endpoints.
 */
class MessageApiV2(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket
) {

  suspend fun sendSealedSenderMessage(
    serviceId: ServiceId,
    timestamp: Long,
    contents: List<SingleOutboundSealedSenderMessage>,
    auth: UserBasedSendAuthorization,
    onlineOnly: Boolean,
    urgent: Boolean
  ): RequestResult<Unit, SealedSendFailure> {
    return unauthWebSocket.runCatchingWithChatConnection { connection ->
      UnauthMessagesService(connection).sendMessage(serviceId.libSignalServiceId, timestamp, contents, auth, onlineOnly, urgent)
    }
  }

  suspend fun sendUnsealedSenderMessage(
    serviceId: ServiceId,
    timestamp: Long,
    contents: List<SingleOutboundUnsealedMessage>,
    onlineOnly: Boolean,
    urgent: Boolean
  ): RequestResult<Unit, UnsealedSendFailure> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthMessagesService(connection).sendMessage(serviceId.libSignalServiceId, timestamp, contents, onlineOnly, urgent)
    }
  }

  suspend fun sendSyncMessage(
    timestamp: Long,
    contents: List<SingleOutboundUnsealedMessage>,
    urgent: Boolean
  ): RequestResult<Unit, SyncSendFailure> {
    return authWebSocket.runCatchingWithChatConnection { connection ->
      AuthMessagesService(connection).sendSyncMessage(timestamp, contents, urgent)
    }
  }
}
