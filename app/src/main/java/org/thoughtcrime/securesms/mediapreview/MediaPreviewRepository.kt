package org.thoughtcrime.securesms.mediapreview

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.database.MediaDatabase.Sorting
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.media

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
        val mediaRecords = mutableListOf<MediaDatabase.MediaRecord>()
        var startingRow = -1
        while (cursor.moveToNext()) {
          if (startingAttachmentId.rowId == cursor.requireLong(AttachmentDatabase.ROW_ID) &&
            startingAttachmentId.uniqueId == cursor.requireLong(AttachmentDatabase.UNIQUE_ID)
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
            val element = MediaDatabase.MediaRecord.from(cursor)
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

  data class Result(val initialPosition: Int, val records: List<MediaDatabase.MediaRecord>)
}
