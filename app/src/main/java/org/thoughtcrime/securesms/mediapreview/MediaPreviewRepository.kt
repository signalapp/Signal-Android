package org.thoughtcrime.securesms.mediapreview

import android.net.Uri
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
import org.thoughtcrime.securesms.mms.PartAuthority

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
  fun getAttachments(startingUri: Uri, threadId: Long, sorting: Sorting, limit: Int = 500): Flowable<List<MediaDatabase.MediaRecord>> {
    return Single.fromCallable {
      val cursor = media.getGalleryMediaForThread(threadId, sorting)

      val acc = mutableListOf<MediaDatabase.MediaRecord>()
      var attachmentUri: Uri? = null
      while (cursor.moveToNext()) {
        val attachmentId = AttachmentId(cursor.requireLong(AttachmentDatabase.ROW_ID), cursor.requireLong(AttachmentDatabase.UNIQUE_ID))
        attachmentUri = PartAuthority.getAttachmentDataUri(attachmentId)
        if (attachmentUri == startingUri) {
          break
        }
      }

      if (attachmentUri == startingUri) {
        for (i in 0..limit) {
          val element = MediaDatabase.MediaRecord.from(cursor)
          if (element != null) {
            acc.add(element)
          }
          if (!cursor.isLast) {
            cursor.moveToNext()
          } else {
            break
          }
        }
        acc.toList()
      } else {
        Log.e(TAG, "Could not find $startingUri in thread $threadId")
        emptyList()
      }
    }.subscribeOn(Schedulers.io()).toFlowable()
  }
}
