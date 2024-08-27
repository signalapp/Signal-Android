/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import android.text.TextUtils
import androidx.annotation.VisibleForTesting
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
import org.thoughtcrime.securesms.backup.v2.BackupRepository
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
import org.whispersystems.signalservice.api.backup.MediaName
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
  attachmentId: AttachmentId,
  private val manual: Boolean,
  private var forceArchiveDownload: Boolean
) : BaseJob(parameters) {

  companion object {
    const val KEY = "AttachmentDownloadJob"
    private val TAG = Log.tag(AttachmentDownloadJob::class.java)

    private const val KEY_MESSAGE_ID = "message_id"
    private const val KEY_ATTACHMENT_ID = "part_row_id"
    private const val KEY_MANUAL = "part_manual"
    private const val KEY_FORCE_ARCHIVE = "force_archive"

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
    fun downloadAttachmentIfNeeded(databaseAttachment: DatabaseAttachment): String? {
      if (databaseAttachment.transferState == AttachmentTable.TRANSFER_RESTORE_OFFLOADED) {
        return RestoreAttachmentJob.restoreOffloadedAttachment(databaseAttachment)
      } else if (databaseAttachment.transferState != AttachmentTable.TRANSFER_PROGRESS_STARTED &&
        databaseAttachment.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE &&
        databaseAttachment.transferState != AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE
      ) {
        val downloadJob = AttachmentDownloadJob(
          messageId = databaseAttachment.mmsId,
          attachmentId = databaseAttachment.attachmentId,
          manual = true,
          forceArchiveDownload = false
        )
        AppDependencies.jobManager.add(downloadJob)
        return downloadJob.id
      }
      return null
    }
  }

  private val attachmentId: Long

  constructor(messageId: Long, attachmentId: AttachmentId, manual: Boolean, forceArchiveDownload: Boolean = false) : this(
    Parameters.Builder()
      .setQueue(constructQueueString(attachmentId))
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    messageId,
    attachmentId,
    manual,
    forceArchiveDownload
  )

  init {
    this.attachmentId = attachmentId.id
  }

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(KEY_MESSAGE_ID, messageId)
      .putLong(KEY_ATTACHMENT_ID, attachmentId)
      .putBoolean(KEY_MANUAL, manual)
      .putBoolean(KEY_FORCE_ARCHIVE, forceArchiveDownload)
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onAdded() {
    Log.i(TAG, "onAdded() messageId: $messageId  attachmentId: $attachmentId  manual: $manual")

    val attachmentId = AttachmentId(attachmentId)
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

    val attachmentId = AttachmentId(attachmentId)
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

    Log.i(TAG, "Downloading push part $attachmentId")
    SignalDatabase.attachments.setTransferState(messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED)

    when (attachment.cdn) {
      Cdn.S3 -> retrieveAttachmentForReleaseChannel(messageId, attachmentId, attachment)
      else -> retrieveAttachment(messageId, attachmentId, attachment)
    }

    if ((attachment.cdn == Cdn.CDN_2 || attachment.cdn == Cdn.CDN_3) &&
      attachment.archiveMediaId == null &&
      SignalStore.backup.backsUpMedia
    ) {
      AppDependencies.jobManager.add(ArchiveAttachmentJob(attachmentId))
    }
  }

  override fun onFailure() {
    Log.w(TAG, format(this, "onFailure() messageId: $messageId  attachmentId: $attachmentId  manual: $manual"))

    val attachmentId = AttachmentId(attachmentId)
    markFailed(messageId, attachmentId)
  }

  override fun onShouldRetry(exception: Exception): Boolean {
    return exception is PushNetworkException ||
      exception is RetryLaterException
  }

  @Throws(IOException::class, RetryLaterException::class)
  private fun retrieveAttachment(
    messageId: Long,
    attachmentId: AttachmentId,
    attachment: DatabaseAttachment
  ) {
    val maxReceiveSize: Long = RemoteConfig.maxAttachmentReceiveSizeBytes
    val attachmentFile: File = SignalDatabase.attachments.getOrCreateTransferFile(attachmentId)
    var archiveFile: File? = null
    var useArchiveCdn = false

    try {
      if (attachment.size > maxReceiveSize) {
        throw MmsException("Attachment too large, failing download")
      }

      useArchiveCdn = if (SignalStore.backup.backsUpMedia && (forceArchiveDownload || attachment.remoteLocation == null)) {
        if (attachment.archiveMediaName.isNullOrEmpty()) {
          throw InvalidPartException("Invalid attachment configuration")
        }
        true
      } else {
        false
      }

      val messageReceiver = AppDependencies.signalServiceMessageReceiver
      val pointer = createAttachmentPointer(attachment, useArchiveCdn)

      val progressListener = object : SignalServiceAttachment.ProgressListener {
        override fun onAttachmentProgress(total: Long, progress: Long) {
          EventBus.getDefault().postSticky(PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, total, progress))
        }

        override fun shouldCancel(): Boolean {
          return this@AttachmentDownloadJob.isCanceled
        }
      }

      val stream = if (useArchiveCdn) {
        archiveFile = SignalDatabase.attachments.getOrCreateArchiveTransferFile(attachmentId)
        val cdnCredentials = BackupRepository.getCdnReadCredentials(attachment.archiveCdn).successOrThrow().headers

        messageReceiver
          .retrieveArchivedAttachment(
            SignalStore.svr.getOrCreateMasterKey().deriveBackupKey().deriveMediaSecrets(MediaName(attachment.archiveMediaName!!)),
            cdnCredentials,
            archiveFile,
            pointer,
            attachmentFile,
            maxReceiveSize,
            false,
            progressListener
          )
      } else {
        messageReceiver
          .retrieveAttachment(
            pointer,
            attachmentFile,
            maxReceiveSize,
            progressListener
          )
      }

      SignalDatabase.attachments.finalizeAttachmentAfterDownload(messageId, attachmentId, stream)
    } catch (e: RangeException) {
      val transferFile = archiveFile ?: attachmentFile
      Log.w(TAG, "Range exception, file size " + transferFile.length(), e)
      if (transferFile.delete()) {
        Log.i(TAG, "Deleted temp download file to recover")
        throw RetryLaterException(e)
      } else {
        throw IOException("Failed to delete temp download file following range exception")
      }
    } catch (e: InvalidPartException) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    } catch (e: NonSuccessfulResponseCodeException) {
      if (SignalStore.backup.backsUpMedia) {
        if (e.code == 404 && !useArchiveCdn && attachment.archiveMediaName?.isNotEmpty() == true) {
          Log.i(TAG, "Retrying download from archive CDN")
          forceArchiveDownload = true
          retrieveAttachment(messageId, attachmentId, attachment)
          return
        } else if (e.code == 401 && useArchiveCdn) {
          SignalStore.backup.cdnReadCredentials = null
          throw RetryLaterException(e)
        }
      }

      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    } catch (e: MmsException) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    } catch (e: MissingConfigurationException) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    } catch (e: InvalidMessageException) {
      Log.w(TAG, "Experienced an InvalidMessageException while trying to download an attachment.", e)
      if (e.cause is InvalidMacException) {
        Log.w(TAG, "Detected an invalid mac. Treating as a permanent failure.")
        markPermanentlyFailed(messageId, attachmentId)
      } else {
        markFailed(messageId, attachmentId)
      }
    }
  }

  @Throws(InvalidPartException::class)
  private fun createAttachmentPointer(attachment: DatabaseAttachment, useArchiveCdn: Boolean): SignalServiceAttachmentPointer {
    if (TextUtils.isEmpty(attachment.remoteKey)) {
      throw InvalidPartException("empty encrypted key")
    }

    return try {
      val remoteData: RemoteData = if (useArchiveCdn) {
        val backupKey = SignalStore.svr.getOrCreateMasterKey().deriveBackupKey()
        val backupDirectories = BackupRepository.getCdnBackupDirectories().successOrThrow()

        RemoteData(
          remoteId = SignalServiceAttachmentRemoteId.Backup(
            backupDir = backupDirectories.backupDir,
            mediaDir = backupDirectories.mediaDir,
            mediaId = backupKey.deriveMediaId(MediaName(attachment.archiveMediaName!!)).encode()
          ),
          cdnNumber = attachment.archiveCdn
        )
      } else {
        if (attachment.remoteLocation.isNullOrEmpty()) {
          throw InvalidPartException("empty content id")
        }

        RemoteData(
          remoteId = SignalServiceAttachmentRemoteId.from(attachment.remoteLocation),
          cdnNumber = attachment.cdn.cdnNumber
        )
      }

      val key = Base64.decode(attachment.remoteKey!!)

      if (attachment.remoteDigest != null) {
        Log.i(TAG, "Downloading attachment with digest: " + Hex.toString(attachment.remoteDigest))
      } else {
        throw InvalidPartException("Null remote digest for $attachmentId")
      }

      SignalServiceAttachmentPointer(
        remoteData.cdnNumber,
        remoteData.remoteId,
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
      throw InvalidPartException(e)
    } catch (e: ArithmeticException) {
      Log.w(TAG, e)
      throw InvalidPartException(e)
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
        if (body != null) {
          if (body.contentLength() > RemoteConfig.maxAttachmentReceiveSizeBytes) {
            throw MmsException("Attachment too large, failing download")
          }
          SignalDatabase.attachments.finalizeAttachmentAfterDownload(messageId, attachmentId, (body.source() as Source).buffer().inputStream())
        }
      }
    } catch (e: MmsException) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(messageId, attachmentId)
    }
  }

  private fun markFailed(messageId: Long, attachmentId: AttachmentId) {
    try {
      SignalDatabase.attachments.setTransferProgressFailed(attachmentId, messageId)
    } catch (e: MmsException) {
      Log.w(TAG, e)
    }
  }

  private fun markPermanentlyFailed(messageId: Long, attachmentId: AttachmentId) {
    try {
      SignalDatabase.attachments.setTransferProgressPermanentFailure(attachmentId, messageId)
    } catch (e: MmsException) {
      Log.w(TAG, e)
    }
  }

  @VisibleForTesting
  internal class InvalidPartException : Exception {
    constructor(s: String?) : super(s)
    constructor(e: Exception?) : super(e)
  }

  private data class RemoteData(val remoteId: SignalServiceAttachmentRemoteId, val cdnNumber: Int)

  class Factory : Job.Factory<AttachmentDownloadJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AttachmentDownloadJob {
      val data = JsonJobData.deserialize(serializedData)
      return AttachmentDownloadJob(
        parameters = parameters,
        messageId = data.getLong(KEY_MESSAGE_ID),
        attachmentId = AttachmentId(data.getLong(KEY_ATTACHMENT_ID)),
        manual = data.getBoolean(KEY_MANUAL),
        forceArchiveDownload = data.getBooleanOrDefault(KEY_FORCE_ARCHIVE, false)
      )
    }
  }
}
