package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.content.Intent
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.MediaTable.Sorting
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.media
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.AttachmentUtil

/**
 * Repository for accessing the attachments in the encrypted database.
 */
class MediaPreviewRepository {
  companion object {
    private val TAG: String = Log.tag(MediaPreviewRepository::class.java)
  }

  /**
   * Accessor for database attachments.
   * @param startingUri the initial position to select from
   * @param threadId the thread to select from
   * @param sorting the ordering of the results
   * @param limit the maximum quantity of the results
   */
  fun getAttachments(startingAttachmentId: AttachmentId, threadId: Long, sorting: Sorting, limit: Int = 500): Flowable<Result> {
    return Single.fromCallable {
      media.getGalleryMediaForThread(threadId, sorting).use { cursor ->
        val mediaRecords = mutableListOf<MediaTable.MediaRecord>()
        var startingRow = -1
        while (cursor.moveToNext()) {
          if (startingAttachmentId.rowId == cursor.requireLong(AttachmentTable.ROW_ID) &&
            startingAttachmentId.uniqueId == cursor.requireLong(AttachmentTable.UNIQUE_ID)
          ) {
            startingRow = cursor.position
            break
          }
        }

        var itemPosition = -1
        if (startingRow >= 0) {
          val frontLimit: Int = limit / 2
          val windowStart = if (startingRow >= frontLimit) startingRow - frontLimit else 0

          itemPosition = startingRow - windowStart

          cursor.moveToPosition(windowStart)

          for (i in 0..limit) {
            val element = MediaTable.MediaRecord.from(cursor)
            if (element != null) {
              mediaRecords.add(element)
            }
            if (!cursor.moveToNext()) {
              break
            }
          }
        }
        Result(itemPosition, mediaRecords.toList())
      }
    }.subscribeOn(Schedulers.io()).toFlowable()
  }

  fun localDelete(context: Context, attachment: DatabaseAttachment): Completable {
    return Completable.fromRunnable {
      AttachmentUtil.deleteAttachment(context.applicationContext, attachment)
    }.subscribeOn(Schedulers.io())
  }

  fun remoteDelete(attachment: DatabaseAttachment): Completable {
    return Completable.fromRunnable {
      MessageSender.sendRemoteDelete(attachment.mmsId, true)
    }.subscribeOn(Schedulers.io())
  }

  fun getMessagePositionIntent(context: Context, messageId: Long): Single<Intent> {
    return Single.fromCallable {
      val messageRecord = SignalDatabase.mms.getMessageRecord(messageId)
      val messagePosition = SignalDatabase.mmsSms.getMessagePositionInConversation(messageRecord.threadId, messageRecord.dateReceived)
      ConversationIntents.createBuilder(context, messageRecord.recipient.id, messageRecord.threadId)
        .withStartingPosition(messagePosition)
        .build()
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
  }

  data class Result(val initialPosition: Int, val records: List<MediaTable.MediaRecord>)
}
