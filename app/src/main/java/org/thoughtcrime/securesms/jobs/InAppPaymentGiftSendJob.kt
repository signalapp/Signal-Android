/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.badges.gifts.Gifts
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.sharing.MultiShareSender
import org.thoughtcrime.securesms.sms.MessageSender
import kotlin.time.Duration.Companion.seconds

/**
 * Sends a message and redeemable token to the recipient contained within the InAppPayment
 */
class InAppPaymentGiftSendJob private constructor(
  private val inAppPaymentId: InAppPaymentTable.InAppPaymentId,
  parameters: Parameters
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(InAppPaymentGiftSendJob::class.java)
    const val KEY = "InAppPurchaseOneTimeGiftSendJob"

    fun create(inAppPayment: InAppPaymentTable.InAppPayment): Job {
      return InAppPaymentGiftSendJob(
        inAppPaymentId = inAppPayment.id,
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray = inAppPaymentId.serialize().toByteArray()

  override fun getFactoryKey(): String = KEY

  override fun onFailure() {
    warning("Failed to send gift.")

    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)
    if (inAppPayment != null && inAppPayment.data.error == null) {
      warn(TAG, "Marking an unknown error. Check logs for more details.")
      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          notified = true,
          state = InAppPaymentTable.State.END,
          data = inAppPayment.data.copy(
            error = InAppPaymentData.Error(
              type = InAppPaymentData.Error.Type.UNKNOWN
            )
          )
        )
      )
    }
  }

  override fun onRun() {
    val inAppPayment = SignalDatabase.inAppPayments.getById(inAppPaymentId)

    requireNotNull(inAppPayment, "Not found.")
    check(inAppPayment!!.type == InAppPaymentType.ONE_TIME_GIFT, "Invalid type: ${inAppPayment.type}")
    check(inAppPayment.state == InAppPaymentTable.State.PENDING, "Invalid state: ${inAppPayment.state}")
    requireNotNull(inAppPayment.data.redemption, "No redemption present on data")
    check(inAppPayment.data.redemption!!.stage == InAppPaymentData.RedemptionState.Stage.REDEMPTION_STARTED, "Invalid stage: ${inAppPayment.data.redemption.stage}")

    val recipient = Recipient.resolved(RecipientId.from(requireNotNull(inAppPayment.data.recipientId, "No recipient on data.")))
    val token = requireNotNull(inAppPayment.data.redemption.receiptCredentialPresentation, "No presentation present on data.")

    if (!recipient.isIndividual || recipient.registered != RecipientTable.RegisteredState.REGISTERED) {
      SignalDatabase.inAppPayments.update(
        inAppPayment.copy(
          notified = false,
          state = InAppPaymentTable.State.END,
          data = inAppPayment.data.copy(
            error = InAppPaymentData.Error(
              type = InAppPaymentData.Error.Type.INVALID_GIFT_RECIPIENT
            )
          )
        )
      )

      throw Exception("Invalid recipient ${recipient.id} for gift send.")
    }

    val thread = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    val outgoingMessage = Gifts.createOutgoingGiftMessage(
      recipient = recipient,
      expiresIn = recipient.expiresInSeconds.toLong().seconds.inWholeMilliseconds,
      sentTimestamp = System.currentTimeMillis(),
      giftBadge = GiftBadge(redemptionToken = token)
    )

    info("Sending gift badge to ${recipient.id}")
    var didInsert = false
    MessageSender.send(context, outgoingMessage, thread, MessageSender.SendType.SIGNAL, null) {
      didInsert = true
    }

    if (didInsert) {
      info("Successfully inserted outbox message for gift.")

      val trimmedMessage = inAppPayment.data.additionalMessage?.trim()
      if (!trimmedMessage.isNullOrBlank()) {
        info("Sending additional message...")

        val result = MultiShareSender.sendSync(
          MultiShareArgs.Builder(setOf(ContactSearchKey.RecipientSearchKey(recipient.id, false)))
            .withDraftText(trimmedMessage)
            .build()
        )

        if (result.containsFailures()) {
          warning("Failed to send additional message but gift is fine.")
        }
      }
    } else {
      warning("Failed to insert outbox message for gift.")
    }

    SignalDatabase.inAppPayments.update(
      inAppPayment = inAppPayment.copy(
        state = InAppPaymentTable.State.END
      )
    )
  }

  private fun check(condition: Boolean, message: String) {
    if (!condition) {
      warning(message)
      throw Exception(message)
    }
  }

  private fun <T> requireNotNull(data: T?, message: String): T {
    if (data == null) {
      warning(message)
      throw Exception(message)
    }

    return data
  }

  override fun onShouldRetry(e: Exception): Boolean = e is InAppPaymentRetryException

  private fun info(message: String, throwable: Throwable? = null) {
    Log.i(TAG, "InAppPayment $inAppPaymentId: $message", throwable, true)
  }

  private fun warning(message: String, throwable: Throwable? = null) {
    Log.w(TAG, "InAppPayment $inAppPaymentId: $message", throwable, true)
  }

  class Factory : Job.Factory<InAppPaymentGiftSendJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): InAppPaymentGiftSendJob {
      return InAppPaymentGiftSendJob(
        inAppPaymentId = InAppPaymentTable.InAppPaymentId(serializedData!!.decodeToString().toLong()),
        parameters = parameters
      )
    }
  }
}
