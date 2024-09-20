/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import android.net.Uri
import org.signal.core.util.Base64
import org.signal.core.util.StreamUtil
import org.signal.core.util.androidx.DocumentFileInfo
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidMacException
import org.signal.libsignal.protocol.InvalidMessageException
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.AttachmentTable.RestorableAttachment
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
    private const val CONCURRENT_QUEUES = 2

    fun enqueueRestoreLocalAttachmentsJobs(mediaNameToFileInfo: Map<String, DocumentFileInfo>) {
      val jobManager = AppDependencies.jobManager

      do {
        val possibleRestorableAttachments: List<RestorableAttachment> = SignalDatabase.attachments.getRestorableAttachments(500)
        val notRestorableAttachments = ArrayList<AttachmentId>(possibleRestorableAttachments.size)
        val restoreAttachmentJobs: MutableList<Job> = ArrayList(possibleRestorableAttachments.size)

        possibleRestorableAttachments
          .forEachIndexed { index, attachment ->
            val fileInfo = if (attachment.remoteKey != null && attachment.remoteDigest != null) {
              val mediaName = MediaName.fromDigest(attachment.remoteDigest).name
              mediaNameToFileInfo[mediaName]
            } else {
              null
            }

            if (fileInfo != null) {
              restoreAttachmentJobs += RestoreLocalAttachmentJob(queueName(index), attachment, fileInfo)
            } else {
              notRestorableAttachments += attachment.attachmentId
            }
          }

        // Mark not restorable attachments as failed
        SignalDatabase.attachments.setRestoreTransferState(notRestorableAttachments, AttachmentTable.TRANSFER_PROGRESS_FAILED)

        // Intentionally enqueues one at a time for safer attachment transfer state management
        restoreAttachmentJobs.forEach { jobManager.add(it) }
      } while (restoreAttachmentJobs.isNotEmpty())

      SignalStore.backup.totalRestorableAttachmentSize = SignalDatabase.attachments.getRemainingRestorableAttachmentSize()

      val checkDoneJobs = (0 until CONCURRENT_QUEUES)
        .map {
          CheckRestoreMediaLeftJob(queueName(it))
        }

      AppDependencies.jobManager.addAll(checkDoneJobs)
    }

    private fun queueName(index: Int): String {
      return "RestoreLocalAttachmentJob_${index % CONCURRENT_QUEUES}"
    }
  }

  private constructor(queue: String, attachment: RestorableAttachment, info: DocumentFileInfo) : this(
    Parameters.Builder()
      .setQueue(queue)
      .setLifespan(Parameters.IMMORTAL)
      .setMaxAttempts(3)
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
      val iv = ByteArray(16)
      streamSupplier.openStream().use { StreamUtil.readFully(it, iv) }
      AttachmentCipherInputStream.createForAttachment(streamSupplier, size, attachment.size, combinedKey, attachment.remoteDigest, null, 0, false).use { input ->
        SignalDatabase.attachments.finalizeAttachmentAfterDownload(attachment.mmsId, attachment.attachmentId, input, iv)
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
