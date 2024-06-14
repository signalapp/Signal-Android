/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api.messages

import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.whispersystems.signalservice.api.messages.shared.SharedContact
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.OptionalUtil.asOptional
import org.whispersystems.signalservice.api.util.OptionalUtil.emptyIfStringEmpty
import org.whispersystems.signalservice.internal.push.BodyRange
import java.util.LinkedList
import java.util.Optional
import org.whispersystems.signalservice.internal.push.DataMessage.Payment as PaymentProto
import org.whispersystems.signalservice.internal.push.DataMessage.Quote as QuoteProto

/**
 * Represents a decrypted Signal Service data message.
 *
 * @param timestamp The sent timestamp.
 * @param groupContext The group information (or null if none).
 * @param attachments The attachments (or null if none).
 * @param body The message contents.
 * @param isEndSession Flag indicating whether this message should close a session.
 * @param expiresInSeconds Number of seconds in which the message should disappear after being seen.
 */
class SignalServiceDataMessage private constructor(
  val timestamp: Long,
  val groupContext: Optional<SignalServiceGroupV2>,
  val attachments: Optional<List<SignalServiceAttachment>>,
  val body: Optional<String>,
  val isEndSession: Boolean,
  val expiresInSeconds: Int,
  val isExpirationUpdate: Boolean,
  val profileKey: Optional<ByteArray>,
  val isProfileKeyUpdate: Boolean,
  val quote: Optional<Quote>,
  val sharedContacts: Optional<List<SharedContact>>,
  val previews: Optional<List<SignalServicePreview>>,
  val mentions: Optional<List<Mention>>,
  val sticker: Optional<Sticker>,
  val isViewOnce: Boolean,
  val reaction: Optional<Reaction>,
  val remoteDelete: Optional<RemoteDelete>,
  val groupCallUpdate: Optional<GroupCallUpdate>,
  val payment: Optional<Payment>,
  val storyContext: Optional<StoryContext>,
  val giftBadge: Optional<GiftBadge>,
  val bodyRanges: Optional<List<BodyRange>>
) {
  val isActivatePaymentsRequest: Boolean = payment.map { it.isActivationRequest }.orElse(false)
  val isPaymentsActivated: Boolean = payment.map { it.isActivation }.orElse(false)

  val groupId: Optional<ByteArray> = groupContext.map { GroupSecretParams.deriveFromMasterKey(it.masterKey).publicParams.groupIdentifier.serialize() }
  val isGroupV2Message: Boolean = groupContext.isPresent

  /** Contains some user data that affects the conversation  */
  private val hasRenderableContent: Boolean =
    this.attachments.isPresent ||
      this.body.isPresent ||
      this.quote.isPresent ||
      this.sharedContacts.isPresent ||
      this.previews.isPresent ||
      this.mentions.isPresent ||
      this.sticker.isPresent ||
      this.reaction.isPresent ||
      this.remoteDelete.isPresent

  val isGroupV2Update: Boolean = groupContext.isPresent && groupContext.get().hasSignedGroupChange() && !hasRenderableContent
  val isEmptyGroupV2Message: Boolean = isGroupV2Message && !isGroupV2Update && !hasRenderableContent

  class Builder {
    private var timestamp: Long = 0
    private var groupV2: SignalServiceGroupV2? = null
    private val attachments: MutableList<SignalServiceAttachment> = LinkedList<SignalServiceAttachment>()
    private var body: String? = null
    private var endSession: Boolean = false
    private var expiresInSeconds: Int = 0
    private var expirationUpdate: Boolean = false
    private var profileKey: ByteArray? = null
    private var profileKeyUpdate: Boolean = false
    private var quote: Quote? = null
    private val sharedContacts: MutableList<SharedContact> = LinkedList<SharedContact>()
    private val previews: MutableList<SignalServicePreview> = LinkedList<SignalServicePreview>()
    private val mentions: MutableList<Mention> = LinkedList<Mention>()
    private var sticker: Sticker? = null
    private var viewOnce: Boolean = false
    private var reaction: Reaction? = null
    private var remoteDelete: RemoteDelete? = null
    private var groupCallUpdate: GroupCallUpdate? = null
    private var payment: Payment? = null
    private var storyContext: StoryContext? = null
    private var giftBadge: GiftBadge? = null
    private var bodyRanges: MutableList<BodyRange> = LinkedList<BodyRange>()

    fun withTimestamp(timestamp: Long): Builder {
      this.timestamp = timestamp
      return this
    }

    fun asGroupMessage(group: SignalServiceGroupV2?): Builder {
      groupV2 = group
      return this
    }

    fun withAttachments(attachments: List<SignalServiceAttachment>?): Builder {
      attachments?.let { this.attachments.addAll(attachments) }
      return this
    }

    fun withBody(body: String?): Builder {
      this.body = body
      return this
    }

    @JvmOverloads
    fun asEndSessionMessage(endSession: Boolean = true): Builder {
      this.endSession = endSession
      return this
    }

    @JvmOverloads
    fun asExpirationUpdate(expirationUpdate: Boolean = true): Builder {
      this.expirationUpdate = expirationUpdate
      return this
    }

    fun withExpiration(expiresInSeconds: Int): Builder {
      this.expiresInSeconds = expiresInSeconds
      return this
    }

    fun withProfileKey(profileKey: ByteArray?): Builder {
      this.profileKey = profileKey
      return this
    }

    fun asProfileKeyUpdate(profileKeyUpdate: Boolean): Builder {
      this.profileKeyUpdate = profileKeyUpdate
      return this
    }

    fun withQuote(quote: Quote?): Builder {
      this.quote = quote
      return this
    }

    fun withSharedContact(contact: SharedContact?): Builder {
      contact?.let { sharedContacts.add(contact) }
      return this
    }

    fun withSharedContacts(contacts: List<SharedContact>?): Builder {
      contacts?.let { sharedContacts.addAll(contacts) }
      return this
    }

    fun withPreviews(previews: List<SignalServicePreview>?): Builder {
      previews?.let { this.previews.addAll(previews) }
      return this
    }

    fun withMentions(mentions: List<Mention>?): Builder {
      mentions?.let { this.mentions.addAll(mentions) }
      return this
    }

    fun withSticker(sticker: Sticker?): Builder {
      this.sticker = sticker
      return this
    }

    fun withViewOnce(viewOnce: Boolean): Builder {
      this.viewOnce = viewOnce
      return this
    }

    fun withReaction(reaction: Reaction?): Builder {
      this.reaction = reaction
      return this
    }

    fun withRemoteDelete(remoteDelete: RemoteDelete?): Builder {
      this.remoteDelete = remoteDelete
      return this
    }

    fun withGroupCallUpdate(groupCallUpdate: GroupCallUpdate?): Builder {
      this.groupCallUpdate = groupCallUpdate
      return this
    }

    fun withPayment(payment: Payment?): Builder {
      this.payment = payment
      return this
    }

    fun withStoryContext(storyContext: StoryContext?): Builder {
      this.storyContext = storyContext
      return this
    }

    fun withGiftBadge(giftBadge: GiftBadge?): Builder {
      this.giftBadge = giftBadge
      return this
    }

    fun withBodyRanges(bodyRanges: List<BodyRange>?): Builder {
      bodyRanges?.let { this.bodyRanges.addAll(bodyRanges) }
      return this
    }

    fun build(): SignalServiceDataMessage {
      if (timestamp == 0L) {
        timestamp = System.currentTimeMillis()
      }

      return SignalServiceDataMessage(
        timestamp = timestamp,
        groupContext = groupV2.asOptional(),
        attachments = attachments.asOptional(),
        body = body.emptyIfStringEmpty(),
        isEndSession = endSession,
        expiresInSeconds = expiresInSeconds,
        isExpirationUpdate = expirationUpdate,
        profileKey = profileKey.asOptional(),
        isProfileKeyUpdate = profileKeyUpdate,
        quote = quote.asOptional(),
        sharedContacts = sharedContacts.asOptional(),
        previews = previews.asOptional(),
        mentions = mentions.asOptional(),
        sticker = sticker.asOptional(),
        isViewOnce = viewOnce,
        reaction = reaction.asOptional(),
        remoteDelete = remoteDelete.asOptional(),
        groupCallUpdate = groupCallUpdate.asOptional(),
        payment = payment.asOptional(),
        storyContext = storyContext.asOptional(),
        giftBadge = giftBadge.asOptional(),
        bodyRanges = bodyRanges.asOptional()
      )
    }
  }

  data class Quote(
    val id: Long,
    val author: ServiceId?,
    val text: String,
    val attachments: List<QuotedAttachment>?,
    val mentions: List<Mention>?,
    val type: Type,
    val bodyRanges: List<BodyRange>?
  ) {
    enum class Type(val protoType: QuoteProto.Type) {
      NORMAL(QuoteProto.Type.NORMAL),
      GIFT_BADGE(QuoteProto.Type.GIFT_BADGE);

      companion object {
        @JvmStatic
        fun fromProto(protoType: QuoteProto.Type): Type {
          return values().firstOrNull { it.protoType == protoType } ?: NORMAL
        }
      }
    }

    data class QuotedAttachment(val contentType: String, val fileName: String?, val thumbnail: SignalServiceAttachment?)
  }
  class Sticker(val packId: ByteArray?, val packKey: ByteArray?, val stickerId: Int, val emoji: String?, val attachment: SignalServiceAttachment?)
  data class Reaction(val emoji: String, val isRemove: Boolean, val targetAuthor: ServiceId, val targetSentTimestamp: Long)
  data class RemoteDelete(val targetSentTimestamp: Long)
  data class Mention(val serviceId: ServiceId, val start: Int, val length: Int)
  data class GroupCallUpdate(val eraId: String?)
  class PaymentNotification(val receipt: ByteArray, val note: String)
  data class PaymentActivation(val type: PaymentProto.Activation.Type)
  class Payment(paymentNotification: PaymentNotification?, paymentActivation: PaymentActivation?) {
    val paymentNotification: Optional<PaymentNotification> = Optional.ofNullable(paymentNotification)
    val paymentActivation: Optional<PaymentActivation> = Optional.ofNullable(paymentActivation)
    val isActivationRequest: Boolean = paymentActivation != null && paymentActivation.type == PaymentProto.Activation.Type.REQUEST
    val isActivation: Boolean = paymentActivation != null && paymentActivation.type == PaymentProto.Activation.Type.ACTIVATED
  }
  data class StoryContext(val authorServiceId: ServiceId, val sentTimestamp: Long)
  data class GiftBadge(val receiptCredentialPresentation: ReceiptCredentialPresentation)

  companion object {
    @JvmStatic
    fun newBuilder(): Builder {
      return Builder()
    }
  }
}
