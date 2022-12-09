package org.thoughtcrime.securesms.mms

import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch
import org.thoughtcrime.securesms.database.documents.NetworkFailure
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.GroupV2UpdateMessageUtil

/**
 * Outgoing media message for all outgoing media messages (push/mms, group updates, expiration updates, payments, etc.)
 */
data class OutgoingMediaMessage(
  val recipient: Recipient,
  val sentTimeMillis: Long,
  val body: String = "",
  val distributionType: Int = ThreadTable.DistributionTypes.DEFAULT,
  val subscriptionId: Int = -1,
  val expiresIn: Long = 0L,
  val isViewOnce: Boolean = false,
  val outgoingQuote: QuoteModel? = null,
  val storyType: StoryType = StoryType.NONE,
  val parentStoryId: ParentStoryId? = null,
  val isStoryReaction: Boolean = false,
  val giftBadge: GiftBadge? = null,
  val isSecure: Boolean = false,
  val attachments: List<Attachment> = emptyList(),
  val sharedContacts: List<Contact> = emptyList(),
  val linkPreviews: List<LinkPreview> = emptyList(),
  val mentions: List<Mention> = emptyList(),
  val isGroup: Boolean = false,
  val isGroupUpdate: Boolean = false,
  val messageGroupContext: MessageGroupContext? = null,
  val isExpirationUpdate: Boolean = false,
  val isPaymentsNotification: Boolean = false,
  val isRequestToActivatePayments: Boolean = false,
  val isPaymentsActivated: Boolean = false,
  val isUrgent: Boolean = true,
  val networkFailures: Set<NetworkFailure> = emptySet(),
  val identityKeyMismatches: Set<IdentityKeyMismatch> = emptySet(),
) {

  val isV2Group: Boolean = messageGroupContext != null && GroupV2UpdateMessageUtil.isGroupV2(messageGroupContext)
  val isJustAGroupLeave: Boolean = messageGroupContext != null && GroupV2UpdateMessageUtil.isJustAGroupLeave(messageGroupContext)

  /**
   * Smaller constructor for calling from Java and legacy code using the original interface.
   */
  constructor(
    recipient: Recipient,
    body: String? = "",
    attachments: List<Attachment> = emptyList(),
    timestamp: Long,
    subscriptionId: Int = -1,
    expiresIn: Long = 0L,
    viewOnce: Boolean = false,
    distributionType: Int = ThreadTable.DistributionTypes.DEFAULT,
    storyType: StoryType = StoryType.NONE,
    parentStoryId: ParentStoryId? = null,
    isStoryReaction: Boolean = false,
    quote: QuoteModel? = null,
    contacts: List<Contact> = emptyList(),
    previews: List<LinkPreview> = emptyList(),
    mentions: List<Mention> = emptyList(),
    networkFailures: Set<NetworkFailure> = emptySet(),
    mismatches: Set<IdentityKeyMismatch> = emptySet(),
    giftBadge: GiftBadge? = null,
    isSecure: Boolean = false
  ) : this(
    recipient = recipient,
    body = body ?: "",
    attachments = attachments,
    sentTimeMillis = timestamp,
    subscriptionId = subscriptionId,
    expiresIn = expiresIn,
    isViewOnce = viewOnce,
    distributionType = distributionType,
    storyType = storyType,
    parentStoryId = parentStoryId,
    isStoryReaction = isStoryReaction,
    outgoingQuote = quote,
    sharedContacts = contacts,
    linkPreviews = previews,
    mentions = mentions,
    networkFailures = networkFailures,
    identityKeyMismatches = mismatches,
    giftBadge = giftBadge,
    isSecure = isSecure
  )

  /**
   * Allow construction of attachments/body via a [SlideDeck] instead of passing in attachments list.
   */
  constructor(
    recipient: Recipient,
    slideDeck: SlideDeck,
    body: String? = "",
    timestamp: Long,
    subscriptionId: Int = -1,
    expiresIn: Long = 0L,
    viewOnce: Boolean = false,
    storyType: StoryType = StoryType.NONE,
    linkPreviews: List<LinkPreview> = emptyList(),
    mentions: List<Mention> = emptyList(),
    isSecure: Boolean = false
  ) : this(
    recipient = recipient,
    body = buildMessage(slideDeck, body ?: ""),
    attachments = slideDeck.asAttachments(),
    sentTimeMillis = timestamp,
    subscriptionId = subscriptionId,
    expiresIn = expiresIn,
    isViewOnce = viewOnce,
    storyType = storyType,
    linkPreviews = linkPreviews,
    mentions = mentions,
    isSecure = isSecure
  )

  fun withExpiry(expiresIn: Long): OutgoingMediaMessage {
    return copy(expiresIn = expiresIn)
  }

  fun stripAttachments(): OutgoingMediaMessage {
    return copy(attachments = emptyList())
  }

  fun makeSecure(): OutgoingMediaMessage {
    return copy(isSecure = true)
  }

  fun requireGroupV1Properties(): MessageGroupContext.GroupV1Properties {
    return messageGroupContext!!.requireGroupV1Properties()
  }

  fun requireGroupV2Properties(): MessageGroupContext.GroupV2Properties {
    return messageGroupContext!!.requireGroupV2Properties()
  }

  companion object {
    /**
     * Helper for creating a group update message when a state change occurs and needs to be sent to others.
     */
    @JvmStatic
    fun groupUpdateMessage(recipient: Recipient, group: DecryptedGroupV2Context, sentTimeMillis: Long): OutgoingMediaMessage {
      val groupContext = MessageGroupContext(group)

      return OutgoingMediaMessage(
        recipient = recipient,
        body = groupContext.encodedGroupContext,
        sentTimeMillis = sentTimeMillis,
        messageGroupContext = groupContext,
        isGroup = true,
        isGroupUpdate = true,
        isSecure = true
      )
    }

    /**
     * Helper for creating a group update message when a state change occurs and needs to be sent to others.
     */
    @JvmStatic
    fun groupUpdateMessage(
      recipient: Recipient,
      groupContext: MessageGroupContext,
      avatar: List<Attachment> = emptyList(),
      sentTimeMillis: Long,
      expiresIn: Long = 0L,
      viewOnce: Boolean = false,
      quote: QuoteModel? = null,
      contacts: List<Contact> = emptyList(),
      previews: List<LinkPreview> = emptyList(),
      mentions: List<Mention> = emptyList()
    ): OutgoingMediaMessage {
      return OutgoingMediaMessage(
        recipient = recipient,
        body = groupContext.encodedGroupContext,
        isGroup = true,
        isGroupUpdate = true,
        messageGroupContext = groupContext,
        attachments = avatar,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isViewOnce = viewOnce,
        outgoingQuote = quote,
        sharedContacts = contacts,
        linkPreviews = previews,
        mentions = mentions,
        isSecure = true
      )
    }

    /**
     * Helper for creating a text story message.
     */
    @JvmStatic
    fun textStoryMessage(
      recipient: Recipient,
      body: String,
      sentTimeMillis: Long,
      storyType: StoryType,
      linkPreviews: List<LinkPreview>
    ): OutgoingMediaMessage {
      return OutgoingMediaMessage(
        recipient = recipient,
        body = body,
        sentTimeMillis = sentTimeMillis,
        storyType = storyType,
        linkPreviews = linkPreviews,
        isSecure = true
      )
    }

    /**
     * Specialized message sent to request someone activate payments.
     */
    @JvmStatic
    fun requestToActivatePaymentsMessage(recipient: Recipient, sentTimeMillis: Long, expiresIn: Long): OutgoingMediaMessage {
      return OutgoingMediaMessage(
        recipient = recipient,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isRequestToActivatePayments = true,
        isUrgent = false,
        isSecure = true
      )
    }

    /**
     * Specialized message sent to indicate you activated payments. Intended to only
     * be sent to those that sent requests prior to activation.
     */
    @JvmStatic
    fun paymentsActivatedMessage(recipient: Recipient, sentTimeMillis: Long, expiresIn: Long): OutgoingMediaMessage {
      return OutgoingMediaMessage(
        recipient = recipient,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isPaymentsActivated = true,
        isUrgent = false,
        isSecure = true
      )
    }

    /**
     * Type of message sent when sending a payment to another Signal contact.
     */
    @JvmStatic
    fun paymentNotificationMessage(recipient: Recipient, paymentUuid: String, sentTimeMillis: Long, expiresIn: Long): OutgoingMediaMessage {
      return OutgoingMediaMessage(
        recipient = recipient,
        body = paymentUuid,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isPaymentsNotification = true,
        isSecure = true
      )
    }

    /**
     * Helper for creating expiration update messages.
     */
    @JvmStatic
    fun expirationUpdateMessage(recipient: Recipient, sentTimeMillis: Long, expiresIn: Long): OutgoingMediaMessage {
      return OutgoingMediaMessage(
        recipient = recipient,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isExpirationUpdate = true,
        isUrgent = false,
        isSecure = true
      )
    }

    @JvmStatic
    fun buildMessage(slideDeck: SlideDeck, message: String): String {
      return if (message.isNotEmpty() && slideDeck.body.isNotEmpty()) {
        "${slideDeck.body}\n\n$message"
      } else if (message.isNotEmpty()) {
        message
      } else {
        slideDeck.body
      }
    }
  }
}
