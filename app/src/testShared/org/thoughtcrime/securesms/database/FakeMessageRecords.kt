package org.thoughtcrime.securesms.database

import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.audio.AudioHash
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch
import org.thoughtcrime.securesms.database.documents.NetworkFailure
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.payments.Payment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.MediaUtil

/**
 * Builds MessageRecords and related components for direct usage in unit testing. Does not modify the database.
 */
object FakeMessageRecords {

  fun buildDatabaseAttachment(
    attachmentId: AttachmentId = AttachmentId(1),
    mmsId: Long = 1,
    hasData: Boolean = true,
    hasThumbnail: Boolean = true,
    hasArchiveThumbnail: Boolean = false,
    contentType: String = MediaUtil.IMAGE_JPEG,
    transferProgress: Int = AttachmentTable.TRANSFER_PROGRESS_DONE,
    size: Long = 0L,
    fileName: String = "",
    cdnNumber: Int = 3,
    location: String = "",
    key: String = "",
    relay: String = "",
    digest: ByteArray = byteArrayOf(),
    incrementalDigest: ByteArray = byteArrayOf(),
    incrementalMacChunkSize: Int = 0,
    fastPreflightId: String = "",
    voiceNote: Boolean = false,
    borderless: Boolean = false,
    videoGif: Boolean = false,
    width: Int = 0,
    height: Int = 0,
    quote: Boolean = false,
    caption: String? = null,
    stickerLocator: StickerLocator? = null,
    blurHash: BlurHash? = null,
    audioHash: AudioHash? = null,
    transformProperties: AttachmentTable.TransformProperties? = null,
    displayOrder: Int = 0,
    uploadTimestamp: Long = 200,
    dataHash: String? = null,
    archiveCdn: Int = 0,
    archiveThumbnailCdn: Int = 0,
    archiveMediaName: String? = null,
    archiveMediaId: String? = null,
    archiveThumbnailId: String? = null,
    thumbnailRestoreState: AttachmentTable.ThumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.NONE
  ): DatabaseAttachment {
    return DatabaseAttachment(
      attachmentId,
      mmsId,
      hasData,
      hasThumbnail,
      hasArchiveThumbnail,
      contentType,
      transferProgress,
      size,
      fileName,
      Cdn.fromCdnNumber(cdnNumber),
      location,
      key,
      digest,
      incrementalDigest,
      incrementalMacChunkSize,
      fastPreflightId,
      voiceNote,
      borderless,
      videoGif,
      width,
      height,
      quote,
      caption,
      stickerLocator,
      blurHash,
      audioHash,
      transformProperties,
      displayOrder,
      uploadTimestamp,
      dataHash,
      archiveCdn,
      archiveThumbnailCdn,
      archiveMediaId,
      archiveMediaName,
      thumbnailRestoreState,
      null
    )
  }

  fun buildLinkPreview(
    url: String = "",
    title: String = "",
    description: String = "",
    date: Long = 200,
    attachmentId: AttachmentId? = null
  ): LinkPreview {
    return LinkPreview(
      url,
      title,
      description,
      date,
      attachmentId
    )
  }

  fun buildMediaMmsMessageRecord(
    id: Long = 1,
    conversationRecipient: Recipient = Recipient.UNKNOWN,
    individualRecipient: Recipient = conversationRecipient,
    recipientDeviceId: Int = 1,
    dateSent: Long = 200,
    dateReceived: Long = 400,
    dateServer: Long = 300,
    hasDeliveryReceipt: Boolean = false,
    threadId: Long = 1,
    body: String = "body",
    slideDeck: SlideDeck = SlideDeck(),
    partCount: Int = slideDeck.slides.count(),
    mailbox: Long = MessageTypes.BASE_INBOX_TYPE,
    mismatches: Set<IdentityKeyMismatch> = emptySet(),
    failures: Set<NetworkFailure> = emptySet(),
    subscriptionId: Int = -1,
    expiresIn: Long = -1,
    expireStarted: Long = -1,
    viewOnce: Boolean = false,
    hasReadReceipt: Boolean = false,
    quote: Quote? = null,
    contacts: List<Contact> = emptyList(),
    linkPreviews: List<LinkPreview> = emptyList(),
    unidentified: Boolean = false,
    reactions: List<ReactionRecord> = emptyList(),
    remoteDelete: Boolean = false,
    mentionsSelf: Boolean = false,
    notifiedTimestamp: Long = 350,
    viewed: Boolean = false,
    receiptTimestamp: Long = 0,
    messageRanges: BodyRangeList? = null,
    storyType: StoryType = StoryType.NONE,
    parentStoryId: ParentStoryId? = null,
    giftBadge: GiftBadge? = null,
    payment: Payment? = null,
    call: CallTable.Call? = null
  ): MmsMessageRecord {
    return MmsMessageRecord(
      id,
      conversationRecipient,
      recipientDeviceId,
      individualRecipient,
      dateSent,
      dateReceived,
      dateServer,
      hasDeliveryReceipt,
      threadId,
      body,
      slideDeck,
      mailbox,
      mismatches,
      failures,
      subscriptionId,
      expiresIn,
      expireStarted,
      viewOnce,
      hasReadReceipt,
      quote,
      contacts,
      linkPreviews,
      unidentified,
      reactions,
      remoteDelete,
      mentionsSelf,
      notifiedTimestamp,
      viewed,
      receiptTimestamp,
      messageRanges,
      storyType,
      parentStoryId,
      giftBadge,
      payment,
      call,
      -1,
      null,
      null,
      0,
      false,
      null
    )
  }
}
