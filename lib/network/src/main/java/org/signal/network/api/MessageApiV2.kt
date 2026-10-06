/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import org.jetbrains.annotations.VisibleForTesting
import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.AuthMessagesService
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.net.SealedSendFailure
import org.signal.libsignal.net.SingleOutboundSealedSenderMessage
import org.signal.libsignal.net.SingleOutboundUnsealedMessage
import org.signal.libsignal.net.SyncSendFailure
import org.signal.libsignal.net.UnauthMessagesService
import org.signal.libsignal.net.UnsealedSendFailure
import org.signal.libsignal.net.UserBasedAuthorization
import org.signal.libsignal.net.UserBasedSendAuthorization
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.websocket.SignalWebSocket

/**
 * Collection of message-related endpoints.
 */
class MessageApiV2(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket
) {

  companion object {
    private val TAG = Log.tag(MessageApiV2::class)
  }

  /**
   * Sends a sealed sender message, retrying once with [SealedSenderAccess.switchToFallback] if the access is rejected.
   * A [RequestUnauthorizedException] result means every access was rejected.
   */
  suspend fun sendSealedSenderMessage(
    serviceId: ServiceId,
    timestamp: Long,
    contents: List<SingleOutboundSealedSenderMessage>,
    sealedSenderAccess: SealedSenderAccess,
    onlineOnly: Boolean,
    urgent: Boolean
  ): RequestResult<Unit, SealedSendFailure> {
    val result = sendSealed(serviceId, timestamp, contents, sealedSenderAccess.toUserBasedAuthorization(), onlineOnly, urgent)

    if (result.isAccessRejected()) {
      val fallbackAccess = sealedSenderAccess.switchToFallback()
      if (fallbackAccess != null) {
        Log.w(TAG, "[$timestamp] Sealed sender access was rejected. Retrying with ${fallbackAccess.javaClass.simpleName}.")
        return sendSealed(serviceId, timestamp, contents, fallbackAccess.toUserBasedAuthorization(), onlineOnly, urgent)
      }
    }

    return result
  }

  suspend fun sendStoryMessage(
    serviceId: ServiceId,
    timestamp: Long,
    contents: List<SingleOutboundSealedSenderMessage>,
    onlineOnly: Boolean,
    urgent: Boolean
  ): RequestResult<Unit, SealedSendFailure> {
    return sendSealed(serviceId, timestamp, contents, UserBasedSendAuthorization.Story, onlineOnly, urgent)
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

  @VisibleForTesting
  internal suspend fun sendSealed(
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

  private fun RequestResult<Unit, SealedSendFailure>.isAccessRejected(): Boolean {
    return this is RequestResult.NonSuccess && error is RequestUnauthorizedException
  }

  private fun SealedSenderAccess.toUserBasedAuthorization(): UserBasedAuthorization {
    return when (this) {
      is SealedSenderAccess.IndividualGroupSendTokenFirst -> UserBasedAuthorization.GroupSend(groupSendToken)
      is SealedSenderAccess.IndividualUnidentifiedAccessFirst -> {
        if (unidentifiedAccess.unidentifiedAccessKey.all { it == 0.toByte() }) {
          UserBasedAuthorization.UnrestrictedUnauthenticatedAccess
        } else {
          UserBasedAuthorization.AccessKey(unidentifiedAccess.unidentifiedAccessKey)
        }
      }
    }
  }
}
