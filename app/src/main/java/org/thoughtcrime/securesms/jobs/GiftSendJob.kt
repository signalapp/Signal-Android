package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.badges.gifts.Gifts
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.sharing.MultiShareSender
import org.thoughtcrime.securesms.sms.MessageSender
import java.util.concurrent.TimeUnit

/**
 * Sends a message to the given recipient containing a redeemable badge token.
 * This job assumes that the client has already determined whether the given recipient can receive a gift badge.
 */
@Deprecated("Replaced with InAppPaymentGiftSendJob")
class GiftSendJob private constructor(parameters: Parameters, private val recipientId: RecipientId, private val additionalMessage: String?) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(GiftSendJob::class.java)

    const val KEY = "SendGiftJob"
    const val DATA_RECIPIENT_ID = "data.recipient.id"
    const val DATA_ADDITIONAL_MESSAGE = "data.additional.message"
  }

  constructor(recipientId: RecipientId, additionalMessage: String?) :
    this(
      parameters = Parameters.Builder()
        .build(),
      recipientId = recipientId,
      additionalMessage = additionalMessage
    )

  override fun serialize(): ByteArray? = JsonJobData.Builder()
    .putLong(DATA_RECIPIENT_ID, recipientId.toLong())
    .putString(DATA_ADDITIONAL_MESSAGE, additionalMessage)
    .serialize()

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    Log.i(TAG, "Getting data and generating message for gift send to $recipientId")

    val token = JsonJobData.deserialize(this.inputData).getStringAsBlob(DonationReceiptRedemptionJob.INPUT_RECEIPT_CREDENTIAL_PRESENTATION) ?: return Result.failure()

    val recipient = Recipient.resolved(recipientId)

    if (recipient.isGroup || recipient.isDistributionList || recipient.registered != RecipientTable.RegisteredState.REGISTERED) {
      Log.w(TAG, "Invalid recipient $recipientId for gift send.")
      return Result.failure()
    }

    val thread = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)

    val outgoingMessage = Gifts.createOutgoingGiftMessage(
      recipient = recipient,
      expiresIn = TimeUnit.SECONDS.toMillis(recipient.expiresInSeconds.toLong()),
      sentTimestamp = System.currentTimeMillis(),
      giftBadge = GiftBadge(redemptionToken = token.toByteString())
    )

    Log.i(TAG, "Sending gift badge to $recipientId...")
    var didInsert = false
    MessageSender.send(context, outgoingMessage, thread, MessageSender.SendType.SIGNAL, null) {
      didInsert = true
    }

    return if (didInsert) {
      Log.i(TAG, "Successfully inserted outbox message for gift", true)

      val trimmedMessage = additionalMessage?.trim()
      if (!trimmedMessage.isNullOrBlank()) {
        Log.i(TAG, "Sending additional message...")

        val result = MultiShareSender.sendSync(
          MultiShareArgs.Builder(setOf(ContactSearchKey.RecipientSearchKey(recipientId, false)))
            .withDraftText(trimmedMessage)
            .build()
        )

        if (result.containsFailures()) {
          Log.w(TAG, "Failed to send additional message, but gift sent fine.", true)
        }

        Result.success()
      } else {
        Result.success()
      }
    } else {
      Log.w(TAG, "Failed to insert outbox message for gift", true)
      Result.failure()
    }
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to submit send of gift badge to $recipientId")
  }

  class Factory : Job.Factory<GiftSendJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): GiftSendJob {
      val data = JsonJobData.deserialize(serializedData)
      val recipientId = RecipientId.from(data.getLong(DATA_RECIPIENT_ID))
      val additionalMessage = data.getStringOrDefault(DATA_ADDITIONAL_MESSAGE, null)

      return GiftSendJob(parameters, recipientId, additionalMessage)
    }
  }
}
