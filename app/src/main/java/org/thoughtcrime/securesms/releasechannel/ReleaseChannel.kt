package org.thoughtcrime.securesms.releasechannel

import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import java.util.Optional
import java.util.UUID

/**
 * One stop shop for inserting Release Channel messages.
 */
object ReleaseChannel {

  const val CDN_NUMBER = -1

  fun insertReleaseChannelMessage(
    recipientId: RecipientId,
    body: String,
    threadId: Long,
    media: String? = null,
    mediaWidth: Int = 0,
    mediaHeight: Int = 0,
    mediaType: String = "image/webp",
    serverUuid: String? = UUID.randomUUID().toString(),
    messageRanges: BodyRangeList? = null,
    storyType: StoryType = StoryType.NONE
  ): MessageTable.InsertResult? {
    val attachments: Optional<List<SignalServiceAttachment>> = if (media != null) {
      val attachment = SignalServiceAttachmentPointer(
        CDN_NUMBER,
        SignalServiceAttachmentRemoteId.from(""),
        mediaType,
        null,
        Optional.empty(),
        Optional.empty(),
        mediaWidth,
        mediaHeight,
        Optional.empty(),
        Optional.of(media),
        false,
        false,
        MediaUtil.isVideo(mediaType),
        Optional.empty(),
        Optional.empty(),
        System.currentTimeMillis()
      )

      Optional.of(listOf(attachment))
    } else {
      Optional.empty()
    }

    val message = IncomingMediaMessage(
      from = recipientId,
      sentTimeMillis = System.currentTimeMillis(),
      serverTimeMillis = System.currentTimeMillis(),
      receivedTimeMillis = System.currentTimeMillis(),
      body = body,
      attachments = PointerAttachment.forPointers(attachments),
      serverGuid = serverUuid,
      messageRanges = messageRanges,
      storyType = storyType
    )

    return SignalDatabase.messages.insertSecureDecryptedMessageInbox(message, threadId).orElse(null)
  }
}
