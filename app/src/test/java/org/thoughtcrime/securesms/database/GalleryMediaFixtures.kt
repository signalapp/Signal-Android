/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.ContentValues
import org.signal.core.database.update
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testutil.RecipientTestRule

/**
 * Inserts threads, messages, and attachments for gallery media tests. Messages get strictly increasing receive timestamps.
 */
class GalleryMediaFixtures(private val recipients: RecipientTestRule) {

  private var nextReceivedTimestamp = 1_000_000L

  fun createThread(): Long {
    val contact = recipients.createRecipient("Media Sender")
    return SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(contact))
  }

  fun insertMessage(threadId: Long, scheduledDate: Long = -1, linkPreviews: String? = null): Long {
    val messageId = TestSms.insert(recipients.signalDatabase.writeableDatabase, threadId = threadId, receivedTimestampMillis = nextReceivedTimestamp++)

    if (scheduledDate != -1L || linkPreviews != null) {
      SignalDatabase.writableDatabase
        .update(MessageTable.TABLE_NAME)
        .values(
          MessageTable.SCHEDULED_DATE to scheduledDate,
          MessageTable.LINK_PREVIEWS to linkPreviews
        )
        .where("${MessageTable.ID} = ?", messageId)
        .run()
    }

    return messageId
  }

  fun insertAttachment(
    messageId: Long,
    contentType: String = "image/jpeg",
    transferState: Int = AttachmentTable.TRANSFER_PROGRESS_DONE,
    dataFile: String? = "/data/file",
    dataSize: Long = 100,
    thumbnailFile: String? = null,
    incrementalDigest: ByteArray? = null,
    thumbnailRestoreState: AttachmentTable.ThumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.NONE,
    displayOrder: Int = 0
  ): Long {
    return SignalDatabase.writableDatabase.insert(
      AttachmentTable.TABLE_NAME,
      null,
      ContentValues().apply {
        put(AttachmentTable.MESSAGE_ID, messageId)
        put(AttachmentTable.CONTENT_TYPE, contentType)
        put(AttachmentTable.TRANSFER_STATE, transferState)
        put(AttachmentTable.DATA_SIZE, dataSize)
        put(AttachmentTable.DISPLAY_ORDER, displayOrder)
        put(AttachmentTable.THUMBNAIL_RESTORE_STATE, thumbnailRestoreState.value)
        dataFile?.let { put(AttachmentTable.DATA_FILE, it) }
        thumbnailFile?.let { put(AttachmentTable.THUMBNAIL_FILE, it) }
        incrementalDigest?.let { put(AttachmentTable.REMOTE_INCREMENTAL_DIGEST, it) }
      }
    )
  }
}
