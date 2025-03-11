/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidMacException
import org.signal.libsignal.protocol.InvalidMessageException
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.InvalidAttachmentException
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.database.createArchiveAttachmentPointer
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobLogger.format
import org.thoughtcrime.securesms.jobmanager.impl.BatteryNotLowConstraint
import org.thoughtcrime.securesms.jobmanager.impl.RestoreAttachmentConstraint
import org.thoughtcrime.securesms.jobs.protos.RestoreAttachmentJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.push.exceptions.RangeException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Download attachment from locations as specified in their record.
 */
class RestoreAttachmentJob private constructor(
  parameters: Parameters,
  private val messageId: Long,
  private val attachmentId: AttachmentId,
  private val manual: Boolean
) : BaseJob(parameters) {

  companion object {
    const val KEY = "RestoreAttachmentJob"
    private val TAG = Log.tag(RestoreAttachmentJob::class.java)

    /**
     * Create a restore job for the initial large batch of media on a fresh restore
     */
    fun forInitialRestore(attachmentId: AttachmentId, messageId: Long): RestoreAttachmentJob {
      return RestoreAttachmentJob(
        attachmentId = attachmentId,
        messageId = messageId,
        manual = false,
        queue = constructQueueString(RestoreOperation.INITIAL_RESTORE)
      )
    }

    /**
     * Create a restore job for the large batch of media on a full media restore after disabling optimize media.
     *
     * See [RestoreOptimizedMediaJob].
     */
    fun forOffloadedRestore(attachmentId: AttachmentId, messageId: Long): RestoreAttachmentJob {
      return RestoreAttachmentJob(
        attachmentId = attachmentId,
        messageId = messageId,
        manual = false,
        queue = constructQueueString(RestoreOperation.RESTORE_OFFLOADED)
      )
    }

    /**
     * Restore an attachment when manually triggered by user interaction.
     *
     * @return job id of the restore
     */
    @JvmStatic
    fun restoreAttachment(attachment: DatabaseAttachment): String {
      val restoreJob = RestoreAttachmentJob(
        messageId = attachment.mmsId,
        attachmentId = attachment.attachmentId,
        manual = true,
        queue = constructQueueString(RestoreOperation.MANUAL)
      )

      AppDependencies.jobManager.add(restoreJob)
      return restoreJob.id
    }

    /**
     * There are three modes of restore and we use separate queues for each to facilitate canceling if necessary.
     */
    @JvmStatic
    fun constructQueueString(restoreOperation: RestoreOperation): String {
      return "RestoreAttachmentJob::${restoreOperation.name}"
    }
  }

  private constructor(messageId: Long, attachmentId: AttachmentId, manual: Boolean, queue: String) : this(
    Parameters.Builder()
      .setQueue(queue)
      .addConstraint(RestoreAttachmentConstraint.KEY)
      .addConstraint(BatteryNotLowConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(30))
      .build(),
    messageId,
    attachmentId,
    manual
  )

  override fun serialize(): ByteArray {
    return RestoreAttachmentJobData(messageId = messageId, attachmentId = attachmentId.id, manual = manual).encode()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onAdded() {
    SignalDatabase.attachments.setRestoreTransferState(attachmentId, AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS)
  }

  @Throws(Exception::class)
  override fun onRun() {
    try {
      doWork()
    } catch (e: IOException) {
      if (BackupRepository.checkForOutOfStorageError(TAG)) {
        throw RetryLaterException(e)
      } else {
        throw e
      }
    }
  }

  @Throws(IOException::class, RetryLaterException::class)
  fun doWork() {
    Log.i(TAG, "onRun() messageId: $messageId  attachmentId: $attachmentId")

    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)

    if (attachment == null) {
      Log.w(TAG, "attachment no longer exists.")
      return
    }

    if (attachment.isPermanentlyFailed) {
      Log.w(TAG, "Attachment was marked as a permanent failure. Refusing to download.")
      return
    }

    if (attachment.transferState != AttachmentTable.TRANSFER_NEEDS_RESTORE &&
      attachment.transferState != AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS &&
      (attachment.transferState != AttachmentTable.TRANSFER_RESTORE_OFFLOADED)
    ) {
      Log.w(TAG, "Attachment does not need to be restored.")
      return
    }

    retrieveAttachment(messageId, attachmentId, attachment)
  }

  override fun onFailure() {
    if (isCanceled) {
      SignalDatabase.attachments.setTransferState(messageId, attachmentId, AttachmentTable.TRANSFER_RESTORE_OFFLOADED)
    } else {
      Log.w(TAG, format(this, "onFailure() messageId: $messageId  attachmentId: $attachmentId"))

      markFailed(attachmentId)
    }
  }

  override fun onShouldRetry(exception: Exception): Boolean {
    return exception is PushNetworkException ||
      exception is RetryLaterException
  }

  @Throws(IOException::class, RetryLaterException::class)
  private fun retrieveAttachment(
    messageId: Long,
    attachmentId: AttachmentId,
    attachment: DatabaseAttachment,
    forceTransitTier: Boolean = false
  ) {
    val maxReceiveSize: Long = RemoteConfig.maxAttachmentReceiveSizeBytes
    val attachmentFile: File = SignalDatabase.attachments.getOrCreateTransferFile(attachmentId)
    var archiveFile: File? = null
    var useArchiveCdn = false

    try {
      if (attachment.size > maxReceiveSize) {
        throw MmsException("Attachment too large, failing download")
      }

      useArchiveCdn = if (SignalStore.backup.backsUpMedia && !forceTransitTier) {
        if (attachment.archiveMediaName.isNullOrEmpty()) {
          throw InvalidAttachmentException("Invalid attachment configuration")
        }
        true
      } else {
        false
      }

      val messageReceiver = AppDependencies.signalServiceMessageReceiver
      val pointer = attachment.createArchiveAttachmentPointer(useArchiveCdn)

      val progressListener = object : SignalServiceAttachment.ProgressListener {
        override fun onAttachmentProgress(total: Long, progress: Long) {
          EventBus.getDefault().postSticky(PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, total, progress))
        }

        override fun shouldCancel(): Boolean {
          return this@RestoreAttachmentJob.isCanceled
        }
      }

      val downloadResult = if (useArchiveCdn) {
        archiveFile = SignalDatabase.attachments.getOrCreateArchiveTransferFile(attachmentId)
        val cdnCredentials = BackupRepository.getCdnReadCredentials(BackupRepository.CredentialType.MEDIA, attachment.archiveCdn).successOrThrow().headers

        messageReceiver
          .retrieveArchivedAttachment(
            SignalStore.backup.mediaRootBackupKey.deriveMediaSecrets(MediaName(attachment.archiveMediaName!!)),
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

      SignalDatabase.attachments.finalizeAttachmentAfterDownload(messageId, attachmentId, downloadResult.dataStream, downloadResult.iv, if (manual) System.currentTimeMillis().milliseconds else null)
    } catch (e: RangeException) {
      val transferFile = archiveFile ?: attachmentFile
      Log.w(TAG, "Range exception, file size " + transferFile.length(), e)
      if (transferFile.delete()) {
        Log.i(TAG, "Deleted temp download file to recover")
        throw RetryLaterException(e)
      } else {
        throw IOException("Failed to delete temp download file following range exception")
      }
    } catch (e: InvalidAttachmentException) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(attachmentId)
    } catch (e: NonSuccessfulResponseCodeException) {
      if (SignalStore.backup.backsUpMedia) {
        if (e.code == 404 && !forceTransitTier && attachment.remoteLocation?.isNotBlank() == true) {
          Log.i(TAG, "Failed to download attachment from archive! Should only happen for recent attachments in a narrow window. Retrying download from transit CDN.")
          if (RemoteConfig.internalUser) {
            postFailedToDownloadFromArchiveNotification()
          }
          retrieveAttachment(messageId, attachmentId, attachment, true)
          return
        } else if (e.code == 401 && useArchiveCdn) {
          SignalStore.backup.mediaCredentials.cdnReadCredentials = null
          SignalStore.backup.cachedMediaCdnPath = null
          throw RetryLaterException(e)
        }
      }

      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(attachmentId)
    } catch (e: MmsException) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(attachmentId)
    } catch (e: MissingConfigurationException) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e)
      markFailed(attachmentId)
    } catch (e: InvalidMessageException) {
      Log.w(TAG, "Experienced an InvalidMessageException while trying to download an attachment.", e)
      if (e.cause is InvalidMacException) {
        Log.w(TAG, "Detected an invalid mac. Treating as a permanent failure.")
        markPermanentlyFailed(attachmentId)
      } else {
        markFailed(attachmentId)
      }
    }
  }

  private fun markFailed(attachmentId: AttachmentId) {
    SignalDatabase.attachments.setRestoreTransferState(attachmentId, AttachmentTable.TRANSFER_PROGRESS_FAILED)
  }

  private fun markPermanentlyFailed(attachmentId: AttachmentId) {
    SignalDatabase.attachments.setRestoreTransferState(attachmentId, AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE)
  }

  private fun postFailedToDownloadFromArchiveNotification() {
    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Failed to download attachment from archive!")
      .setContentText("Tap to send a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)
  }

  class Factory : Job.Factory<RestoreAttachmentJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RestoreAttachmentJob {
      val data = RestoreAttachmentJobData.ADAPTER.decode(serializedData!!)
      return RestoreAttachmentJob(
        parameters = parameters,
        messageId = data.messageId,
        attachmentId = AttachmentId(data.attachmentId),
        manual = data.manual
      )
    }
  }

  enum class RestoreOperation {
    MANUAL, RESTORE_OFFLOADED, INITIAL_RESTORE
  }
}
