package org.thoughtcrime.securesms.messages

import ProtoUtil.isNotEmpty
import com.squareup.wire.Message
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.orNull
import org.signal.libsignal.protocol.message.DecryptionErrorMessage
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.InvalidMessageStructureException
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.payments.Money
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.AttachmentPointerUtil
import org.whispersystems.signalservice.internal.push.AttachmentPointer
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.DataMessage.Payment
import org.whispersystems.signalservice.internal.push.GroupContextV2
import org.whispersystems.signalservice.internal.push.StoryMessage
import org.whispersystems.signalservice.internal.push.SyncMessage
import org.whispersystems.signalservice.internal.push.SyncMessage.Sent
import org.whispersystems.signalservice.internal.push.TypingMessage
import org.whispersystems.signalservice.internal.util.Util
import java.util.Optional
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object SignalServiceProtoUtil {

  @JvmStatic
  val emptyGroupChange: DecryptedGroupChange by lazy { DecryptedGroupChange() }

  /** Contains some user data that affects the conversation  */
  val DataMessage.hasRenderableContent: Boolean
    get() {
      return attachments.isNotEmpty() ||
        body != null ||
        quote != null ||
        contact.isNotEmpty() ||
        preview.isNotEmpty() ||
        bodyRanges.isNotEmpty() ||
        sticker != null ||
        reaction != null ||
        hasRemoteDelete
    }

  val DataMessage.hasDisallowedAnnouncementOnlyContent: Boolean
    get() {
      return body != null ||
        attachments.isNotEmpty() ||
        quote != null ||
        preview.isNotEmpty() ||
        bodyRanges.isNotEmpty() ||
        sticker != null
    }

  val DataMessage.isExpirationUpdate: Boolean
    get() = flags != null && flags!! and DataMessage.Flags.EXPIRATION_TIMER_UPDATE.value != 0

  val DataMessage.hasRemoteDelete: Boolean
    get() = delete != null && delete!!.targetSentTimestamp != null

  val DataMessage.isGroupV2Update: Boolean
    get() = !hasRenderableContent && hasSignedGroupChange

  val DataMessage?.hasGroupContext: Boolean
    get() = this?.groupV2?.masterKey.isNotEmpty()

  val DataMessage.hasSignedGroupChange: Boolean
    get() = hasGroupContext && groupV2!!.hasSignedGroupChange

  val DataMessage.isMediaMessage: Boolean
    get() = attachments.isNotEmpty() || quote != null || contact.isNotEmpty() || sticker != null || bodyRanges.isNotEmpty() || preview.isNotEmpty()

  val DataMessage.isEndSession: Boolean
    get() = flags != null && flags!! and DataMessage.Flags.END_SESSION.value != 0

  val DataMessage.isStoryReaction: Boolean
    get() = reaction != null && storyContext != null

  val DataMessage.isPaymentActivationRequest: Boolean
    get() = payment?.activation?.type == Payment.Activation.Type.REQUEST

  val DataMessage.isPaymentActivated: Boolean
    get() = payment?.activation?.type == Payment.Activation.Type.ACTIVATED

  val DataMessage.isInvalid: Boolean
    get() {
      if (isViewOnce == true) {
        val contentType = attachments[0].contentType?.lowercase()
        return attachments.size != 1 || !MediaUtil.isImageOrVideoType(contentType)
      }
      return false
    }

  val DataMessage.isEmptyGroupV2Message: Boolean
    get() = hasGroupContext && !isGroupV2Update && !hasRenderableContent

  val DataMessage.expireTimerDuration: Duration
    get() = (expireTimer ?: 0).seconds

  val GroupContextV2.hasSignedGroupChange: Boolean
    get() = groupChange.isNotEmpty()

  val GroupContextV2.signedGroupChange: ByteArray
    get() = groupChange!!.toByteArray()

  val GroupContextV2.groupMasterKey: GroupMasterKey
    get() = GroupMasterKey(masterKey!!.toByteArray())

  val GroupContextV2?.isValid: Boolean
    get() = this?.masterKey.isNotEmpty()

  val GroupContextV2.groupId: GroupId.V2?
    get() = if (isValid) GroupId.v2(groupMasterKey) else null

  val StoryMessage.type: StoryType
    get() {
      return if (allowsReplies == true) {
        if (textAttachment != null) {
          StoryType.TEXT_STORY_WITH_REPLIES
        } else {
          StoryType.STORY_WITH_REPLIES
        }
      } else {
        if (textAttachment != null) {
          StoryType.TEXT_STORY_WITHOUT_REPLIES
        } else {
          StoryType.STORY_WITHOUT_REPLIES
        }
      }
    }

  fun Sent.isUnidentified(serviceId: ServiceId?): Boolean {
    return serviceId != null && unidentifiedStatus.firstOrNull { ServiceId.parseOrNull(it.destinationServiceId) == serviceId }?.unidentified ?: false
  }

  val Sent.serviceIdsToUnidentifiedStatus: Map<ServiceId, Boolean>
    get() {
      return unidentifiedStatus
        .mapNotNull { status ->
          val serviceId = ServiceId.parseOrNull(status.destinationServiceId)
          if (serviceId != null) {
            serviceId to (status.unidentified ?: false)
          } else {
            null
          }
        }
        .toMap()
    }

  val TypingMessage.hasStarted: Boolean
    get() = action == TypingMessage.Action.STARTED

  fun ByteString.toDecryptionErrorMessage(metadata: EnvelopeMetadata): DecryptionErrorMessage {
    try {
      return DecryptionErrorMessage(toByteArray())
    } catch (e: InvalidMessageStructureException) {
      throw InvalidMessageStructureException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    }
  }

  fun List<AttachmentPointer>.toPointersWithinLimit(): List<Attachment> {
    return mapNotNull { it.toPointer() }.take(RemoteConfig.maxAttachmentCount)
  }

  fun AttachmentPointer.toPointer(stickerLocator: StickerLocator? = null): Attachment? {
    return try {
      val pointer = PointerAttachment.forPointer(Optional.of(toSignalServiceAttachmentPointer()), stickerLocator).orNull()
      if (pointer?.cdn != Cdn.S3) {
        pointer
      } else {
        null
      }
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

  fun SyncMessage.Builder.pad(length: Int = 512): SyncMessage.Builder {
    padding(Util.getRandomLengthSecretBytes(length).toByteString())
    return this
  }

  @Suppress("UNCHECKED_CAST")
  inline fun <reified MessageType : Message<MessageType, BuilderType>, BuilderType : Message.Builder<MessageType, BuilderType>> Message.Builder<MessageType, BuilderType>.buildWith(block: BuilderType.() -> Unit): MessageType {
    block(this as BuilderType)
    return build()
  }
}
