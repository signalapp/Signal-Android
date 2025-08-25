/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import androidx.annotation.MainThread
import okio.Source
import okio.buffer
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidMacException
import org.signal.libsignal.protocol.InvalidMessageException
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.InvalidAttachmentException
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobLogger.format
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.notifications.v2.ConversationId.Companion.forConversation
import org.thoughtcrime.securesms.s3.S3
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.thoughtcrime.securesms.util.AttachmentUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream.IntegrityCheck
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.push.exceptions.RangeException
import java.io.File
import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Download attachment from locations as specified in their record.
 */
class AttachmentDownloadJob private constructor(
  parameters: Parameters,
  private val messageId: Long,
  private val attachmentId: AttachmentId,
  private val manual: Boolean
) : BaseJob(parameters) {

  companion object {
    const val KEY = "AttachmentDownloadJob"
    private val TAG = Log.tag(AttachmentDownloadJob::class.java)

    private const val KEY_MESSAGE_ID = "message_id"
    private const val KEY_ATTACHMENT_ID = "part_row_id"
    private const val KEY_MANUAL = "part_manual"

    @JvmStatic
    fun constructQueueString(attachmentId: AttachmentId): String {
      return "AttachmentDownloadJob-" + attachmentId.id
    }

    fun jobSpecMatchesAttachmentId(jobSpec: JobSpec, attachmentId: AttachmentId): Boolean {
      if (KEY != jobSpec.factoryKey) {
        return false
      }

      val serializedData = jobSpec.serializedData ?: return false
      val data = JsonJobData.deserialize(serializedData)
      val parsed = AttachmentId(data.getLong(KEY_ATTACHMENT_ID))
      return attachmentId == parsed
    }

    @JvmStatic
    @MainThread
    fun downloadAttachmentIfNeeded(databaseAttachment: DatabaseAttachment): String? {
      return when (val transferState = databaseAttachment.transferState) {
        AttachmentTable.TRANSFER_PROGRESS_DONE -> null

        AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS,
        AttachmentTable.TRANSFER_RESTORE_OFFLOADED,
        AttachmentTable.TRANSFER_NEEDS_RESTORE -> RestoreAttachmentJob.forManualRestore(databaseAttachment)

        AttachmentTable.TRANSFER_PROGRESS_PENDING,
        AttachmentTable.TRANSFER_PROGRESS_FAILED -> {
          if (SignalStore.backup.backsUpMedia && (databaseAttachment.remoteLocation == null || databaseAttachment.remoteDigest == null)) {
            if (databaseAttachment.archiveTransferState == AttachmentTable.ArchiveTransferState.FINISHED) {
              Log.i(TAG, "Trying to restore attachment from archive cdn")
              RestoreAttachmentJob.forManualRestore(databaseAttachment)
            } else {
              Log.w(TAG, "No remote location, and the archive transfer state is unfinished. Can't download.")
              null
            }
          } else {
            val downloadJob = AttachmentDownloadJob(
              messageId = databaseAttachment.mmsId,
              attachmentId = databaseAttachment.attachmentId,
              manual = true
            )
            AppDependencies.jobManager.add(downloadJob)
            downloadJob.id
          }
        }

        AttachmentTable.TRANSFER_PROGRESS_STARTED,
        AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE -> {
          Log.d(TAG, "${databaseAttachment.attachmentId} is downloading or permanently failed, transferState: $transferState")
          null
        }

        else -> {
          Log.w(TAG, "Attempted to download attachment with unknown transfer state: $transferState")
          null
        }
      }
    }
  }

  constructor(messageId: Long, attachmentId: AttachmentId, manual: Boolean) : this(
    Parameters.Builder()
      .setQueue(constructQueueString(attachmentId))
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    messageId,
    attachmentId,
    manual
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(KEY_MESSAGE_ID, messageId)
      .putLong(KEY_ATTACHMENT_ID, attachmentId.id)
      .putBoolean(KEY_MANUAL, manual)
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onAdded() {
    Log.i(TAG, "onAdded() messageId: $messageId  attachmentId: $attachmentId  manual: $manual")

    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)
    val pending = attachment != null && attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE && attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE

    if (pending && (manual || AttachmentUtil.isAutoDownloadPermitted(context, attachment))) {
      Log.i(TAG, "onAdded() Marking attachment progress as 'started'")
      SignalDatabase.attachments.setTransferState(messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED)
    }
  }

  @Throws(Exception::class)
  public override fun onRun() {
    doWork()

    if (!SignalDatabase.messages.isStory(messageId)) {
      AppDependencies.messageNotifier.updateNotification(context, forConversation(0))
    }
  }

  @Throws(IOException::class, RetryLaterException::class)
  fun doWork() {
    Log.i(TAG, "onRun() messageId: $messageId  attachmentId: $attachmentId  manual: $manual")

    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)

    if (attachment == null) {
      Log.w(TAG, "attachment no longer exists.")
      return
    }

    if (attachment.isPermanentlyFailed) {
      Log.w(TAG, "Attachment was marked as a permanent failure. Refusing to download.")
      return
    }

    if (!attachment.isInProgress) {
      Log.w(TAG, "Attachment was already downloaded.")
      return
    }

    if (!manual && !AttachmentUtil.isAutoDownloadPermitted(context, attachment)) {
      Log.w(TAG, "Attachment can't be auto downloaded...")
      SignalDatabase.attachments.setTransferState(messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_PENDING)
      return
    }

    if (SignalStore.backup.backsUpMedia && attachment.remoteLocation == null) {
      if (attachment.archiveTransferState != AttachmentTable.ArchiveTransferState.FINISHED) {
        throw InvalidAttachmentException("No remote location, and the archive transfer state is unfinished. Can't download.")
      }

      Log.i(TAG, "Trying to restore attachment from archive cdn instead")
      RestoreAttachmentJob.forManualRestore(attachment)

      return
    }

    Log.i(TAG, "Downloading push part $attachmentId")
    SignalDatabase.attachments.setTransferState(messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED)

    when (attachment.cdn) {
      Cdn.S3 -> retrieveAttachmentForReleaseChannel(messageId, attachmentId, attachment)
      else -> retrieveAttachment(messageId, attachmentId, attachment)
    }

    if (SignalStore.backup.backsUpMedia) {
      val isStory = SignalDatabase.messages.isStory(messageId)
      when {
        attachment.archiveTransferState == AttachmentTable.ArchiveTransferState.FINISHED -> {
          Log.i(TAG, "[$attachmentId] Already archived. Skipping.")
        }

        attachment.cdn !in CopyAttachmentToArchiveJob.ALLOWED_SOURCE_CDNS -> {
          Log.i(TAG, "[$attachmentId] Attachment CDN doesn't support copying to archive. Re-uploading to archive.")
          AppDependencies.jobManager.add(UploadAttachmentToArchiveJob(attachmentId))
        }

        isStory -> {
          Log.i(TAG, "[$attachmentId] Attachment is a story. Skipping.")
        }

        SignalDatabase.messages.isViewOnce(messageId) -> {
          Log.i(TAG, "[$attachmentId] View-once. Skipping.")
        }

        SignalDatabase.messages.willMessageExpireBeforeCutoff(messageId) -> {
          Log.i(TAG, "[$attachmentId] Message will expire within 24hrs. Skipping.")
        }

        SignalStore.account.isLinkedDevice -> {
          Log.i(TAG, "[$attachmentId] Linked device. Skipping.")
        }

        else -> {
          Log.i(TAG, "[$attachmentId] Enqueuing job to copy to archive.")
          AppDependencies.jobManager.add(CopyAttachmentToArchiveJob(attachmentId))
        }
      }
    }
  }

  override fun onFailure() {
    Log.w(TAG, format(this, "onFailure() messageId: $messageId  attachmentId: $attachmentId  manual: $manual"))

    markFailed(messageId, attachmentId)
  }

  override fun onShouldRetry(exception: Exception): Boolean {
    return exception is PushNetworkException ||
      exception is RetryLaterException
  }

  /**
   * @return True if the digest changed as part of downloading, otherwise false.
   */
  @Throws(IOException::class, RetryLaterException::class)
  private fun retrieveAttachment(
    messageId: Long,
    attachmentId: AttachmentId,
    attachment: DatabaseAttachment
  ) {
    val maxReceiveSize: Long = RemoteConfig.maxAttachmentReceiveSizeBytes
    val attachmentFile: File = SignalDatabase.attachments.getOrCreateTransferFile(attachmentId)

    try {
      if (attachment.size > maxReceiveSize) {
        throw MmsException("[$attachmentId] Attachment too large, failing download")
      }

      val pointer = createAttachmentPointer(attachment)

      val progressListener = object : SignalServiceAttachment.ProgressListener {
        override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
          EventBus.getDefault().postSticky(PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, progress))
        }

        override fun shouldCancel(): Boolean {
          return this@AttachmentDownloadJob.isCanceled
        }
      }

      if (attachment.remoteDigest == null && attachment.dataHash == null) {
        Log.w(TAG, "[$attachmentId] Attachment has no integrity check!")
        throw InvalidAttachmentException("Attachment has no integrity check!")
      }

      val decryptingStream = AppDependencies
        .signalServiceMessageReceiver
        .retrieveAttachment(
          pointer,
          attachmentFile,
          maxReceiveSize,
          IntegrityCheck.forEncryptedDigestAndPlaintextHash(attachment.remoteDigest, attachment.dataHash),
          progressListener
        )

      decryptingStream.use { input ->
        SignalDatabase.attachments.finalizeAttachmentAfterDownload(messageId, attachmentId, input)
      }
    } catch (e: RangeException) {
      Log.w(TAG, "[$attachmentId] Range exception, file size " + attachmentFile.length(), e)
      if (attachmentFile.delete()) {
        Log.i(TAG, "[$attachmentId] Deleted temp download file to recover")
        throw RetryLaterException(e)
      } else {
        throw IOException("[$attachmentId] Failed to delete temp download file following range exception")
      }
    } catch (e: InvalidAttachmentException) {
      Log.w(TAG, "[$attachmentId] Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    } catch (e: NonSuccessfulResponseCodeException) {
      if (SignalStore.backup.backsUpMedia && e.code == 404 && attachment.archiveTransferState === AttachmentTable.ArchiveTransferState.FINISHED) {
        Log.i(TAG, "[$attachmentId] Retrying download from archive CDN")
        RestoreAttachmentJob.forManualRestore(attachment)
        return
      }

      Log.w(TAG, "[$attachmentId] Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    } catch (e: MmsException) {
      Log.w(TAG, "[$attachmentId] Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    } catch (e: MissingConfigurationException) {
      Log.w(TAG, "[$attachmentId] Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    } catch (e: InvalidMessageException) {
      Log.w(TAG, "[$attachmentId] Experienced an InvalidMessageException while trying to download an attachment.", e)
      if (e.cause is InvalidMacException) {
        Log.w(TAG, "[$attachmentId] Detected an invalid mac. Treating as a permanent failure.")
        markPermanentlyFailed(messageId, attachmentId)
      } else {
        markFailed(messageId, attachmentId)
      }
    } catch (e: org.signal.libsignal.protocol.incrementalmac.InvalidMacException) {
      Log.w(TAG, "[$attachmentId] Detected an invalid incremental mac. Clearing and marking as a temporary failure, requiring the user to manually try again.")
      SignalDatabase.attachments.clearIncrementalMacsForAttachmentAndAnyDuplicates(attachmentId, attachment.remoteKey, attachment.dataHash)
      markFailed(messageId, attachmentId)
    }
  }

  @Throws(InvalidAttachmentException::class)
  private fun createAttachmentPointer(attachment: DatabaseAttachment): SignalServiceAttachmentPointer {
    if (attachment.remoteKey.isNullOrEmpty()) {
      throw InvalidAttachmentException("empty encrypted key")
    }

    if (attachment.remoteLocation.isNullOrEmpty()) {
      throw InvalidAttachmentException("empty content id")
    }

    return try {
      val remoteId = SignalServiceAttachmentRemoteId.from(attachment.remoteLocation)
      val cdnNumber = attachment.cdn.cdnNumber

      val key = Base64.decode(attachment.remoteKey)

      if (attachment.remoteDigest != null) {
        Log.i(TAG, "Downloading attachment with digest: " + Hex.toString(attachment.remoteDigest))
      } else {
        throw InvalidAttachmentException("Null remote digest for $attachmentId")
      }

      SignalServiceAttachmentPointer(
        cdnNumber,
        remoteId,
        null,
        key,
        Optional.of(Util.toIntExact(attachment.size)),
        Optional.empty(),
        0,
        0,
        Optional.ofNullable(attachment.remoteDigest),
        Optional.ofNullable(attachment.getIncrementalDigest()),
        attachment.incrementalMacChunkSize,
        Optional.ofNullable(attachment.fileName),
        attachment.voiceNote,
        attachment.borderless,
        attachment.videoGif,
        Optional.empty(),
        Optional.ofNullable(attachment.blurHash).map { it.hash },
        attachment.uploadTimestamp,
        attachment.uuid
      )
    } catch (e: IOException) {
      Log.w(TAG, e)
      throw InvalidAttachmentException(e)
    } catch (e: ArithmeticException) {
      Log.w(TAG, e)
      throw InvalidAttachmentException(e)
    }
  }

  @Throws(IOException::class)
  private fun retrieveAttachmentForReleaseChannel(
    messageId: Long,
    attachmentId: AttachmentId,
    attachment: Attachment
  ) {
    try {
      S3.getObject(attachment.fileName!!).use { response ->
        val body = response.body
        if (body.contentLength() > RemoteConfig.maxAttachmentReceiveSizeBytes) {
          throw MmsException("Attachment too large, failing download")
        }

        SignalDatabase.attachments.createRemoteKeyIfNecessary(attachmentId)

        SignalDatabase.attachments.finalizeAttachmentAfterDownload(
          messageId,
          attachmentId,
          (body.source() as Source).buffer().inputStream()
        )
      }
    } catch (e: MmsException) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    }
  }

  private fun markFailed(messageId: Long, attachmentId: AttachmentId) {
    SignalDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)
  }

  private fun markPermanentlyFailed(messageId: Long, attachmentId: AttachmentId) {
    SignalDatabase.attachments.setTransferProgressPermanentFailure(attachmentId, messageId)
  }

  class Factory : Job.Factory<AttachmentDownloadJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AttachmentDownloadJob {
      val data = JsonJobData.deserialize(serializedData)
      return AttachmentDownloadJob(
        parameters = parameters,
        messageId = data.getLong(KEY_MESSAGE_ID),
        attachmentId = AttachmentId(data.getLong(KEY_ATTACHMENT_ID)),
        manual = data.getBoolean(KEY_MANUAL)
      )
    }
  }
}
