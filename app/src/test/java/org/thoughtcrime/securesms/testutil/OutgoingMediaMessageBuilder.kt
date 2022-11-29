package org.thoughtcrime.securesms.testutil

import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch
import org.thoughtcrime.securesms.database.documents.NetworkFailure
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient

object OutgoingMediaMessageBuilder {
  fun create(
    recipient: Recipient = Recipient.UNKNOWN,
    message: String = "",
    attachments: List<Attachment> = emptyList(),
    sentTimeMillis: Long = System.currentTimeMillis(),
    subscriptionId: Int = -1,
    expiresIn: Long = -1,
    viewOnce: Boolean = false,
    distributionType: Int = ThreadTable.DistributionTypes.DEFAULT,
    storyType: StoryType = StoryType.NONE,
    parentStoryId: ParentStoryId? = null,
    isStoryReaction: Boolean = false,
    quoteModel: QuoteModel? = null,
    contacts: List<Contact> = emptyList(),
    linkPreviews: List<LinkPreview> = emptyList(),
    mentions: List<Mention> = emptyList(),
    networkFailures: Set<NetworkFailure> = emptySet(),
    identityKeyMismatches: Set<IdentityKeyMismatch> = emptySet(),
    giftBadge: GiftBadge? = null
  ): OutgoingMediaMessage {
    return OutgoingMediaMessage(
      recipient,
      message,
      attachments,
      sentTimeMillis,
      subscriptionId,
      expiresIn,
      viewOnce,
      distributionType,
      storyType,
      parentStoryId,
      isStoryReaction,
      quoteModel,
      contacts,
      linkPreviews,
      mentions,
      networkFailures,
      identityKeyMismatches,
      giftBadge
    )
  }

  fun OutgoingMediaMessage.secure(): OutgoingSecureMediaMessage = OutgoingSecureMediaMessage(this)
}
