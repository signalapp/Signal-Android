package org.signal.benchmark.setup

import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.TestDbUtils
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import java.util.Collections
import java.util.Optional

object TestMessages {
  fun insertOutgoingTextMessage(other: Recipient, body: String, timestamp: Long = System.currentTimeMillis()) {
    insertOutgoingMessage(
      recipient = other,
      message = OutgoingMessage(
        recipient = other,
        body = body,
        timestamp = timestamp,
        isSecure = true
      ),
      timestamp = timestamp
    )
  }

  fun insertOutgoingImageMessage(other: Recipient, body: String? = null, attachmentCount: Int, timestamp: Long = System.currentTimeMillis()): Long {
    val attachments: List<SignalServiceAttachmentPointer> = (0 until attachmentCount).map {
      imageAttachment()
    }
    val message = OutgoingMessage(
      recipient = other,
      body = body,
      attachments = PointerAttachment.forPointers(Optional.of(attachments)),
      timestamp = timestamp,
      isSecure = true
    )
    return insertOutgoingMediaMessage(recipient = other, message = message, timestamp = timestamp)
  }

  private fun insertOutgoingMediaMessage(recipient: Recipient, message: OutgoingMessage, timestamp: Long): Long {
    val insert = insertOutgoingMessage(recipient, message = message, timestamp = timestamp)
    setMessageMediaTransfered(insert)

    return insert
  }

  private fun insertOutgoingMessage(recipient: Recipient, message: OutgoingMessage, timestamp: Long? = null): Long {
    val insert = SignalDatabase.messages.insertMessageOutbox(
      message,
      SignalDatabase.threads.getOrCreateThreadIdFor(recipient),
      false,
      null
    )
    if (timestamp != null) {
      TestDbUtils.setMessageReceived(insert.messageId, timestamp)
    }
    SignalDatabase.messages.markAsSent(insert.messageId, true)

    return insert.messageId
  }
  fun insertIncomingTextMessage(other: Recipient, body: String, timestamp: Long? = null) {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      body = body,
      sentTimeMillis = timestamp ?: System.currentTimeMillis(),
      serverTimeMillis = timestamp ?: System.currentTimeMillis(),
      receivedTimeMillis = timestamp ?: System.currentTimeMillis()
    )

    SignalDatabase.messages.insertMessageInbox(message, SignalDatabase.threads.getOrCreateThreadIdFor(other)).get().messageId
  }
  fun insertIncomingQuoteTextMessage(other: Recipient, body: String, quote: QuoteModel, timestamp: Long?) {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      body = body,
      sentTimeMillis = timestamp ?: System.currentTimeMillis(),
      serverTimeMillis = timestamp ?: System.currentTimeMillis(),
      receivedTimeMillis = timestamp ?: System.currentTimeMillis(),
      quote = quote
    )
    insertIncomingMessage(other, message = message)
  }
  fun insertIncomingImageMessage(other: Recipient, body: String? = null, attachmentCount: Int, timestamp: Long? = null, failed: Boolean = false): Long {
    val attachments: List<SignalServiceAttachmentPointer> = (0 until attachmentCount).map {
      imageAttachment()
    }
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      sentTimeMillis = timestamp ?: System.currentTimeMillis(),
      serverTimeMillis = timestamp ?: System.currentTimeMillis(),
      receivedTimeMillis = timestamp ?: System.currentTimeMillis(),
      attachments = PointerAttachment.forPointers(Optional.of(attachments))
    )
    return insertIncomingMessage(recipient = other, message = message, failed = failed)
  }

  fun insertIncomingVoiceMessage(other: Recipient, timestamp: Long? = null): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = other.id,
      sentTimeMillis = timestamp ?: System.currentTimeMillis(),
      serverTimeMillis = timestamp ?: System.currentTimeMillis(),
      receivedTimeMillis = timestamp ?: System.currentTimeMillis(),
      attachments = PointerAttachment.forPointers(Optional.of(Collections.singletonList(voiceAttachment()) as List<SignalServiceAttachment>))
    )
    return insertIncomingMessage(recipient = other, message = message, failed = false)
  }

  private fun insertIncomingMessage(recipient: Recipient, message: IncomingMessage, failed: Boolean = false): Long {
    val id = insertIncomingMessage(recipient = recipient, message = message)
    if (failed) {
      setMessageMediaFailed(id)
    } else {
      setMessageMediaTransfered(id)
    }

    return id
  }

  private fun insertIncomingMessage(recipient: Recipient, message: IncomingMessage): Long {
    return SignalDatabase.messages.insertMessageInbox(message, SignalDatabase.threads.getOrCreateThreadIdFor(recipient)).get().messageId
  }

  private fun setMessageMediaFailed(messageId: Long) {
    SignalDatabase.attachments.getAttachmentsForMessage(messageId).forEachIndexed { index, attachment ->
      SignalDatabase.attachments.setTransferProgressPermanentFailure(attachment.attachmentId, messageId)
    }
  }

  private fun setMessageMediaTransfered(messageId: Long) {
    SignalDatabase.attachments.getAttachmentsForMessage(messageId).forEachIndexed { _, attachment ->
      SignalDatabase.attachments.setTransferState(messageId, attachment.attachmentId, AttachmentTable.TRANSFER_PROGRESS_DONE)
    }
  }
  private fun imageAttachment(): SignalServiceAttachmentPointer {
    return SignalServiceAttachmentPointer(
      Cdn.S3.cdnNumber,
      SignalServiceAttachmentRemoteId.from(""),
      "image/webp",
      null,
      Optional.empty(),
      Optional.empty(),
      1024,
      1024,
      Optional.empty(),
      Optional.empty(),
      0,
      Optional.of("/not-there.jpg"),
      false,
      false,
      false,
      Optional.empty(),
      Optional.empty(),
      System.currentTimeMillis(),
      null
    )
  }

  private fun voiceAttachment(): SignalServiceAttachmentPointer {
    return SignalServiceAttachmentPointer(
      Cdn.S3.cdnNumber,
      SignalServiceAttachmentRemoteId.from(""),
      "audio/aac",
      null,
      Optional.empty(),
      Optional.empty(),
      1024,
      1024,
      Optional.empty(),
      Optional.empty(),
      0,
      Optional.of("/not-there.aac"),
      true,
      false,
      false,
      Optional.empty(),
      Optional.empty(),
      System.currentTimeMillis(),
      null
    )
  }

  class TimestampGenerator(private var start: Long = System.currentTimeMillis()) {
    fun nextTimestamp(): Long {
      start += 500L

      return start
    }
  }
}
