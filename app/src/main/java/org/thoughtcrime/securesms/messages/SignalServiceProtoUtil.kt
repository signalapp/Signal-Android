package org.thoughtcrime.securesms.messages

import com.google.protobuf.ByteString
import org.signal.core.util.orNull
import org.signal.libsignal.protocol.message.DecryptionErrorMessage
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toPointer
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.InvalidMessageStructureException
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.payments.Money
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.AttachmentPointerUtil
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage.Payment
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContextV2
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.StoryMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.Sent
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.TypingMessage
import java.util.Optional

private val ByteString.isNotEmpty: Boolean
  get() = !this.isEmpty

object SignalServiceProtoUtil {

  /** Contains some user data that affects the conversation  */
  val DataMessage.hasRenderableContent: Boolean
    get() {
      return attachmentsList.isNotEmpty() ||
        hasBody() ||
        hasQuote() ||
        contactList.isNotEmpty() ||
        previewList.isNotEmpty() ||
        bodyRangesList.isNotEmpty() ||
        hasSticker() ||
        hasReaction() ||
        hasRemoteDelete
    }

  val DataMessage.hasDisallowedAnnouncementOnlyContent: Boolean
    get() {
      return hasBody() ||
        attachmentsList.isNotEmpty() ||
        hasQuote() ||
        previewList.isNotEmpty() ||
        bodyRangesList.isNotEmpty() ||
        hasSticker()
    }

  val DataMessage.isExpirationUpdate: Boolean
    get() = flags and DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE != 0

  val DataMessage.hasRemoteDelete: Boolean
    get() = hasDelete() && delete.hasTargetSentTimestamp()

  val DataMessage.isGroupV2Update: Boolean
    get() = !hasRenderableContent && hasSignedGroupChange

  val DataMessage.hasGroupContext: Boolean
    get() = hasGroupV2() && groupV2.hasMasterKey() && groupV2.masterKey.isNotEmpty

  val DataMessage.hasSignedGroupChange: Boolean
    get() = hasGroupContext && groupV2.hasSignedGroupChange

  val DataMessage.isMediaMessage: Boolean
    get() = attachmentsList.isNotEmpty() || hasQuote() || contactList.isNotEmpty() || hasSticker() || bodyRangesList.isNotEmpty() || previewList.isNotEmpty()

  val DataMessage.isEndSession: Boolean
    get() = flags and DataMessage.Flags.END_SESSION_VALUE != 0

  val DataMessage.isStoryReaction: Boolean
    get() = hasReaction() && hasStoryContext()

  val DataMessage.isPaymentActivationRequest: Boolean
    get() = hasPayment() && payment.hasActivation() && payment.activation.type == Payment.Activation.Type.REQUEST

  val DataMessage.isPaymentActivated: Boolean
    get() = hasPayment() && payment.hasActivation() && payment.activation.type == Payment.Activation.Type.ACTIVATED

  val DataMessage.isInvalid: Boolean
    get() {
      if (isViewOnce) {
        val contentType = attachmentsList[0].contentType.lowercase()
        return attachmentsList.size != 1 || !MediaUtil.isImageOrVideoType(contentType)
      }
      return false
    }

  val DataMessage.isEmptyGroupV2Message: Boolean
    get() = hasGroupContext && !isGroupV2Update && !hasRenderableContent

  val GroupContextV2.hasSignedGroupChange: Boolean
    get() = hasGroupChange() && groupChange.isNotEmpty

  val GroupContextV2.signedGroupChange: ByteArray
    get() = groupChange.toByteArray()

  val GroupContextV2.groupMasterKey: GroupMasterKey
    get() = GroupMasterKey(masterKey.toByteArray())

  val GroupContextV2?.isValid: Boolean
    get() = this != null && masterKey.isNotEmpty

  val GroupContextV2.groupId: GroupId.V2?
    get() = if (isValid) GroupId.v2(groupMasterKey) else null

  val StoryMessage.type: StoryType
    get() {
      return if (allowsReplies) {
        if (hasTextAttachment()) {
          StoryType.TEXT_STORY_WITH_REPLIES
        } else {
          StoryType.STORY_WITH_REPLIES
        }
      } else {
        if (hasTextAttachment()) {
          StoryType.TEXT_STORY_WITHOUT_REPLIES
        } else {
          StoryType.STORY_WITHOUT_REPLIES
        }
      }
    }

  fun Sent.isUnidentified(serviceId: ServiceId?): Boolean {
    return serviceId != null && unidentifiedStatusList.firstOrNull { ServiceId.parseOrNull(it.destinationUuid) == serviceId }?.unidentified ?: false
  }

  val Sent.serviceIdsToUnidentifiedStatus: Map<ServiceId, Boolean>
    get() {
      return unidentifiedStatusList
        .mapNotNull { status ->
          val serviceId = ServiceId.parseOrNull(status.destinationUuid)
          if (serviceId != null) {
            serviceId to status.unidentified
          } else {
            null
          }
        }
        .toMap()
    }

  val TypingMessage.hasStarted: Boolean
    get() = hasAction() && action == TypingMessage.Action.STARTED

  fun ByteString.toDecryptionErrorMessage(metadata: EnvelopeMetadata): DecryptionErrorMessage {
    try {
      return DecryptionErrorMessage(toByteArray())
    } catch (e: InvalidMessageStructureException) {
      throw InvalidMessageStructureException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    }
  }

  fun List<AttachmentPointer>.toPointers(): List<Attachment> {
    return mapNotNull { it.toPointer() }
  }

  fun AttachmentPointer.toPointer(stickerLocator: StickerLocator? = null): Attachment? {
    return try {
      PointerAttachment.forPointer(Optional.of(toSignalServiceAttachmentPointer()), stickerLocator).orNull()
    } catch (e: InvalidMessageStructureException) {
      null
    }
  }

  fun AttachmentPointer.toSignalServiceAttachmentPointer(): SignalServiceAttachmentPointer {
    return AttachmentPointerUtil.createSignalAttachmentPointer(this)
  }

  fun Long.toMobileCoinMoney(): Money {
    return Money.picoMobileCoin(this)
  }
}
