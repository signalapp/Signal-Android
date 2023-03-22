package org.whispersystems.signalservice.api.messages

import org.signal.libsignal.protocol.message.DecryptionErrorMessage
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.whispersystems.signalservice.api.InvalidMessageStructureException
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContextV2
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.ReceiptMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.StoryMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.TypingMessage

/**
 * Validates an [Envelope] and its decrypted [Content] so that we know the message can be processed safely
 * down the line.
 *
 * Mostly makes sure that UUIDs are valid, required fields are presents, etc.
 */
object EnvelopeContentValidator {

  fun validate(envelope: Envelope, content: Content): Result {
    return when {
      envelope.story && !content.meetsStoryFlagCriteria() -> Result.Invalid("Envelope was flagged as a story, but it did not have any story-related content!")
      content.hasDataMessage() -> validateDataMessage(envelope, content.dataMessage)
      content.hasSyncMessage() -> validateSyncMessage(envelope, content.syncMessage)
      content.hasCallMessage() -> Result.Valid
      content.hasReceiptMessage() -> validateReceiptMessage(content.receiptMessage)
      content.hasTypingMessage() -> validateTypingMessage(envelope, content.typingMessage)
      content.hasDecryptionErrorMessage() -> validateDecryptionErrorMessage(content.decryptionErrorMessage.toByteArray())
      content.hasStoryMessage() -> validateStoryMessage(content.storyMessage)
      content.hasPniSignatureMessage() -> Result.Valid
      content.hasSenderKeyDistributionMessage() -> Result.Valid
      else -> Result.Invalid("Content is empty!")
    }
  }

  private fun validateDataMessage(envelope: Envelope, dataMessage: DataMessage): Result {
    if (dataMessage.requiredProtocolVersion > DataMessage.ProtocolVersion.CURRENT_VALUE) {
      return Result.UnsupportedDataMessage(
        ourVersion = DataMessage.ProtocolVersion.CURRENT_VALUE,
        theirVersion = dataMessage.requiredProtocolVersion
      )
    }

    if (!dataMessage.hasTimestamp()) {
      return Result.Invalid("[DataMessage] Missing timestamp!")
    }

    if (dataMessage.timestamp != envelope.timestamp) {
      Result.Invalid("[DataMessage] Timestamps don't match! envelope: ${envelope.timestamp}, content: ${dataMessage.timestamp}")
    }

    if (dataMessage.hasQuote() && dataMessage.quote.authorUuid.isNullOrInvalidUuid()) {
      return Result.Invalid("[DataMessage] Invalid UUID on quote!")
    }

    if (dataMessage.contactList.any { it.hasAvatar() && it.avatar.avatar.isPresentAndInvalid() }) {
      return Result.Invalid("[DataMessage] Invalid AttachmentPointer on DataMessage.contactList.avatar!")
    }

    if (dataMessage.previewList.any { it.hasImage() && it.image.isPresentAndInvalid() }) {
      return Result.Invalid("[DataMessage] Invalid AttachmentPointer on DataMessage.previewList.image!")
    }

    if (dataMessage.bodyRangesList.any { it.hasMentionUuid() && it.mentionUuid.isNullOrInvalidUuid() }) {
      return Result.Invalid("[DataMessage] Invalid UUID on body range!")
    }

    if (dataMessage.hasSticker() && dataMessage.sticker.data.isNullOrInvalid()) {
      return Result.Invalid("[DataMessage] Invalid AttachmentPointer on DataMessage.sticker!")
    }

    if (dataMessage.hasReaction()) {
      if (!dataMessage.reaction.hasTargetSentTimestamp()) {
        return Result.Invalid("[DataMessage] Missing timestamp on DataMessage.reaction!")
      }
      if (dataMessage.reaction.targetAuthorUuid.isNullOrInvalidUuid()) {
        return Result.Invalid("[DataMessage] Invalid UUID on DataMessage.reaction!")
      }
    }

    if (dataMessage.hasDelete() && !dataMessage.delete.hasTargetSentTimestamp()) {
      return Result.Invalid("[DataMessage] Missing timestamp on DataMessage.delete!")
    }

    if (dataMessage.hasStoryContext() && dataMessage.storyContext.authorUuid.isNullOrInvalidUuid()) {
      return Result.Invalid("[DataMessage] Invalid UUID on DataMessage.storyContext!")
    }

    if (dataMessage.hasGiftBadge()) {
      if (!dataMessage.giftBadge.hasReceiptCredentialPresentation()) {
        return Result.Invalid("[DataMessage] Missing DataMessage.giftBadge.receiptCredentialPresentation!")
      }
      if (!dataMessage.giftBadge.hasReceiptCredentialPresentation()) {
        try {
          ReceiptCredentialPresentation(dataMessage.giftBadge.receiptCredentialPresentation.toByteArray())
        } catch (e: InvalidInputException) {
          return Result.Invalid("[DataMessage] Invalid DataMessage.giftBadge.receiptCredentialPresentation!")
        }
      }
    }

    if (dataMessage.attachmentsList.any { it.isNullOrInvalid() }) {
      return Result.Invalid("[DataMessage] Invalid attachments!")
    }

    return Result.Valid
  }

  private fun validateSyncMessage(envelope: Envelope, syncMessage: SyncMessage): Result {
    if (syncMessage.hasSent()) {
      val validAddress = syncMessage.sent.destinationUuid.isValidUuid()
      val hasDataGroup = syncMessage.sent.message?.hasGroupV2() ?: false
      val hasStoryGroup = syncMessage.sent.storyMessage?.hasGroup() ?: false
      val hasStoryManifest = syncMessage.sent.storyMessageRecipientsList.isNotEmpty()

      if (hasDataGroup) {
        validateGroupContextV2(syncMessage.sent.message.groupV2, "[SyncMessage.Sent.Message]")?.let { return it }
      }

      if (hasStoryGroup) {
        validateGroupContextV2(syncMessage.sent.storyMessage.group, "[SyncMessage.Sent.StoryMessage]")?.let { return it }
      }

      if (!validAddress && !hasDataGroup && !hasStoryGroup && !hasStoryManifest) {
        return Result.Invalid("[SyncMessage] No valid destination! Checked the destination, DataMessage.group, StoryMessage.group, and storyMessageRecipientList")
      }

      for (status in syncMessage.sent.unidentifiedStatusList) {
        if (status.destinationUuid.isNullOrInvalidUuid()) {
          return Result.Invalid("[SyncMessage] Invalid UUID in SyncMessage.sent.unidentifiedStatusList!")
        }
      }

      return if (syncMessage.sent.hasMessage()) {
        validateDataMessage(envelope, syncMessage.sent.message)
      } else if (syncMessage.sent.hasStoryMessage()) {
        validateStoryMessage(syncMessage.sent.storyMessage)
      } else {
        Result.Invalid("[SyncMessage] Empty SyncMessage.sent!")
      }
    }

    if (syncMessage.readList.any { it.senderUuid.isNullOrInvalidUuid() }) {
      return Result.Invalid("[SyncMessage] Invalid UUID in SyncMessage.readList!")
    }

    if (syncMessage.viewedList.any { it.senderUuid.isNullOrInvalidUuid() }) {
      return Result.Invalid("[SyncMessage] Invalid UUID in SyncMessage.viewList!")
    }

    if (syncMessage.hasViewOnceOpen() && syncMessage.viewOnceOpen.senderUuid.isNullOrInvalidUuid()) {
      return Result.Invalid("[SyncMessage] Invalid UUID in SyncMessage.viewOnceOpen!")
    }

    if (syncMessage.hasVerified() && syncMessage.verified.destinationUuid.isNullOrInvalidUuid()) {
      return Result.Invalid("[SyncMessage] Invalid UUID in SyncMessage.verified!")
    }

    if (syncMessage.stickerPackOperationList.any { !it.hasPackId() }) {
      return Result.Invalid("[SyncMessage] Missing packId in stickerPackOperationList!")
    }

    if (syncMessage.hasBlocked() && syncMessage.blocked.uuidsList.any { it.isNullOrInvalidUuid() }) {
      return Result.Invalid("[SyncMessage] Invalid UUID in SyncMessage.blocked!")
    }

    if (syncMessage.hasMessageRequestResponse() && !syncMessage.messageRequestResponse.hasGroupId() && syncMessage.messageRequestResponse.threadUuid.isNullOrInvalidUuid()) {
      return Result.Invalid("[SyncMessage] Invalid UUID in SyncMessage.messageRequestResponse!")
    }

    return Result.Valid
  }

  private fun validateReceiptMessage(receiptMessage: ReceiptMessage): Result {
    return if (!receiptMessage.hasType()) {
      Result.Invalid("[ReceiptMessage] Missing type!")
    } else {
      Result.Valid
    }
  }

  private fun validateTypingMessage(envelope: Envelope, typingMessage: TypingMessage): Result {
    return if (!typingMessage.hasTimestamp()) {
      return Result.Invalid("[TypingMessage] Missing timestamp!")
    } else if (typingMessage.hasTimestamp() && typingMessage.timestamp != envelope.timestamp) {
      Result.Invalid("[TypingMessage] Timestamps don't match! envelope: ${envelope.timestamp}, content: ${typingMessage.timestamp}")
    } else if (!typingMessage.hasAction()) {
      Result.Invalid("[TypingMessage] Missing action!")
    } else {
      Result.Valid
    }
  }

  private fun validateDecryptionErrorMessage(serializedDecryptionErrorMessage: ByteArray): Result {
    return try {
      DecryptionErrorMessage(serializedDecryptionErrorMessage)
      Result.Valid
    } catch (e: InvalidMessageStructureException) {
      Result.Invalid("[DecryptionErrorMessage] Bad decryption error message!", e)
    }
  }

  private fun validateStoryMessage(storyMessage: StoryMessage): Result {
    if (storyMessage.hasGroup()) {
      validateGroupContextV2(storyMessage.group, "[StoryMessage]")?.let { return it }
    }

    return Result.Valid
  }

  private fun AttachmentPointer?.isNullOrInvalid(): Boolean {
    return this == null || this.attachmentIdentifierCase == AttachmentPointer.AttachmentIdentifierCase.ATTACHMENTIDENTIFIER_NOT_SET
  }

  private fun AttachmentPointer?.isPresentAndInvalid(): Boolean {
    return this != null && this.attachmentIdentifierCase == AttachmentPointer.AttachmentIdentifierCase.ATTACHMENTIDENTIFIER_NOT_SET
  }

  private fun String?.isValidUuid(): Boolean {
    return UuidUtil.isUuid(this)
  }

  private fun String?.isNullOrInvalidUuid(): Boolean {
    return !UuidUtil.isUuid(this)
  }

  private fun Content?.meetsStoryFlagCriteria(): Boolean {
    return when {
      this == null -> false
      this.hasSenderKeyDistributionMessage() -> true
      this.hasStoryMessage() -> true
      this.hasDataMessage() && this.dataMessage.hasStoryContext() && this.dataMessage.hasGroupV2() -> true
      this.hasDataMessage() && this.dataMessage.hasDelete() -> true
      else -> false
    }
  }

  private fun validateGroupContextV2(groupContext: GroupContextV2, prefix: String): Result.Invalid? {
    return if (!groupContext.hasMasterKey()) {
      Result.Invalid("$prefix Missing GV2 master key!")
    } else if (!groupContext.hasRevision()) {
      Result.Invalid("$prefix Missing GV2 revision!")
    } else {
      try {
        GroupMasterKey(groupContext.masterKey.toByteArray())
        null
      } catch (e: InvalidInputException) {
        Result.Invalid("$prefix Bad GV2 master key!", e)
      }
    }
  }

  sealed class Result {
    /** Content is valid. */
    object Valid : Result()

    /** The [DataMessage.requiredProtocolVersion_] is newer than the one we support. */
    class UnsupportedDataMessage(val ourVersion: Int, val theirVersion: Int) : Result()

    /** The contents of the proto do not match our expectations, e.g. invalid UUIDs, missing required fields, etc.  */
    class Invalid(val reason: String, val throwable: Throwable = Throwable()) : Result()
  }
}
