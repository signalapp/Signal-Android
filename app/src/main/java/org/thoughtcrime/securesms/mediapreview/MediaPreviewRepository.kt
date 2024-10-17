package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.Stopwatch
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
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob
import org.thoughtcrime.securesms.longmessage.resolveBody
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
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
   * @param startingAttachmentId the initial position to select from
   * @param threadId the thread to select from
   * @param sorting the ordering of the results
   * @param limit the maximum quantity of the results
   */
  fun getAttachments(context: Context, startingAttachmentId: AttachmentId, threadId: Long, sorting: Sorting, limit: Int = 500): Flowable<Result> {
    return Single.fromCallable {
      media.getGalleryMediaForThread(threadId, sorting).use { cursor ->
        val mediaRecords = mutableListOf<MediaTable.MediaRecord>()
        var startingRow = -1
        while (cursor.moveToNext()) {
          if (startingAttachmentId.id == cursor.requireLong(AttachmentTable.ID)) {
            startingRow = cursor.position
            break
          }
        }

        var itemPosition = -1
        if (startingRow >= 0) {
          val frontLimit: Int = limit / 2
          val windowStart = if (startingRow >= frontLimit) startingRow - frontLimit else 0

          cursor.moveToPosition(windowStart)

          for (i in 0..limit) {
            val element = MediaTable.MediaRecord.from(cursor)
            if (element.attachment?.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE ||
              element.attachment?.transferState == AttachmentTable.TRANSFER_PROGRESS_STARTED ||
              element.attachment?.thumbnailUri != null
            ) {
              mediaRecords.add(element)

              if (startingAttachmentId.id == cursor.requireLong(AttachmentTable.ID)) {
                itemPosition = mediaRecords.lastIndex
              }
            }

            if (!cursor.moveToNext()) {
              break
            }
          }

          if (itemPosition == -1) {
            Log.w(TAG, "Unable to find target image for $startingAttachmentId")
          }
        }

        val messageIds = mediaRecords.mapNotNull { it.attachment?.mmsId }.toSet()
        val messages: Map<Long, SpannableString> = SignalDatabase.messages.getMessages(messageIds)
          .map { it as MmsMessageRecord }
          .associate { it.id to it.resolveBody(context).getDisplayBody(context) }

        Result(if (mediaRecords.isNotEmpty()) itemPosition.coerceIn(mediaRecords.indices) else itemPosition, mediaRecords, messages)
      }
    }.subscribeOn(Schedulers.io()).toFlowable()
  }

  fun localDelete(attachment: DatabaseAttachment): Completable {
    return Completable.fromRunnable {
      val deletedMessageRecord = AttachmentUtil.deleteAttachment(attachment)
      if (deletedMessageRecord != null && Recipient.self().deleteSyncCapability.isSupported) {
        MultiDeviceDeleteSyncJob.enqueueMessageDeletes(setOf(deletedMessageRecord))
      }
    }.subscribeOn(Schedulers.io())
  }

  fun remoteDelete(attachment: DatabaseAttachment): Completable {
    return Completable.fromRunnable {
      MessageSender.sendRemoteDelete(attachment.mmsId)
    }.subscribeOn(Schedulers.io())
  }

  fun getMessagePositionIntent(context: Context, messageId: Long): Single<Intent> {
    return Single.fromCallable {
      val stopwatch = Stopwatch("Message Position Intent")
      val messageRecord: MessageRecord = SignalDatabase.messages.getMessageRecord(messageId)
      stopwatch.split("get message record")

      val threadId: Long = messageRecord.threadId
      val messagePosition: Int = SignalDatabase.messages.getMessagePositionInConversation(threadId, messageRecord.dateReceived)
      stopwatch.split("get message position")

      val recipientId: RecipientId = SignalDatabase.threads.getRecipientForThreadId(threadId)?.id ?: throw IllegalStateException("Could not find recipient for thread ID $threadId")
      stopwatch.split("get recipient ID")

      stopwatch.stop(TAG)
      ConversationIntents.createBuilderSync(context, recipientId, threadId)
        .withStartingPosition(messagePosition)
        .build()
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
  }

  data class Result(val initialPosition: Int, val records: List<MediaTable.MediaRecord>, val messageBodies: Map<Long, SpannableString>)
}
