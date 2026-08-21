/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messagedetails

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.signal.core.models.backup.MediaName
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.network.util.JsonUtil
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobs.ArchiveCommitAttachmentDeletesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId

class InternalMessageDetailsViewModel(val messageId: Long) : ViewModel() {

  companion object {
    private val TAG = Log.tag(InternalMessageDetailsViewModel::class)
  }

  private val _state: MutableState<ViewState?> = mutableStateOf(null)
  val state: State<ViewState?> = _state

  private val _actionResult: MutableState<String?> = mutableStateOf(null)
  val actionResult: State<String?> = _actionResult

  init {
    refresh()
  }

  fun consumeActionResult() {
    _actionResult.value = null
  }

  /**
   * Puts the attachment into the offloaded state, so the media is only on the archive CDN.
   */
  fun offloadLocalData(attachmentId: Long) {
    runAction("Offloaded local data") {
      SignalDatabase.attachments.debugOffloadAttachment(AttachmentId(attachmentId))
    }
  }

  /**
   * Deletes the archive CDN copy while leaving every local record alone, which is the state a reconciliation is meant to detect and repair.
   */
  fun deleteFromCdn(attachmentId: Long, thumbnail: Boolean) {
    val label = if (thumbnail) "Deleted thumbnail from CDN" else "Deleted full-size from CDN"

    runAction(label) {
      val attachment = SignalDatabase.attachments.getAttachment(AttachmentId(attachmentId)) ?: error("Attachment is gone")
      val plaintextHash = attachment.dataHash?.let { Base64.decode(it) } ?: error("No plaintext hash")
      val remoteKey = attachment.remoteKey?.let { Base64.decode(it) } ?: error("No remote key")
      val cdn = attachment.archiveCdn ?: error("No archive CDN, so nothing is up there")

      val mediaName = if (thumbnail) {
        MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(plaintextHash, remoteKey)
      } else {
        MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey)
      }

      val mediaObject = ArchivedMediaObject(
        mediaId = mediaName.toMediaId(SignalStore.backup.mediaRootBackupKey).encode(),
        cdn = cdn
      )

      val failure = ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(
        tag = TAG,
        attachmentsToDelete = setOf(mediaObject),
        backoffGenerator = { 0 },
        cancellationSignal = { false }
      )

      if (failure != null) {
        error("CDN delete did not succeed: $failure")
      }
    }
  }

  private fun runAction(successLabel: String, action: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      _actionResult.value = try {
        action()
        successLabel
      } catch (e: Exception) {
        Log.w(TAG, "Internal attachment action failed.", e)
        "Failed: ${e.message}"
      }

      loadState()
    }
  }

  private fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      loadState()
    }
  }

  private fun loadState() {
    val messageRecord = SignalDatabase.messages.getMessageRecord(messageId)
    val attachments = SignalDatabase.attachments.getAttachmentsForMessage(messageId)

    _state.value = ViewState(
      id = messageRecord.id,
      sentTimestamp = messageRecord.dateSent,
      receivedTimestamp = messageRecord.dateReceived,
      serverSentTimestamp = messageRecord.serverTimestamp,
      from = messageRecord.fromRecipient.id,
      to = messageRecord.toRecipient.id,
      attachments = attachments.map { attachment ->
        val info = SignalDatabase.attachments.getDataFileInfo(attachment.attachmentId)

        AttachmentInfo(
          id = attachment.attachmentId.id,
          contentType = attachment.contentType,
          quoteTargetContentType = attachment.quoteTargetContentType,
          size = attachment.size,
          fileName = attachment.fileName,
          hashStart = info?.hashStart,
          hashEnd = info?.hashEnd,
          transformProperties = info?.transformProperties?.let { JsonUtil.toJson(it) } ?: "null",
          hasLocalData = attachment.hasData,
          transferState = attachment.transferState,
          archiveCdn = attachment.archiveCdn,
          archiveTransferState = attachment.archiveTransferState,
          archiveThumbnailTransferState = attachment.archiveThumbnailTransferState
        )
      }
    )
  }

  data class ViewState(
    val id: Long,
    val sentTimestamp: Long,
    val receivedTimestamp: Long,
    val serverSentTimestamp: Long,
    val from: RecipientId,
    val to: RecipientId,
    val attachments: List<AttachmentInfo>
  )

  data class AttachmentInfo(
    val id: Long,
    val contentType: String?,
    val quoteTargetContentType: String?,
    val size: Long,
    val fileName: String?,
    val hashStart: String?,
    val hashEnd: String?,
    val transformProperties: String?,
    val hasLocalData: Boolean,
    val transferState: Int,
    val archiveCdn: Int?,
    val archiveTransferState: AttachmentTable.ArchiveTransferState,
    val archiveThumbnailTransferState: AttachmentTable.ArchiveTransferState
  )
}
