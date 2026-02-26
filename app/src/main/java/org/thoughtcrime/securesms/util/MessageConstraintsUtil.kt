package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Helpers for determining if a message send/receive is valid for those that
 * have strict time limits.
 */
object MessageConstraintsUtil {
  private val SEND_THRESHOLD = RemoteConfig.regularDeleteThreshold.milliseconds.inWholeMilliseconds
  private val RECEIVE_THRESHOLD = SEND_THRESHOLD + 1.days.inWholeMilliseconds
  private val ADMIN_SEND_THRESHOLD = RemoteConfig.adminDeleteThreshold.milliseconds.inWholeMilliseconds
  private val ADMIN_RECEIVE_THRESHOLD = ADMIN_SEND_THRESHOLD + 1.days.inWholeMilliseconds

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
  fun isValidAdminDeleteReceive(targetMessage: MessageRecord, deleteSender: Recipient, deleteServerTimestamp: Long, groupRecord: GroupRecord): Boolean {
    val isValidSender = groupRecord.isAdmin(deleteSender)
    val messageTimestamp = targetMessage.dateSent

    return isValidSender && (deleteServerTimestamp - messageTimestamp < ADMIN_RECEIVE_THRESHOLD)
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
  fun isValidAdminDeleteSend(targetMessages: Collection<MessageRecord>, currentTime: Long, isAdmin: Boolean): Boolean {
    return targetMessages.all { isValidAdminDeleteSend(it, currentTime, isAdmin) }
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

    val isNoteToSelf = targetMessage.toRecipient.isSelf && targetMessage.fromRecipient.isSelf

    return isValidRemoteDeleteSend(originalMessage, currentTime) &&
      (isNoteToSelf || targetMessage.revisionNumber < MAX_EDIT_COUNT) &&
      !targetMessage.isViewOnceMessage() &&
      !targetMessage.hasAudio() &&
      !targetMessage.hasSharedContact() &&
      !targetMessage.hasSticker() &&
      !targetMessage.hasPoll()
  }

  /**
   * Check regardless of timing, whether a target message can be edited
   */
  @JvmStatic
  fun isValidEditMessageSend(targetMessage: MessageRecord): Boolean {
    return isValidEditMessageSend(targetMessage, targetMessage.dateSent)
  }

  fun isValidRemoteDeleteSend(message: MessageRecord, currentTime: Long): Boolean {
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

  private fun isValidAdminDeleteSend(message: MessageRecord, currentTime: Long, isAdmin: Boolean): Boolean {
    return RemoteConfig.sendAdminDelete &&
      isAdmin &&
      !message.isUpdate &&
      message.isPush &&
      (!message.toRecipient.isGroup || message.toRecipient.isActiveGroup) &&
      !message.isRemoteDelete &&
      !message.hasGiftBadge() &&
      !message.isPaymentNotification &&
      !message.isPaymentTombstone &&
      (currentTime - message.dateSent < ADMIN_SEND_THRESHOLD)
  }

  private fun isSelf(recipientId: RecipientId): Boolean {
    return Recipient.isSelfSet && Recipient.self().id == recipientId
  }
}
