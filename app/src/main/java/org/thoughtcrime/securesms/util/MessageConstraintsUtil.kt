package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Helpers for determining if a message send/receive is valid for those that
 * have strict time limits.
 */
object MessageConstraintsUtil {
  private val RECEIVE_THRESHOLD = TimeUnit.DAYS.toMillis(2)
  private val SEND_THRESHOLD = TimeUnit.DAYS.toMillis(1)

  const val MAX_EDIT_COUNT = 10

  @JvmStatic
  fun isValidRemoteDeleteReceive(targetMessage: MessageRecord, deleteSenderId: RecipientId, deleteServerTimestamp: Long): Boolean {
    val selfIsDeleteSender = isSelf(deleteSenderId)

    val isValidIncomingOutgoing = selfIsDeleteSender && targetMessage.isOutgoing || !selfIsDeleteSender && !targetMessage.isOutgoing
    val isValidSender = targetMessage.fromRecipient.id == deleteSenderId || selfIsDeleteSender && targetMessage.isOutgoing

    val messageTimestamp = if (selfIsDeleteSender && targetMessage.isOutgoing) targetMessage.dateSent else targetMessage.serverTimestamp

    return isValidIncomingOutgoing &&
      isValidSender &&
      ((deleteServerTimestamp - messageTimestamp < RECEIVE_THRESHOLD) || (selfIsDeleteSender && targetMessage.isOutgoing))
  }

  @JvmStatic
  fun isValidEditMessageReceive(targetMessage: MessageRecord, editSender: Recipient, editServerTimestamp: Long): Boolean {
    return isValidRemoteDeleteReceive(targetMessage, editSender.id, editServerTimestamp)
  }

  @JvmStatic
  fun isValidRemoteDeleteSend(targetMessages: Collection<MessageRecord>, currentTime: Long): Boolean {
    // TODO [greyson] [remote-delete] Update with server timestamp when available for outgoing messages
    return targetMessages.all { isValidRemoteDeleteSend(it, currentTime) }
  }

  @JvmStatic
  fun isWithinMaxEdits(targetMessage: MessageRecord): Boolean {
    return targetMessage.revisionNumber < MAX_EDIT_COUNT
  }

  @JvmStatic
  fun getEditMessageThresholdHours(): Int {
    return SEND_THRESHOLD.milliseconds.inWholeHours.toInt()
  }

  /**
   * Check if at the current time a target message can be edited
   */
  @JvmStatic
  fun isValidEditMessageSend(targetMessage: MessageRecord, currentTime: Long): Boolean {
    val originalMessage = if (targetMessage.isEditMessage && targetMessage.id != targetMessage.originalMessageId?.id) {
      SignalDatabase.messages.getMessageRecord(targetMessage.originalMessageId!!.id)
    } else {
      targetMessage
    }
    return isValidRemoteDeleteSend(originalMessage, currentTime) &&
      targetMessage.revisionNumber < MAX_EDIT_COUNT &&
      !targetMessage.isViewOnceMessage() &&
      !targetMessage.hasAudio() &&
      !targetMessage.hasSharedContact() &&
      !targetMessage.hasSticker()
  }

  /**
   * Check regardless of timing, whether a target message can be edited
   */
  @JvmStatic
  fun isValidEditMessageSend(targetMessage: MessageRecord): Boolean {
    return isValidEditMessageSend(targetMessage, targetMessage.dateSent)
  }

  private fun isValidRemoteDeleteSend(message: MessageRecord, currentTime: Long): Boolean {
    return !message.isUpdate &&
      message.isOutgoing &&
      message.isPush &&
      (!message.toRecipient.isGroup || message.toRecipient.isActiveGroup) &&
      !message.isRemoteDelete &&
      !message.hasGiftBadge() &&
      !message.isPaymentNotification &&
      !message.isPaymentTombstone &&
      (currentTime - message.dateSent < SEND_THRESHOLD || message.toRecipient.isSelf)
  }

  private fun isSelf(recipientId: RecipientId): Boolean {
    return Recipient.isSelfSet && Recipient.self().id == recipientId
  }
}
