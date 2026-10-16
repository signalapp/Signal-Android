/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaviewer

import android.content.Context
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Stopwatch
import org.signal.core.util.logTime
import org.signal.core.util.logging.Log
import org.signal.mediaviewer.MediaViewerItem
import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pages the gallery media of a thread (or all threads) in display order.
 */
class MediaViewerPagedDataSource(
  private val context: Context,
  private val threadId: Long,
  private val sorting: MediaTable.Sorting,
  private val leftIsRecent: Boolean
) : PagedDataSource<AttachmentId, MediaViewerItem> {

  private val primedCount = AtomicInteger(-1)

  override fun size(): Int {
    val primed = primedCount.getAndSet(-1)
    if (primed >= 0) {
      Log.d(TAG, "size(), thread $threadId: $primed (primed by findDisplayIndex)")
      return primed
    }
    return logTime(TAG, "size(), thread $threadId", decimalPlaces = 2) {
      SignalDatabase.media.getViewableGalleryMediaCountForThread(threadId)
    }
  }

  override fun load(start: Int, length: Int, totalSize: Int, cancellationSignal: PagedDataSource.CancellationSignal): List<MediaViewerItem> {
    val stopwatch = Stopwatch(title = "load($start, $length), thread $threadId", decimalPlaces = 2)

    val end = start + length
    val offset = if (leftIsRecent) start else totalSize - end

    val records = SignalDatabase.media.getViewableGalleryMediaForThread(threadId, sorting, limit = end - start, offset = offset)
    stopwatch.split("query")

    val items = records.mapNotNull { it.toMediaViewerItem(context) }
    stopwatch.split("to-items")
    stopwatch.stop(TAG)

    if (items.size != length) {
      Log.w(TAG, "Short page: wanted ${end - start} from $offset, got ${items.size}. Awaiting invalidation.")
      return emptyList()
    }

    return if (leftIsRecent) items else items.reversed()
  }

  override fun load(key: AttachmentId): MediaViewerItem? {
    return logTime(TAG, "load($key), thread $threadId", decimalPlaces = 2) {
      SignalDatabase.media.getViewableGalleryMediaForAttachment(threadId, key.id)?.toMediaViewerItem(context)
    }
  }

  override fun getKey(data: MediaViewerItem): AttachmentId = data.attachmentId

  /**
   * The display-order index of the given attachment, or -1 when it is no longer displayable.
   * Also primes the next [size] call, since both come from the same scan of the gallery.
   */
  fun findDisplayIndex(attachmentId: AttachmentId): Int {
    val positionAndCount = logTime(TAG, "findDisplayIndex($attachmentId), thread $threadId", decimalPlaces = 2) {
      SignalDatabase.media.getViewableGalleryMediaPositionAndCount(threadId, sorting, attachmentId.id)
    } ?: return -1

    primedCount.set(positionAndCount.count)

    return if (leftIsRecent) {
      positionAndCount.position
    } else {
      positionAndCount.count - 1 - positionAndCount.position
    }
  }

  /** Whether the attachment is still part of the viewable gallery. */
  fun isStillViewable(attachmentId: AttachmentId): Boolean {
    return logTime(TAG, "isStillViewable($attachmentId), thread $threadId", decimalPlaces = 2) {
      SignalDatabase.media.getViewableGalleryMediaForAttachment(threadId, attachmentId.id) != null
    }
  }

  companion object {
    private val TAG = Log.tag(MediaViewerPagedDataSource::class)
  }
}

/** Maps a database record to the feature module's item model. Null when the record has no attachment. */
private fun MediaTable.MediaRecord.toMediaViewerItem(context: Context): MediaViewerItem? {
  val attachment = this.attachment ?: return null
  val threadRecipient = Recipient.resolved(threadRecipientId)
  val (formattedDate, formattedDateContentDescription) = if (date > 0) {
    DateUtils.getExtendedRelativeTimeSpanString(context, Locale.getDefault(), date)
  } else {
    "" to ""
  }

  return MediaViewerItem(
    attachmentId = attachment.attachmentId,
    uri = attachment.displayUri ?: PartAuthority.getAttachmentDataUri(attachment.attachmentId),
    playbackUri = attachment.uri,
    contentType = attachment.contentType,
    size = attachment.size,
    width = attachment.width,
    height = attachment.height,
    isVideoGif = attachment.videoGif,
    hasData = attachment.hasData,
    transferNeeded = attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE,
    messageId = attachment.mmsId,
    threadId = threadId,
    date = date,
    formattedDate = formattedDate,
    formattedDateContentDescription = formattedDateContentDescription,
    isOutgoing = isOutgoing,
    caption = attachment.caption,
    fromDisplayName = Recipient.resolved(recipientId).getDisplayName(context),
    threadDisplayName = threadRecipient.getDisplayName(context),
    isThreadGroup = threadRecipient.isGroup,
    isNoteToSelf = isOutgoing && threadRecipient.isSelf
  )
}
