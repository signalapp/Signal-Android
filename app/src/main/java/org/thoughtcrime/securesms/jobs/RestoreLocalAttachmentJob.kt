/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import android.net.Uri
import org.signal.core.util.Base64
import org.signal.core.util.androidx.DocumentFileInfo
import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.signal.libsignal.protocol.InvalidMacException
import org.signal.libsignal.protocol.InvalidMessageException
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.AttachmentTable.LocalRestorableAttachment
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.protos.RestoreLocalAttachmentJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.MmsException
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream.StreamSupplier
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Restore attachment from local backup storage.
 */
class RestoreLocalAttachmentJob private constructor(
  parameters: Parameters,
  private val attachmentId: AttachmentId,
  private val messageId: Long,
  private val restoreUri: Uri,
  private val size: Long
) : Job(parameters) {

  companion object {
    const val KEY = "RestoreLocalAttachmentJob"
    val TAG = Log.tag(RestoreLocalAttachmentJob::class.java)

    fun enqueueRestoreLocalAttachmentsJobs(mediaNameToFileInfo: Map<String, DocumentFileInfo>) {
      var restoreAttachmentJobs: MutableList<Job>

      do {
        val possibleRestorableAttachments: List<LocalRestorableAttachment> = SignalDatabase.attachments.getLocalRestorableAttachments(500)
        val restorableAttachments = ArrayList<LocalRestorableAttachment>(possibleRestorableAttachments.size)
        val notRestorableAttachments = ArrayList<LocalRestorableAttachment>(possibleRestorableAttachments.size)

        restoreAttachmentJobs = ArrayList(possibleRestorableAttachments.size)

        possibleRestorableAttachments
          .forEachIndexed { index, attachment ->
            val fileInfo = if (attachment.remoteKey != null && attachment.remoteDigest != null) {
              val mediaName = MediaName.fromDigest(attachment.remoteDigest).name
              mediaNameToFileInfo[mediaName]
            } else {
              null
            }

            if (fileInfo != null) {
              restorableAttachments += attachment
              restoreAttachmentJobs += RestoreLocalAttachmentJob("RestoreLocalAttachmentJob_${index % 2}", attachment, fileInfo)
            } else {
              notRestorableAttachments += attachment
            }
          }

        SignalDatabase.rawDatabase.withinTransaction {
          SignalDatabase.attachments.setRestoreInProgressTransferState(restorableAttachments)
          SignalDatabase.attachments.setRestoreFailedTransferState(notRestorableAttachments)

          SignalStore.backup.totalRestorableAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize()
          AppDependencies.jobManager.addAll(restoreAttachmentJobs)
        }
      } while (restoreAttachmentJobs.isNotEmpty())
    }
  }

  private constructor(queue: String, attachment: LocalRestorableAttachment, info: DocumentFileInfo) : this(
    Parameters.Builder()
      .setQueue(queue)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    attachmentId = attachment.attachmentId,
    messageId = attachment.mmsId,
    restoreUri = info.documentFile.uri,
    size = info.size
  )

  override fun serialize(): ByteArray? {
    return RestoreLocalAttachmentJobData(
      attachmentId = attachmentId.id,
      messageId = messageId,
      fileUri = restoreUri.toString(),
      fileSize = size
    ).encode()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    Log.i(TAG, "onRun() messageId: $messageId  attachmentId: $attachmentId")

    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)

    if (attachment == null) {
      Log.w(TAG, "attachment no longer exists.")
      return Result.failure()
    }

    if (attachment.remoteDigest == null || attachment.remoteKey == null) {
      Log.w(TAG, "Attachment no longer has a remote digest or key")
      return Result.failure()
    }

    if (attachment.isPermanentlyFailed) {
      Log.w(TAG, "Attachment was marked as a permanent failure. Refusing to download.")
      return Result.failure()
    }

    if (attachment.transferState != AttachmentTable.TRANSFER_NEEDS_RESTORE && attachment.transferState != AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS) {
      Log.w(TAG, "Attachment does not need to be restored.")
      return Result.success()
    }

    val combinedKey = Base64.decode(attachment.remoteKey)
    val streamSupplier = StreamSupplier { ArchiveFileSystem.openInputStream(context, restoreUri) ?: throw IOException("Unable to open stream") }

    try {
      // TODO [local-backup] actually verify mac and save iv
      AttachmentCipherInputStream.createForAttachment(streamSupplier, size, attachment.size, combinedKey, null, null, 0, true).use { input ->
        SignalDatabase.attachments.finalizeAttachmentAfterDownload(attachment.mmsId, attachment.attachmentId, input, null)
      }
    } catch (e: InvalidMessageException) {
      Log.w(TAG, "Experienced an InvalidMessageException while trying to read attachment.", e)
      if (e.cause is InvalidMacException) {
        Log.w(TAG, "Detected an invalid mac. Treating as a permanent failure.")
        markPermanentlyFailed(messageId, attachmentId)
      }
      return Result.failure()
    } catch (e: MmsException) {
      Log.w(TAG, "Experienced exception while trying to store attachment.", e)
      return Result.failure()
    } catch (e: IOException) {
      Log.w(TAG, "Experienced an exception while trying to read attachment.", e)
      return Result.retry(defaultBackoff())
    }

    return Result.success()
  }

  override fun onFailure() {
    markFailed(messageId, attachmentId)
    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
  }

  private fun markFailed(messageId: Long, attachmentId: AttachmentId) {
    SignalDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)
  }

  private fun markPermanentlyFailed(messageId: Long, attachmentId: AttachmentId) {
    SignalDatabase.attachments.setTransferProgressPermanentFailure(attachmentId, messageId)
  }

  class Factory : Job.Factory<RestoreLocalAttachmentJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RestoreLocalAttachmentJob {
      val data = RestoreLocalAttachmentJobData.ADAPTER.decode(serializedData!!)
      return RestoreLocalAttachmentJob(
        parameters = parameters,
        attachmentId = AttachmentId(data.attachmentId),
        messageId = data.messageId,
        restoreUri = Uri.parse(data.fileUri),
        size = data.fileSize
      )
    }
  }
}
