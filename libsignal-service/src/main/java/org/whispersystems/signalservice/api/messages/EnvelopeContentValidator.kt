package org.whispersystems.signalservice.api.messages

import okio.ByteString
import org.signal.libsignal.protocol.message.DecryptionErrorMessage
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.AttachmentPointer
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.EditMessage
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.GroupContextV2
import org.whispersystems.signalservice.internal.push.PniSignatureMessage
import org.whispersystems.signalservice.internal.push.ReceiptMessage
import org.whispersystems.signalservice.internal.push.StoryMessage
import org.whispersystems.signalservice.internal.push.SyncMessage
import org.whispersystems.signalservice.internal.push.TypingMessage
import org.whispersystems.signalservice.internal.util.Util

/**
 * Validates an [Envelope] and its decrypted [Content] so that we know the message can be processed safely
 * down the line.
 *
 * Mostly makes sure that UUIDs are valid, required fields are presents, etc.
 */
object EnvelopeContentValidator {

  private const val MAX_POLL_CHARACTER_LENGTH = 100
  private const val MIN_POLL_OPTIONS = 2

  fun validate(envelope: Envelope, content: Content, localAci: ACI): Result {
    if (envelope.type == Envelope.Type.PLAINTEXT_CONTENT) {
      validatePlaintextContent(content)?.let { return it }
    }

    val sourceServiceId = ServiceId.parseOrNull(envelope.sourceServiceId, envelope.sourceServiceIdBinary)
    if (Util.anyNotNull(envelope.sourceServiceId, envelope.sourceServiceIdBinary) && sourceServiceId.isNullOrInvalidServiceId()) {
      return Result.Invalid("Envelope had an invalid sourceServiceId!")
    }

    if (content.senderKeyDistributionMessage != null) {
      validateSenderKeyDistributionMessage(content.senderKeyDistributionMessage.toByteArray())?.let { return it }
    }

    if (content.pniSignatureMessage != null) {
      validatePniSignatureMessage(content.pniSignatureMessage)?.let { return it }
    }

    // Reminder: envelope.destinationServiceId was already validated since we need that for decryption

    return when {
      envelope.story == true && !content.meetsStoryFlagCriteria() -> Result.Invalid("Envelope was flagged as a story, but it did not have any story-related content!")
      content.dataMessage != null -> validateDataMessage(envelope, content.dataMessage)
      content.syncMessage != null -> validateSyncMessage(envelope, content.syncMessage, localAci)
      content.callMessage != null -> Result.Valid
      content.nullMessage != null -> Result.Valid
      content.receiptMessage != null -> validateReceiptMessage(content.receiptMessage)
      content.typingMessage != null -> validateTypingMessage(envelope, content.typingMessage)
      content.decryptionErrorMessage != null -> validateDecryptionErrorMessage(content.decryptionErrorMessage.toByteArray())
      content.storyMessage != null -> validateStoryMessage(content.storyMessage)
      content.editMessage != null -> validateEditMessage(content.editMessage)
      content.pniSignatureMessage != null -> Result.Valid
      content.senderKeyDistributionMessage != null -> Result.Valid
      else -> Result.Invalid("Content is empty!")
    }
  }

  private fun validateDataMessage(envelope: Envelope, dataMessage: DataMessage): Result {
    if (dataMessage.requiredProtocolVersion != null && dataMessage.requiredProtocolVersion > DataMessage.ProtocolVersion.CURRENT.value) {
      return Result.UnsupportedDataMessage(
        ourVersion = DataMessage.ProtocolVersion.CURRENT.value,
        theirVersion = dataMessage.requiredProtocolVersion
      )
    }

    if (dataMessage.timestamp == null) {
      return Result.Invalid("[DataMessage] Missing timestamp!")
    }

    if (dataMessage.timestamp != envelope.timestamp) {
      return Result.Invalid("[DataMessage] Timestamps don't match! envelope: ${envelope.timestamp}, content: ${dataMessage.timestamp}")
    }

    if (dataMessage.quote != null && ACI.parseOrNull(dataMessage.quote.authorAci, dataMessage.quote.authorAciBinary).isNullOrInvalidServiceId()) {
      return Result.Invalid("[DataMessage] Invalid ACI on quote!")
    }

    if (dataMessage.contact.any { it.avatar != null && it.avatar.avatar.isPresentAndInvalid() }) {
      return Result.Invalid("[DataMessage] Invalid AttachmentPointer on DataMessage.contactList.avatar!")
    }

    if (dataMessage.preview.any { it.image != null && it.image.isPresentAndInvalid() }) {
      return Result.Invalid("[DataMessage] Invalid AttachmentPointer on DataMessage.previewList.image!")
    }

    if (dataMessage.bodyRanges.any { Util.anyNotNull(it.mentionAci, it.mentionAciBinary) && ACI.parseOrNull(it.mentionAci, it.mentionAciBinary).isNullOrInvalidServiceId() }) {
      return Result.Invalid("[DataMessage] Invalid ACI on body range!")
    }

    if (dataMessage.sticker != null && dataMessage.sticker.data_.isNullOrInvalid()) {
      return Result.Invalid("[DataMessage] Invalid AttachmentPointer on DataMessage.sticker!")
    }

    if (dataMessage.reaction != null) {
      if (dataMessage.reaction.targetSentTimestamp == null) {
        return Result.Invalid("[DataMessage] Missing timestamp on DataMessage.reaction!")
      }

      if (ACI.parseOrNull(dataMessage.reaction.targetAuthorAci, dataMessage.reaction.targetAuthorAciBinary).isNullOrInvalidServiceId()) {
        return Result.Invalid("[DataMessage] Invalid ACI on DataMessage.reaction!")
      }
    }

    if (dataMessage.delete != null && dataMessage.delete.targetSentTimestamp == null) {
      return Result.Invalid("[DataMessage] Missing timestamp on DataMessage.delete!")
    }

    if (dataMessage.storyContext != null && ACI.parseOrNull(dataMessage.storyContext.authorAci, dataMessage.storyContext.authorAciBinary).isNullOrInvalidServiceId()) {
      return Result.Invalid("[DataMessage] Invalid ACI on DataMessage.storyContext!")
    }

    if (dataMessage.giftBadge != null) {
      if (dataMessage.giftBadge.receiptCredentialPresentation == null) {
        return Result.Invalid("[DataMessage] Missing DataMessage.giftBadge.receiptCredentialPresentation!")
      }

      try {
        ReceiptCredentialPresentation(dataMessage.giftBadge.receiptCredentialPresentation.toByteArray())
      } catch (e: InvalidInputException) {
        return Result.Invalid("[DataMessage] Invalid DataMessage.giftBadge.receiptCredentialPresentation!")
      }
    }

    if (dataMessage.attachments.any { it.isNullOrInvalid() }) {
      return Result.Invalid("[DataMessage] Invalid attachments!")
    }

    if (dataMessage.groupV2 != null) {
      validateGroupContextV2(dataMessage.groupV2, "[DataMessage]")?.let { return it }
    }

    if (dataMessage.pollCreate != null && (dataMessage.pollCreate.hasInvalidPollQuestion() || dataMessage.pollCreate.hasInvalidPollOptions() || dataMessage.pollCreate.allowMultiple == null)) {
      return Result.Invalid("[DataMessage] Invalid poll create!")
    }

    if (dataMessage.pollTerminate != null && dataMessage.pollTerminate.targetSentTimestamp == null) {
      return Result.Invalid("[DataMessage] Invalid poll terminate!")
    }

    if (dataMessage.pollVote != null && (dataMessage.pollVote.targetAuthorAciBinary.isNullOrInvalidAci() || dataMessage.pollVote.targetSentTimestamp == null || dataMessage.pollVote.voteCount == null)) {
      return Result.Invalid("[DataMessage] Invalid poll vote!")
    }

    return Result.Valid
  }

  private fun DataMessage.PollCreate.hasInvalidPollQuestion(): Boolean {
    return this.question.isNullOrBlank() || this.question.length > MAX_POLL_CHARACTER_LENGTH
  }

  private fun DataMessage.PollCreate.hasInvalidPollOptions(): Boolean {
    return this.options.size < MIN_POLL_OPTIONS || this.options.any { option -> option.length > MAX_POLL_CHARACTER_LENGTH }
  }

  private fun validateSyncMessage(envelope: Envelope, syncMessage: SyncMessage, localAci: ACI): Result {
    // Source serviceId was already determined to be a valid serviceId in general
    val sourceServiceId = ServiceId.parseOrThrow(envelope.sourceServiceId, envelope.sourceServiceIdBinary)

    if (sourceServiceId != localAci) {
      return Result.Invalid("[SyncMessage] Source was not our own account!")
    }

    if (syncMessage.sent != null) {
      val validAddress = ServiceId.parseOrNull(syncMessage.sent.destinationServiceId, syncMessage.sent.destinationServiceIdBinary) != null
      val hasDataGroup = syncMessage.sent.message?.groupV2 != null
      val hasStoryGroup = syncMessage.sent.storyMessage?.group != null
      val hasStoryManifest = syncMessage.sent.storyMessageRecipients.isNotEmpty()
      val hasEditMessageGroup = syncMessage.sent.editMessage?.dataMessage?.groupV2 != null

      if (hasDataGroup) {
        validateGroupContextV2(syncMessage.sent.message!!.groupV2!!, "[SyncMessage.Sent.Message]")?.let { return it }
      }

      if (hasStoryGroup) {
        validateGroupContextV2(syncMessage.sent.storyMessage!!.group!!, "[SyncMessage.Sent.StoryMessage]")?.let { return it }
      }

      if (hasEditMessageGroup) {
        validateGroupContextV2(syncMessage.sent.editMessage!!.dataMessage!!.groupV2!!, "[SyncMessage.Sent.EditMessage]")?.let { return it }
      }

      if (!validAddress && !hasDataGroup && !hasStoryGroup && !hasStoryManifest && !hasEditMessageGroup) {
        return Result.Invalid("[SyncMessage] No valid destination! Checked the destination, DataMessage.group, StoryMessage.group, EditMessage.group and storyMessageRecipientList")
      }

      for (status in syncMessage.sent.unidentifiedStatus) {
        if (ServiceId.parseOrNull(status.destinationServiceId, status.destinationServiceIdBinary).isNullOrInvalidServiceId()) {
          return Result.Invalid("[SyncMessage] Invalid ServiceId in SyncMessage.sent.unidentifiedStatusList!")
        }
      }

      return if (syncMessage.sent.message != null) {
        validateDataMessage(envelope, syncMessage.sent.message)
      } else if (syncMessage.sent.storyMessage != null) {
        validateStoryMessage(syncMessage.sent.storyMessage)
      } else if (syncMessage.sent.storyMessageRecipients.isNotEmpty()) {
        Result.Valid
      } else if (syncMessage.sent.editMessage != null) {
        validateEditMessage(syncMessage.sent.editMessage)
      } else {
        Result.Invalid("[SyncMessage] Empty SyncMessage.sent!")
      }
    }

    if (syncMessage.read.any { ACI.parseOrNull(it.senderAci, it.senderAciBinary).isNullOrInvalidServiceId() }) {
      return Result.Invalid("[SyncMessage] Invalid ACI in SyncMessage.readList!")
    }

    if (syncMessage.viewed.any { ACI.parseOrNull(it.senderAci, it.senderAciBinary).isNullOrInvalidServiceId() }) {
      return Result.Invalid("[SyncMessage] Invalid ACI in SyncMessage.viewList!")
    }

    if (syncMessage.viewOnceOpen != null && ACI.parseOrNull(syncMessage.viewOnceOpen.senderAci, syncMessage.viewOnceOpen.senderAciBinary).isNullOrInvalidServiceId()) {
      return Result.Invalid("[SyncMessage] Invalid ACI in SyncMessage.viewOnceOpen!")
    }

    if (syncMessage.verified != null && ACI.parseOrNull(syncMessage.verified.destinationAci, syncMessage.verified.destinationAciBinary).isNullOrInvalidServiceId()) {
      return Result.Invalid("[SyncMessage] Invalid ACI in SyncMessage.verified!")
    }

    if (syncMessage.stickerPackOperation.any { it.packId == null }) {
      return Result.Invalid("[SyncMessage] Missing packId in stickerPackOperationList!")
    }

    if (syncMessage.blocked != null && syncMessage.blocked.acis.any { it.isNullOrInvalidAci() } && syncMessage.blocked.acisBinary.any { it.isNullOrInvalidAci() }) {
      return Result.Invalid("[SyncMessage] Invalid ACI in SyncMessage.blocked!")
    }

    if (syncMessage.messageRequestResponse != null && syncMessage.messageRequestResponse.groupId == null && ACI.parseOrNull(syncMessage.messageRequestResponse.threadAci, syncMessage.messageRequestResponse.threadAciBinary).isNullOrInvalidServiceId()) {
      return Result.Invalid("[SyncMessage] Invalid ACI in SyncMessage.messageRequestResponse!")
    }

    if (syncMessage.outgoingPayment != null && syncMessage.outgoingPayment.recipientServiceId.isNullOrInvalidServiceId()) {
      return Result.Invalid("[SyncMessage] Invalid ServiceId in SyncMessage.outgoingPayment!")
    }

    return Result.Valid
  }

  private fun validateReceiptMessage(receiptMessage: ReceiptMessage): Result {
    return if (receiptMessage.type == null) {
      Result.Invalid("[ReceiptMessage] Missing type!")
    } else {
      Result.Valid
    }
  }

  private fun validateTypingMessage(envelope: Envelope, typingMessage: TypingMessage): Result {
    return if (typingMessage.timestamp == null) {
      return Result.Invalid("[TypingMessage] Missing timestamp!")
    } else if (typingMessage.timestamp != envelope.timestamp) {
      Result.Invalid("[TypingMessage] Timestamps don't match! envelope: ${envelope.timestamp}, content: ${typingMessage.timestamp}")
    } else if (typingMessage.action == null) {
      Result.Invalid("[TypingMessage] Missing action!")
    } else {
      Result.Valid
    }
  }

  private fun validateDecryptionErrorMessage(serializedDecryptionErrorMessage: ByteArray): Result {
    return try {
      DecryptionErrorMessage(serializedDecryptionErrorMessage)
      Result.Valid
    } catch (e: Exception) {
      Result.Invalid("[DecryptionErrorMessage] Bad decryption error message!", e)
    }
  }

  private fun validateSenderKeyDistributionMessage(serializedSenderKeyDistributionMessage: ByteArray): Result.Invalid? {
    return try {
      SenderKeyDistributionMessage(serializedSenderKeyDistributionMessage)
      null
    } catch (e: Exception) {
      Result.Invalid("[SenderKeyDistributionMessage] Bad sender key distribution message!", e)
    }
  }

  private fun validatePniSignatureMessage(pniSignatureMessage: PniSignatureMessage): Result? {
    if (pniSignatureMessage.pni.isNullOrInvalidPni()) {
      return Result.Invalid("[PniSignatureMessage] Invalid PNI")
    }

    if (pniSignatureMessage.signature == null) {
      return Result.Invalid("[PniSignatureMessage] Signature is null")
    }

    return null
  }

  private fun validateStoryMessage(storyMessage: StoryMessage): Result {
    if (storyMessage.group != null) {
      validateGroupContextV2(storyMessage.group, "[StoryMessage]")?.let { return it }
    }

    return Result.Valid
  }

  private fun validateEditMessage(editMessage: EditMessage): Result {
    if (editMessage.dataMessage == null) {
      return Result.Invalid("[EditMessage] No data message present")
    }

    if (editMessage.targetSentTimestamp == null) {
      return Result.Invalid("[EditMessage] No targetSentTimestamp specified")
    }

    val dataMessage: DataMessage = editMessage.dataMessage

    if (dataMessage.requiredProtocolVersion != null && dataMessage.requiredProtocolVersion > DataMessage.ProtocolVersion.CURRENT.value) {
      return Result.UnsupportedDataMessage(
        ourVersion = DataMessage.ProtocolVersion.CURRENT.value,
        theirVersion = dataMessage.requiredProtocolVersion
      )
    }

    if (dataMessage.preview.any { it.image != null && it.image.isPresentAndInvalid() }) {
      return Result.Invalid("[EditMessage] Invalid AttachmentPointer on DataMessage.previewList.image!")
    }

    if (dataMessage.bodyRanges.any { Util.anyNotNull(it.mentionAci, it.mentionAciBinary) && ACI.parseOrNull(it.mentionAci, it.mentionAciBinary).isNullOrInvalidServiceId() }) {
      return Result.Invalid("[EditMessage] Invalid UUID on body range!")
    }

    if (dataMessage.attachments.any { it.isNullOrInvalid() }) {
      return Result.Invalid("[EditMessage] Invalid attachments!")
    }

    if (dataMessage.groupV2 != null) {
      validateGroupContextV2(dataMessage.groupV2, "[EditMessage]")?.let { return it }
    }

    return Result.Valid
  }

  private fun AttachmentPointer?.isNullOrInvalid(): Boolean {
    return this == null || (this.cdnId == null && this.cdnKey == null)
  }

  private fun AttachmentPointer?.isPresentAndInvalid(): Boolean {
    return this != null && (this.cdnId == null && this.cdnKey == null)
  }

  private fun String?.isValidServiceId(): Boolean {
    val parsed = ServiceId.parseOrNull(this)
    return parsed != null && parsed.isValid
  }

  private fun String?.isNullOrInvalidServiceId(): Boolean {
    val parsed = ServiceId.parseOrNull(this)
    return parsed == null || parsed.isUnknown
  }

  private fun String?.isNullOrInvalidAci(): Boolean {
    val parsed = ACI.parseOrNull(this)
    return parsed == null || parsed.isUnknown
  }

  private fun ByteString?.isNullOrInvalidAci(): Boolean {
    val parsed = this?.let { ACI.parseOrNull(this) }
    return parsed == null || parsed.isUnknown
  }

  private fun ByteString?.isNullOrInvalidPni(): Boolean {
    val parsed = ServiceId.PNI.parseOrNull(this?.toByteArray())
    return parsed == null || parsed.isUnknown
  }

  private fun ServiceId?.isNullOrInvalidServiceId(): Boolean {
    return this == null || this.isUnknown
  }

  private fun Content?.meetsStoryFlagCriteria(): Boolean {
    return when {
      this == null -> false
      this.senderKeyDistributionMessage != null -> true
      this.storyMessage != null -> true
      this.dataMessage != null && this.dataMessage.storyContext != null && this.dataMessage.groupV2 != null -> true
      this.dataMessage != null && this.dataMessage.delete != null -> true
      else -> false
    }
  }

  private fun validatePlaintextContent(content: Content): Result? {
    val errors: MutableList<String> = mutableListOf()
    if (content.decryptionErrorMessage == null) {
      errors += "Missing DecryptionErrorMessage"
    }
    if (content.dataMessage != null) {
      errors += "Unexpected DataMessage"
    }
    if (content.syncMessage != null) {
      errors += "Unexpected SyncMessage"
    }
    if (content.callMessage != null) {
      errors += "Unexpected CallMessage"
    }
    if (content.nullMessage != null) {
      errors += "Unexpected NullMessage"
    }
    if (content.receiptMessage != null) {
      errors += "Unexpected ReceiptMessage"
    }
    if (content.typingMessage != null) {
      errors += "Unexpected TypingMessage"
    }
    if (content.senderKeyDistributionMessage != null) {
      errors += "Unexpected SenderKeyDistributionMessage"
    }
    if (content.storyMessage != null) {
      errors += "Unexpected StoryMessage"
    }
    if (content.pniSignatureMessage != null) {
      errors += "Unexpected PniSignatureMessage"
    }
    if (content.editMessage != null) {
      errors += "Unexpected EditMessage"
    }

    return if (errors.isNotEmpty()) {
      Result.Invalid("Invalid PLAINTEXT_CONTENT! Errors: $errors")
    } else {
      null
    }
  }

  private fun validateGroupContextV2(groupContext: GroupContextV2, prefix: String): Result.Invalid? {
    return if (groupContext.masterKey == null) {
      Result.Invalid("$prefix Missing GV2 master key!")
    } else if (groupContext.revision == null) {
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

    /** The [DataMessage.requiredProtocolVersion] is newer than the one we support. */
    class UnsupportedDataMessage(val ourVersion: Int, val theirVersion: Int?) : Result()

    /** The contents of the proto do not match our expectations, e.g. invalid UUIDs, missing required fields, etc.  */
    class Invalid(val reason: String, val throwable: Throwable = Throwable()) : Result()
  }
}
