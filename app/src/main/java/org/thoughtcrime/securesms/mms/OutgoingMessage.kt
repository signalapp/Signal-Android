package org.thoughtcrime.securesms.mms

import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch
import org.thoughtcrime.securesms.database.documents.NetworkFailure
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.GV2UpdateDescription
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.GroupV2UpdateMessageUtil

/**
 * Represents all the data needed for an outgoing message.
 */
data class OutgoingMessage(
  val threadRecipient: Recipient,
  val sentTimeMillis: Long,
  val body: String = "",
  val distributionType: Int = ThreadTable.DistributionTypes.DEFAULT,
  val expiresIn: Long = 0L,
  val expireTimerVersion: Int = threadRecipient.expireTimerVersion,
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
  val bodyRanges: BodyRangeList? = null,
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
  val isEndSession: Boolean = false,
  val isIdentityVerified: Boolean = false,
  val isIdentityDefault: Boolean = false,
  val scheduledDate: Long = -1,
  val messageToEdit: Long = 0,
  val isReportSpam: Boolean = false,
  val isMessageRequestAccept: Boolean = false,
  val isBlocked: Boolean = false,
  val isUnblocked: Boolean = false,
  val messageExtras: MessageExtras? = null
) {

  val isV2Group: Boolean = messageGroupContext != null && GroupV2UpdateMessageUtil.isGroupV2(messageGroupContext)
  val isJustAGroupLeave: Boolean = messageGroupContext != null && GroupV2UpdateMessageUtil.isJustAGroupLeave(messageGroupContext)
  val isMessageEdit: Boolean = messageToEdit != 0L

  /**
   * Smaller constructor for calling from Java and legacy code using the original interface.
   */
  constructor(
    recipient: Recipient,
    body: String? = "",
    attachments: List<Attachment> = emptyList(),
    timestamp: Long,
    expiresIn: Long = 0L,
    expireTimerVersion: Int = 1,
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
    isSecure: Boolean = false,
    bodyRanges: BodyRangeList? = null,
    scheduledDate: Long = -1,
    messageToEdit: Long = 0
  ) : this(
    threadRecipient = recipient,
    body = body ?: "",
    attachments = attachments,
    sentTimeMillis = timestamp,
    expiresIn = expiresIn,
    expireTimerVersion = expireTimerVersion,
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
    isSecure = isSecure,
    bodyRanges = bodyRanges,
    scheduledDate = scheduledDate,
    messageToEdit = messageToEdit
  )

  /**
   * Allow construction of attachments/body via a [SlideDeck] instead of passing in attachments list.
   */
  constructor(
    recipient: Recipient,
    slideDeck: SlideDeck,
    body: String? = "",
    timestamp: Long,
    expiresIn: Long = 0L,
    expiresTimerVersion: Int = 1,
    viewOnce: Boolean = false,
    storyType: StoryType = StoryType.NONE,
    linkPreviews: List<LinkPreview> = emptyList(),
    mentions: List<Mention> = emptyList(),
    isSecure: Boolean = false,
    bodyRanges: BodyRangeList? = null,
    contacts: List<Contact> = emptyList()
  ) : this(
    threadRecipient = recipient,
    body = buildMessage(slideDeck, body ?: ""),
    attachments = slideDeck.asAttachments(),
    sentTimeMillis = timestamp,
    expiresIn = expiresIn,
    expireTimerVersion = expiresTimerVersion,
    isViewOnce = viewOnce,
    storyType = storyType,
    linkPreviews = linkPreviews,
    mentions = mentions,
    isSecure = isSecure,
    bodyRanges = bodyRanges,
    sharedContacts = contacts
  )

  val subscriptionId = -1

  fun withExpiry(expiresIn: Long, expireTimerVersion: Int): OutgoingMessage {
    return copy(expiresIn = expiresIn, expireTimerVersion = expireTimerVersion)
  }

  fun stripAttachments(): OutgoingMessage {
    return copy(attachments = emptyList())
  }

  fun makeSecure(): OutgoingMessage {
    return copy(isSecure = true)
  }

  fun requireGroupV1Properties(): MessageGroupContext.GroupV1Properties {
    return messageGroupContext!!.requireGroupV1Properties()
  }

  fun requireGroupV2Properties(): MessageGroupContext.GroupV2Properties {
    return messageGroupContext!!.requireGroupV2Properties()
  }

  fun sendAt(scheduledDate: Long): OutgoingMessage {
    return copy(scheduledDate = scheduledDate)
  }

  companion object {

    /**
     * A literal, insecure SMS message.
     */
    @JvmStatic
    fun sms(threadRecipient: Recipient, body: String): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = System.currentTimeMillis(),
        body = body,
        isSecure = false
      )
    }

    /**
     * A secure message that only contains text.
     */
    @JvmStatic
    fun text(
      threadRecipient: Recipient,
      body: String,
      expiresIn: Long,
      sentTimeMillis: Long = System.currentTimeMillis(),
      bodyRanges: BodyRangeList? = null
    ): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        body = body,
        expiresIn = expiresIn,
        isUrgent = true,
        isSecure = true,
        bodyRanges = bodyRanges
      )
    }

    /**
     * Edit a secure message that only contains text.
     */
    @JvmStatic
    fun editText(
      recipient: Recipient,
      body: String,
      sentTimeMillis: Long,
      bodyRanges: BodyRangeList?,
      messageToEdit: Long
    ): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = recipient,
        sentTimeMillis = sentTimeMillis,
        body = body,
        isUrgent = true,
        isSecure = true,
        bodyRanges = bodyRanges,
        messageToEdit = messageToEdit
      )
    }

    /**
     * Helper for creating a group update message when a state change occurs and needs to be sent to others.
     */
    @JvmStatic
    fun groupUpdateMessage(threadRecipient: Recipient, update: GV2UpdateDescription, sentTimeMillis: Long): OutgoingMessage {
      val messageExtras = MessageExtras(gv2UpdateDescription = update)
      val groupContext = MessageGroupContext(update.gv2ChangeDescription!!)

      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        messageGroupContext = groupContext,
        isGroup = true,
        isGroupUpdate = true,
        isSecure = true,
        messageExtras = messageExtras
      )
    }

    /**
     * Helper for creating a group update message when a state change occurs and needs to be sent to others.
     */
    @JvmStatic
    fun groupUpdateMessage(
      threadRecipient: Recipient,
      groupContext: MessageGroupContext,
      avatar: List<Attachment> = emptyList(),
      sentTimeMillis: Long,
      expiresIn: Long = 0L,
      viewOnce: Boolean = false,
      quote: QuoteModel? = null,
      contacts: List<Contact> = emptyList(),
      previews: List<LinkPreview> = emptyList(),
      mentions: List<Mention> = emptyList()
    ): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
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
      threadRecipient: Recipient,
      body: String,
      sentTimeMillis: Long,
      storyType: StoryType,
      linkPreviews: List<LinkPreview>,
      bodyRanges: BodyRangeList?
    ): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        body = body,
        sentTimeMillis = sentTimeMillis,
        storyType = storyType,
        linkPreviews = linkPreviews,
        bodyRanges = bodyRanges,
        isSecure = true
      )
    }

    /**
     * Specialized message sent to request someone activate payments.
     */
    @JvmStatic
    fun requestToActivatePaymentsMessage(threadRecipient: Recipient, sentTimeMillis: Long, expiresIn: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
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
    fun paymentsActivatedMessage(threadRecipient: Recipient, sentTimeMillis: Long, expiresIn: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
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
    fun paymentNotificationMessage(threadRecipient: Recipient, paymentUuid: String, sentTimeMillis: Long, expiresIn: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
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
    fun expirationUpdateMessage(threadRecipient: Recipient, sentTimeMillis: Long, expiresIn: Long, expireTimerVersion: Int): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isExpirationUpdate = true,
        expireTimerVersion = expireTimerVersion,
        isUrgent = false,
        isSecure = true
      )
    }

    /**
     * Message for when you have verified the identity of a contact.
     */
    @JvmStatic
    fun identityVerifiedMessage(threadRecipient: Recipient, sentTimeMillis: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        isIdentityVerified = true,
        isUrgent = false,
        isSecure = true
      )
    }

    /**
     * Message for when the verification status of an identity is getting set to the default.
     */
    @JvmStatic
    fun identityDefaultMessage(threadRecipient: Recipient, sentTimeMillis: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        isIdentityDefault = true,
        isUrgent = false,
        isSecure = true
      )
    }

    /**
     * A legacy message that represented that the user manually reset the session. We don't send these anymore, and could probably get rid of them,
     * but it doesn't hurt to support receiving them in sync messages.
     */
    @JvmStatic
    fun endSessionMessage(threadRecipient: Recipient, sentTimeMillis: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        isEndSession = true,
        isUrgent = false,
        isSecure = true
      )
    }

    @JvmStatic
    fun reportSpamMessage(threadRecipient: Recipient, sentTimeMillis: Long, expiresIn: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isReportSpam = true,
        isUrgent = false,
        isSecure = true
      )
    }

    @JvmStatic
    fun messageRequestAcceptMessage(threadRecipient: Recipient, sentTimeMillis: Long, expiresIn: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isMessageRequestAccept = true,
        isUrgent = false,
        isSecure = true
      )
    }

    /**
     * Message for when you block someone
     */
    @JvmStatic
    fun blockedMessage(threadRecipient: Recipient, sentTimeMillis: Long, expiresIn: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isGroup = threadRecipient.isPushV2Group,
        isBlocked = true,
        isUrgent = false,
        isSecure = true
      )
    }

    /**
     * Message for when you unblock someone
     */
    @JvmStatic
    fun unblockedMessage(threadRecipient: Recipient, sentTimeMillis: Long, expiresIn: Long): OutgoingMessage {
      return OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = sentTimeMillis,
        expiresIn = expiresIn,
        isGroup = threadRecipient.isPushV2Group,
        isUnblocked = true,
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
