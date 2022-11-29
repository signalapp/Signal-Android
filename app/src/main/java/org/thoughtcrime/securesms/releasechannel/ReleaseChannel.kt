package org.thoughtcrime.securesms.releasechannel

import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.recipients.RecipientId
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
    image: String? = null,
    imageWidth: Int = 0,
    imageHeight: Int = 0,
    serverUuid: String? = UUID.randomUUID().toString(),
    messageRanges: BodyRangeList? = null,
    storyType: StoryType = StoryType.NONE
  ): MessageTable.InsertResult? {

    val attachments: Optional<List<SignalServiceAttachment>> = if (image != null) {
      val attachment = SignalServiceAttachmentPointer(
        CDN_NUMBER,
        SignalServiceAttachmentRemoteId.from(""),
        "image/webp",
        null,
        Optional.empty(),
        Optional.empty(),
        imageWidth,
        imageHeight,
        Optional.empty(),
        Optional.of(image),
        false,
        false,
        false,
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

    return SignalDatabase.mms.insertSecureDecryptedMessageInbox(message, threadId).orElse(null)
  }
}
