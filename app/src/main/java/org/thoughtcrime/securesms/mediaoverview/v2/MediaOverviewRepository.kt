/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview.v2

import android.content.Context
import android.content.Intent
import android.database.Cursor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.models.database.AttachmentId
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.readToList
import org.signal.mediaoverview.MediaOverviewAction
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewState
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.AttachmentUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.Locale

/**
 * Loads and acts on the media shown by the media overview. Actions take item keys and look up the
 * current records themselves, since the paged list only ever holds part of a tab.
 */
class MediaOverviewRepository(
  private val context: Context = AppDependencies.application
) {

  fun observeTitle(threadId: Long): Flow<String> {
    return flow {
      val recipientId = SignalDatabase.threads.getRecipientForThreadId(threadId)?.id
      if (recipientId != null) {
        emitAll(Recipient.live(recipientId).observable().asFlow())
      }
    }
      .map { it.getDisplayName(context) }
      .distinctUntilChanged()
      .flowOn(SignalDispatchers.Default)
  }

  /**
   * Calls [onChange] whenever any attachment changes, until the returned function is called.
   */
  fun observeMediaChanges(onChange: () -> Unit): () -> Unit {
    val observer = DatabaseObserver.Observer { onChange() }
    AppDependencies.databaseObserver.registerAttachmentUpdatedObserver(observer)
    return { AppDependencies.databaseObserver.unregisterObserver(observer) }
  }

  fun mediaChanges(): Flow<Unit> {
    return callbackFlow {
      val unregister = observeMediaChanges { trySend(Unit) }
      awaitClose { unregister() }
    }.conflate()
  }

  suspend fun getCount(threadId: Long, tab: MediaOverviewState.Tab): Int {
    return withContext(SignalDispatchers.Default) {
      val media = SignalDatabase.media
      when (tab) {
        MediaOverviewState.Tab.MEDIA -> media.getGalleryMediaCountForThread(threadId)
        MediaOverviewState.Tab.FILES -> media.getDocumentMediaCountForThread(threadId)
        MediaOverviewState.Tab.AUDIO -> media.getAudioMediaCountForThread(threadId)
        MediaOverviewState.Tab.LINKS -> media.getLinkMediaCountForThread(threadId)
        MediaOverviewState.Tab.ALL -> media.getAllMediaCountForThread(threadId)
      }
    }
  }

  suspend fun getPageRecords(threadId: Long, tab: MediaOverviewState.Tab, sorting: MediaOverviewState.Sorting, offset: Int, limit: Int): List<MediaTable.MediaRecord> {
    return withContext(SignalDispatchers.Default) {
      query(threadId, tab, sorting, limit, offset).readToList { MediaTable.MediaRecord.from(it) }
    }
  }

  /** Every sender and conversation recipient [records] mention, resolved once each. */
  suspend fun resolveRecipients(records: List<MediaTable.MediaRecord>): Map<RecipientId, Recipient> {
    return withContext(SignalDispatchers.Default) {
      val ids = records.flatMap { record -> listOfNotNull(record.recipientId.takeUnless { record.isOutgoing }, record.threadRecipientId) }.toSet()
      ids.associateWith { Recipient.resolved(it) }
    }
  }

  suspend fun toItems(records: List<MediaTable.MediaRecord>, recipients: Map<RecipientId, Recipient>): List<MediaOverviewItem> {
    return withContext(SignalDispatchers.Default) {
      val showArchiveHint = SignalStore.internal.showArchiveStateHint
      records.map { toItem(it, recipients, showArchiveHint) }
    }
  }

  /** The current state of the items for [keys]. Keys that no longer exist are skipped. */
  suspend fun getItems(keys: Collection<MediaOverviewItem.Key>): List<MediaOverviewItem> {
    return withContext(SignalDispatchers.Default) {
      val records = getRecords(keys)
      toItems(records, resolveRecipients(records))
    }
  }

  suspend fun getExistingKeys(keys: Collection<MediaOverviewItem.Key>): Set<MediaOverviewItem.Key> {
    return withContext(SignalDispatchers.Default) { getRecords(keys).map { it.toKey() }.toSet() }
  }

  /** Every item in [tab], mapped to its size, without loading the full records. */
  suspend fun getAllKeysWithSizes(threadId: Long, tab: MediaOverviewState.Tab, sorting: MediaOverviewState.Sorting): Map<MediaOverviewItem.Key, Long> {
    return withContext(SignalDispatchers.Default) {
      query(threadId, tab, sorting, limit = 0, offset = 0).use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(AttachmentTable.ID)
        val sizeColumn = cursor.getColumnIndexOrThrow(AttachmentTable.DATA_SIZE)
        val messageIdColumn = cursor.getColumnIndexOrThrow(MediaTable.MEDIA_MESSAGE_ID)

        buildMap {
          while (cursor.moveToNext()) {
            val key = if (cursor.isNull(idColumn)) {
              MediaOverviewItem.Key.Message(cursor.getLong(messageIdColumn))
            } else {
              MediaOverviewItem.Key.Attachment(AttachmentId(cursor.getLong(idColumn)))
            }
            put(key, cursor.getLong(sizeColumn))
          }
        }
      }
    }
  }

  fun isOptimizeStorageEnabled(): Boolean = SignalStore.backup.optimizeStorage

  suspend fun download(key: MediaOverviewItem.Key) {
    withContext(SignalDispatchers.IO) {
      getRecords(listOf(key)).firstOrNull()?.attachment?.let { AttachmentDownloadJob.downloadAttachmentIfNeeded(it) }
    }
  }

  /** Deletes the media for [keys], reporting how many have been processed as it goes. */
  suspend fun delete(keys: Collection<MediaOverviewItem.Key>, onProgress: (Int) -> Unit) {
    withContext(SignalDispatchers.IO) {
      val deletedMessageRecords = AttachmentUtil.deleteAttachments(getRecords(keys), onProgress)
      if (deletedMessageRecords.isNotEmpty()) {
        MultiDeviceDeleteSyncJob.enqueueMessageDeletes(deletedMessageRecords)
      }
    }
  }

  suspend fun createPreviewIntent(key: MediaOverviewItem.Key, sorting: MediaOverviewState.Sorting, isAllThreads: Boolean): Intent? {
    val record = withContext(SignalDispatchers.Default) { getRecords(listOf(key)).firstOrNull() } ?: return null
    val attachment = record.attachment ?: return null
    val displayUri = attachment.displayUri ?: return null

    val args = MediaIntentFactory.MediaPreviewArgs(
      threadId = if (isAllThreads) MediaTable.ALL_THREADS.toLong() else record.threadId,
      date = record.date,
      messageId = record.messageId,
      fromRecipientId = record.recipientId,
      threadRecipientId = record.threadRecipientId,
      outgoing = record.isOutgoing,
      initialMediaUri = displayUri,
      initialMediaDataUri = attachment.uri,
      initialMediaType = record.contentType,
      initialMediaSize = attachment.size,
      initialCaption = attachment.caption,
      leftIsRecent = true,
      showThread = isAllThreads,
      allMediaInRail = true,
      sorting = sorting.toMediaTableSorting(),
      isVideoGif = attachment.videoGif,
      sharedElementArgs = MediaIntentFactory.SharedElementArgs(
        width = attachment.width,
        height = attachment.height,
        topLeft = DimensionUnit.DP.toDp(12f),
        topRight = DimensionUnit.DP.toDp(12f),
        bottomRight = DimensionUnit.DP.toDp(12f),
        bottomLeft = DimensionUnit.DP.toDp(12f)
      ),
      skipSharedElementTransition = false
    )

    return MediaIntentFactory.create(context, args)
  }

  /** An intent that opens the item's conversation scrolled to its message. */
  suspend fun createConversationIntent(key: MediaOverviewItem.Key): Intent? {
    return withContext(SignalDispatchers.Default) {
      val record = getRecords(listOf(key)).firstOrNull() ?: return@withContext null
      val dateReceived = SignalDatabase.messages.getMessageRecordOrNull(record.messageId)?.dateReceived ?: record.date
      val position = SignalDatabase.messages.getMessagePositionInConversation(record.threadId, dateReceived)

      ConversationIntents.createBuilderSync(context, record.threadRecipientId, record.threadId)
        .withStartingPosition(maxOf(0, position))
        .build()
    }
  }

  suspend fun getSaveableMedia(keys: Collection<MediaOverviewItem.Key>): List<MediaOverviewAction.SaveableMedia> {
    return withContext(SignalDispatchers.Default) {
      getRecords(keys).mapNotNull { record ->
        val uri = record.attachment?.uri ?: return@mapNotNull null
        val contentType = record.contentType ?: return@mapNotNull null
        MediaOverviewAction.SaveableMedia(uri = uri, contentType = contentType, date = record.date, fileName = record.attachment.fileName)
      }
    }
  }

  private fun query(threadId: Long, tab: MediaOverviewState.Tab, sorting: MediaOverviewState.Sorting, limit: Int, offset: Int): Cursor {
    val media = SignalDatabase.media
    val mediaTableSorting = sorting.toMediaTableSorting()

    return when (tab) {
      MediaOverviewState.Tab.MEDIA -> media.getGalleryMediaForThread(threadId, mediaTableSorting, limit, offset)
      MediaOverviewState.Tab.FILES -> media.getDocumentMediaForThread(threadId, mediaTableSorting, limit, offset)
      MediaOverviewState.Tab.AUDIO -> media.getAudioMediaForThread(threadId, mediaTableSorting, limit, offset)
      MediaOverviewState.Tab.LINKS -> media.getLinkMediaForThread(threadId, mediaTableSorting, limit, offset)
      MediaOverviewState.Tab.ALL -> media.getAllMediaForThread(threadId, mediaTableSorting, limit, offset)
    }
  }

  private fun getRecords(keys: Collection<MediaOverviewItem.Key>): List<MediaTable.MediaRecord> {
    val attachmentIds = keys.filterIsInstance<MediaOverviewItem.Key.Attachment>().map { it.attachmentId }
    val messageIds = keys.filterIsInstance<MediaOverviewItem.Key.Message>().map { it.messageId }
    return SignalDatabase.media.getMediaRecords(attachmentIds, messageIds).distinctBy { it.toKey() }
  }

  private fun toItem(record: MediaTable.MediaRecord, recipients: Map<RecipientId, Recipient>, showArchiveHint: Boolean): MediaOverviewItem {
    val attachment = record.attachment
    val slide = attachment?.let { MediaUtil.getSlideForAttachment(it) }
    val link = record.linkPreview

    val kind = when {
      record.linkPreviewJson != null -> MediaOverviewItem.Kind.LINK
      slide == null -> MediaOverviewItem.Kind.DOCUMENT
      slide.hasAudio() -> MediaOverviewItem.Kind.AUDIO
      slide.hasVideo() -> MediaOverviewItem.Kind.VIDEO
      slide.hasImage() -> MediaOverviewItem.Kind.IMAGE
      else -> MediaOverviewItem.Kind.DOCUMENT
    }

    val sender = if (record.isOutgoing) Recipient.self() else recipients.getValue(record.recipientId)
    val thread = recipients.getValue(record.threadRecipientId)

    return MediaOverviewItem(
      key = record.toKey(),
      kind = kind,
      messageId = record.messageId,
      threadId = record.threadId,
      date = record.date,
      formattedDate = DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), record.date),
      size = attachment?.size ?: 0,
      contentType = record.contentType,
      uri = attachment?.uri,
      displayUri = attachment?.displayUri,
      blurHash = attachment?.blurHash,
      fileName = slide?.fileName?.orElse(null),
      fileExtension = if (kind == MediaOverviewItem.Kind.DOCUMENT) slide?.getFileType(context)?.orElse(null)?.lowercase() else null,
      transferState = attachment?.toTransferState() ?: MediaOverviewItem.TransferState.DONE,
      showPlayOverlay = slide != null && slide.uri != null && slide.hasPlayOverlay() && attachment?.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE,
      isOutgoing = record.isOutgoing,
      senderName = sender.getDisplayName(context),
      threadName = thread.getDisplayName(context),
      isThreadGroup = thread.isGroup,
      isNoteToSelf = sender.isSelf && thread.isSelf,
      linkUrl = link?.url,
      linkTitle = link?.title,
      archiveHint = if (showArchiveHint && (kind == MediaOverviewItem.Kind.AUDIO || kind == MediaOverviewItem.Kind.DOCUMENT)) attachment?.toArchiveHint() else null
    )
  }
}

internal fun MediaTable.MediaRecord.toKey(): MediaOverviewItem.Key {
  val attachment = attachment
  return if (attachment != null) {
    MediaOverviewItem.Key.Attachment(attachment.attachmentId)
  } else {
    MediaOverviewItem.Key.Message(messageId)
  }
}

internal fun MediaOverviewState.Sorting.toMediaTableSorting(): MediaTable.Sorting {
  return when (this) {
    MediaOverviewState.Sorting.NEWEST -> MediaTable.Sorting.Newest
    MediaOverviewState.Sorting.OLDEST -> MediaTable.Sorting.Oldest
    MediaOverviewState.Sorting.LARGEST -> MediaTable.Sorting.Largest
  }
}

private fun DatabaseAttachment.toTransferState(): MediaOverviewItem.TransferState {
  return when {
    isPermanentlyFailed -> MediaOverviewItem.TransferState.FAILED
    transferState == AttachmentTable.TRANSFER_PROGRESS_DONE -> MediaOverviewItem.TransferState.DONE
    transferState == AttachmentTable.TRANSFER_RESTORE_OFFLOADED -> MediaOverviewItem.TransferState.OFFLOADED
    else -> MediaOverviewItem.TransferState.IN_PROGRESS
  }
}

private fun DatabaseAttachment.toArchiveHint(): MediaOverviewItem.ArchiveHint {
  return when (archiveTransferState) {
    AttachmentTable.ArchiveTransferState.NONE -> MediaOverviewItem.ArchiveHint.NONE
    AttachmentTable.ArchiveTransferState.COPY_PENDING -> MediaOverviewItem.ArchiveHint.COPY_PENDING
    AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS -> MediaOverviewItem.ArchiveHint.UPLOAD_IN_PROGRESS
    AttachmentTable.ArchiveTransferState.FINISHED -> MediaOverviewItem.ArchiveHint.FINISHED
    AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE -> MediaOverviewItem.ArchiveHint.TEMPORARY_FAILURE
    AttachmentTable.ArchiveTransferState.PERMANENT_FAILURE -> MediaOverviewItem.ArchiveHint.PERMANENT_FAILURE
  }
}
