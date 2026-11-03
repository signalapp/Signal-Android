/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import org.signal.core.models.ServiceId
import org.signal.libsignal.net.ChallengeOption
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.network.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import java.io.IOException
import kotlin.time.Duration

/**
 * Entry point for sending messages. Sends to a single recipient and to our linked devices go through [IndividualMessageService], group and story
 * sends through [GroupMessageService].
 */
class MessageService(
  private val individualMessageService: IndividualMessageService,
  private val groupMessageService: GroupMessageService,
  private val dependencies: MessageSendDependencies
) {

  suspend fun sendMessage(
    serviceId: ServiceId,
    sealedSenderAccess: SealedSenderAccess?,
    request: SendRequest<EnvelopeContent>,
    onEncrypted: (() -> Unit)? = null
  ): Either<SendError, SendSuccess> {
    return individualMessageService.sendMessage(serviceId, request.content, request.timestamp, sealedSenderAccess, request.isOnline, request.urgent, onEncrypted)
  }

  /** For callers that build one [SendRequest] and may send it to someone else or ourselves. */
  suspend fun sendSyncMessage(
    request: SendRequest<EnvelopeContent>,
    onEncrypted: (() -> Unit)? = null
  ): Either<SendError, SendSuccess> {
    return sendSyncMessage(request.timestamp, request.content, request.urgent, onEncrypted)
  }

  suspend fun sendSyncMessage(
    timestamp: Long,
    envelopeContent: EnvelopeContent,
    urgent: Boolean,
    onEncrypted: (() -> Unit)?
  ): Either<SendError, SendSuccess> {
    return individualMessageService.sendSyncMessage(timestamp, envelopeContent, urgent, onEncrypted)
  }

  /**
   * Sends [request] to a group or story audience. With a [messageId], every result is recorded in one message send log entry.
   *
   * The group id on [request] is not used, [destination]'s is.
   */
  suspend fun sendGroupMessage(
    destination: GroupSendDestination,
    recipients: List<GroupSendRecipient>,
    request: SendRequest<EnvelopeContent.Encrypted>,
    messageId: Long? = null
  ): Either<GroupSendError, List<RecipientSendResult>> {
    val content = request.content.content.get()
    val contentHint = request.content.contentHint
    val log = messageId?.let { SendLogEntry(it, recipients, request.timestamp, contentHint, request.urgent) }
    return groupMessageService.sendMessage(destination, recipients, content, contentHint, request.timestamp, request.isOnline, request.urgent, onResult = { log?.record(it) })
  }

  private inner class SendLogEntry(
    private val messageId: Long,
    recipients: List<GroupSendRecipient>,
    private val timestamp: Long,
    private val contentHint: ContentHint,
    private val urgent: Boolean
  ) {
    private val recipientIds: Map<ServiceId, Long> = recipients.associate { it.serviceId to it.id }
    private var entryId = -1L

    fun record(result: RecipientSendResult) {
      val success = result.result.getOrNull() ?: return
      val recipientId = recipientIds[result.serviceId] ?: return
      synchronized(this) {
        entryId = dependencies.logSendResult(entryId, recipientId, result.serviceId, success, timestamp, contentHint, messageId, urgent)
      }
    }
  }

  data class RecipientSendResult(val serviceId: ServiceId, val result: Either<SendError, SendSuccess>)

  /**
   * Send completed successfully.
   *
   * [devices] is the set of recipient devices the encrypted payload was delivered to. Callers persisting
   * a [org.thoughtcrime.securesms.database.MessageSendLogTables] entry (or a pending PNI signature record)
   * need this to know which sessions the recipient may later reference in a retry receipt.
   */
  data class SendSuccess(
    val envelopeContent: EnvelopeContent,
    val sentSealedSender: Boolean,
    val devices: List<Int>
  )

  sealed class SendError : Exception() {
    /** You discovered a safety number change during sending. */
    data class IdentityMismatch(val serviceId: ServiceId, val exception: UntrustedIdentityException) : SendError()

    /** The recipient is no longer registered. */
    class NotRegistered : SendError()

    /** Invalid credentials. You are likely no longer registered. */
    class Unauthorized : SendError()

    /**
     * The server wants you to complete a push challenge/captcha before continuing.
     * [token] is the challenge token; [options] enumerates the supported challenge mechanisms
     * (e.g. "captcha", "pushChallenge"). [retryAfter] is the Retry-After hint, if provided.
     */
    data class ChallengeRequired(val token: String, val options: Set<ChallengeOption>, val retryAfter: Duration?) : SendError() {
      override fun toString(): String = "ChallengeRequired(options=$options, retryAfter=$retryAfter)"
    }

    /**
     * The encoded content exceeded the configured size cap. Permanent failure for this message —
     * retrying with the same content won't help.
     */
    data class ContentTooLarge(val size: Long, val maxAllowed: Long) : SendError()

    /**
     * Each send attempt may result in us having to establish sessions with linked devices and such. This indicates that we hit our max attempt count while
     * trying to handle these situations. It should be safe to retry with normal backoff.
     */
    class SessionAttemptsExhausted : SendError()

    /** We needed to establish a session, but the server was missing either a signed or kyber prekey for the user. */
    data class PreKeyUnavailable(val reason: String) : SendError()

    /** You're rate-limited. Use the [retryAfter] for your backoff. */
    data class RateLimited(val retryAfter: Duration?) : SendError()

    /** A generic, retryable network error. */
    data class NetworkError(val exception: IOException) : SendError()

    /** An unexpected error. You should likely crash. */
    data class ApplicationError(val exception: Throwable) : SendError()
  }
}
