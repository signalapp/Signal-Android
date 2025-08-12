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
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidMacException
import org.signal.libsignal.protocol.InvalidMessageException
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.InvalidAttachmentException
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.createArchiveAttachmentPointer
import org.thoughtcrime.securesms.backup.v2.requireMediaName
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobLogger.format
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.jobmanager.impl.BatteryNotLowConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.RestoreAttachmentConstraint
import org.thoughtcrime.securesms.jobs.protos.RestoreAttachmentJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream.IntegrityCheck
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.push.exceptions.RangeException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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

  object Queues {
    /** Job queues used for the initial attachment restore post-registration. The number of queues in this set determine the level of parallelization. */
    val INITIAL_RESTORE = setOf(
      "RestoreAttachmentJob::InitialRestore_01",
      "RestoreAttachmentJob::InitialRestore_02",
      "RestoreAttachmentJob::InitialRestore_03",
      "RestoreAttachmentJob::InitialRestore_04"
    )

    /** Job queues used when restoring an offloaded attachment. The number of queues in this set determine the level of parallelization. */
    val OFFLOAD_RESTORE = setOf(
      "RestoreAttachmentJob::OffloadRestore_01",
      "RestoreAttachmentJob::OffloadRestore_02",
      "RestoreAttachmentJob::OffloadRestore_03",
      "RestoreAttachmentJob::OffloadRestore_04"
    )

    /** Job queues used for manual restoration. The number of queues in this set determine the level of parallelization. */
    val MANUAL_RESTORE = setOf(
      "RestoreAttachmentJob::ManualRestore_01",
      "RestoreAttachmentJob::ManualRestore_02"
    )

    /** All possible queues used by this job. */
    val ALL = INITIAL_RESTORE + OFFLOAD_RESTORE + MANUAL_RESTORE
  }

  companion object {
    const val KEY = "RestoreAttachmentJob"
    private val TAG = Log.tag(RestoreAttachmentJob::class.java)

    /**
     * Create a restore job for the initial large batch of media on a fresh restore.
     * Will enqueue with some amount of parallization with low job priority.
     */
    fun forInitialRestore(attachmentId: AttachmentId, messageId: Long): RestoreAttachmentJob {
      return RestoreAttachmentJob(
        attachmentId = attachmentId,
        messageId = messageId,
        manual = false,
        queue = Queues.INITIAL_RESTORE.random(),
        priority = Parameters.PRIORITY_LOW
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
        queue = Queues.OFFLOAD_RESTORE.random(),
        priority = Parameters.PRIORITY_LOW
      )
    }

    /**
     * Restore an attachment when manually triggered by user interaction.
     *
     * @return job id of the restore
     */
    @JvmStatic
    fun forManualRestore(attachment: DatabaseAttachment): String {
      val restoreJob = RestoreAttachmentJob(
        messageId = attachment.mmsId,
        attachmentId = attachment.attachmentId,
        manual = true,
        queue = Queues.MANUAL_RESTORE.random(),
        priority = Parameters.PRIORITY_DEFAULT
      )

      AppDependencies.jobManager.add(restoreJob)
      return restoreJob.id
    }
  }

  private constructor(messageId: Long, attachmentId: AttachmentId, manual: Boolean, queue: String, priority: Int) : this(
    Parameters.Builder()
      .setQueue(queue)
      .apply {
        if (manual) {
          addConstraint(NetworkConstraint.KEY)
        } else {
          addConstraint(RestoreAttachmentConstraint.KEY)
          addConstraint(BatteryNotLowConstraint.KEY)
        }
      }
      .setLifespan(TimeUnit.DAYS.toMillis(30))
      .setGlobalPriority(priority)
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
      Log.w(TAG, "[$attachmentId] Attachment no longer exists.")
      return
    }

    if (attachment.isPermanentlyFailed) {
      Log.w(TAG, "[$attachmentId] Attachment was marked as a permanent failure. Refusing to download.")
      return
    }

    if (attachment.transferState != AttachmentTable.TRANSFER_NEEDS_RESTORE &&
      attachment.transferState != AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS &&
      attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_FAILED &&
      attachment.transferState != AttachmentTable.TRANSFER_RESTORE_OFFLOADED
    ) {
      Log.w(TAG, "[$attachmentId] Attachment does not need to be restored. Current state: ${attachment.transferState}")
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

      Log.w(TAG, "onFailure(): Attempting to fall back on attachment thumbnail.")
      val restoreThumbnailAttachmentJob = RestoreAttachmentThumbnailJob(
        messageId = messageId,
        attachmentId = attachmentId,
        highPriority = manual
      )

      AppDependencies.jobManager.add(restoreThumbnailAttachmentJob)
    }
  }

  override fun onShouldRetry(exception: Exception): Boolean {
    return exception is PushNetworkException || exception is RetryLaterException
  }

  override fun getNextRunAttemptBackoff(pastAttemptCount: Int, exception: java.lang.Exception): Long {
    return if (exception is NonSuccessfulResponseCodeException && exception.code == 404) {
      if (manual) {
        BackoffUtil.exponentialBackoff(pastAttemptCount, 1.hours.inWholeMilliseconds)
      } else {
        1.days.inWholeMilliseconds * 2.0.pow(max(0.0, pastAttemptCount.toDouble()) - 1.0).toInt()
      }
    } else {
      super.getNextRunAttemptBackoff(pastAttemptCount, exception)
    }
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
    var useArchiveCdn = false

    if (attachment.remoteDigest == null && attachment.dataHash == null) {
      Log.w(TAG, "[$attachmentId] Attachment has no integrity check! Cannot proceed.")
      markPermanentlyFailed(attachmentId)
      return
    }

    try {
      if (attachment.size > maxReceiveSize) {
        throw MmsException("[$attachmentId] Attachment too large, failing download")
      }

      useArchiveCdn = if (SignalStore.backup.backsUpMedia && !forceTransitTier) {
        if (attachment.archiveTransferState != AttachmentTable.ArchiveTransferState.FINISHED) {
          throw InvalidAttachmentException("[$attachmentId] Invalid attachment configuration! backsUpMedia: ${SignalStore.backup.backsUpMedia}, forceTransitTier: $forceTransitTier, archiveTransferState: ${attachment.archiveTransferState}")
        }
        true
      } else {
        false
      }

      val messageReceiver = AppDependencies.signalServiceMessageReceiver
      val pointer = attachment.createArchiveAttachmentPointer(useArchiveCdn)

      val progressListener = object : SignalServiceAttachment.ProgressListener {
        override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
          EventBus.getDefault().postSticky(PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, progress))
        }

        override fun shouldCancel(): Boolean {
          return this@RestoreAttachmentJob.isCanceled
        }
      }

      val decryptingStream = if (useArchiveCdn) {
        val cdnCredentials = BackupRepository.getCdnReadCredentials(BackupRepository.CredentialType.MEDIA, attachment.archiveCdn ?: RemoteConfig.backupFallbackArchiveCdn).successOrThrow().headers

        messageReceiver
          .retrieveArchivedAttachment(
            SignalStore.backup.mediaRootBackupKey.deriveMediaSecrets(attachment.requireMediaName()),
            attachment.dataHash!!.decodeBase64OrThrow(),
            cdnCredentials,
            attachmentFile,
            pointer,
            maxReceiveSize,
            progressListener
          )
      } else {
        messageReceiver
          .retrieveAttachment(
            pointer,
            attachmentFile,
            maxReceiveSize,
            IntegrityCheck.forEncryptedDigestAndPlaintextHash(pointer.digest.getOrNull(), attachment.dataHash),
            progressListener
          )
      }

      decryptingStream.use { input ->
        SignalDatabase.attachments.finalizeAttachmentAfterDownload(messageId, attachmentId, input, if (manual) System.currentTimeMillis().milliseconds else null)
      }
    } catch (e: RangeException) {
      Log.w(TAG, "[$attachmentId] Range exception, file size " + attachmentFile.length(), e)
      if (attachmentFile.delete()) {
        Log.i(TAG, "Deleted temp download file to recover")
        throw RetryLaterException(e)
      } else {
        throw IOException("Failed to delete temp download file following range exception")
      }
    } catch (e: InvalidAttachmentException) {
      Log.w(TAG, e.message)
      markFailed(attachmentId)
    } catch (e: NonSuccessfulResponseCodeException) {
      when (e.code) {
        404 -> {
          if (forceTransitTier) {
            Log.w(TAG, "[$attachmentId] Completely failed to restore an attachment! Failed downloading from both the archive and transit CDN.")
            maybePostFailedToDownloadFromArchiveAndTransitNotification()
          } else if (SignalStore.backup.backsUpMedia && attachment.remoteLocation.isNotNullOrBlank()) {
            Log.w(TAG, "[$attachmentId] Failed to download attachment from the archive CDN! Retrying download from transit CDN.")
            maybePostFailedToDownloadFromArchiveNotification()

            return retrieveAttachment(messageId, attachmentId, attachment, forceTransitTier = true)
          } else if (SignalStore.backup.backsUpMedia) {
            Log.w(TAG, "[$attachmentId] Completely failed to restore an attachment! Failed to download from archive CDN, and there's not transit CDN info.")
            maybePostFailedToDownloadFromArchiveAndTransitNotification()
          } else if (attachment.remoteLocation.isNotNullOrBlank()) {
            Log.w(TAG, "[$attachmentId] Failed to restore an attachment for a free tier user. Likely just older than 45 days.")
          }
        }
        401 -> {
          if (useArchiveCdn) {
            Log.w(TAG, "[$attachmentId] Had a credential issue when downloading an attachment. Clearing credentials and retrying.")
            SignalStore.backup.mediaCredentials.cdnReadCredentials = null
            SignalStore.backup.cachedMediaCdnPath = null
            throw RetryLaterException(e)
          } else {
            Log.w(TAG, "[$attachmentId] Unexpected 401 response for transit CDN restore.")
          }
        }
      }

      Log.w(TAG, "[$attachmentId] Experienced exception while trying to download an attachment.", e)
      markFailed(attachmentId)
    } catch (e: MmsException) {
      Log.w(TAG, "[$attachmentId] Experienced exception while trying to download an attachment.", e)
      markFailed(attachmentId)
    } catch (e: MissingConfigurationException) {
      Log.w(TAG, "[$attachmentId] Experienced exception while trying to download an attachment.", e)
      markFailed(attachmentId)
    } catch (e: InvalidMessageException) {
      Log.w(TAG, "[$attachmentId] Experienced an InvalidMessageException while trying to download an attachment.", e)
      if (e.cause is InvalidMacException) {
        Log.w(TAG, "[$attachmentId] Detected an invalid mac. Treating as a permanent failure.")
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

  private fun maybePostFailedToDownloadFromArchiveNotification() {
    if (!RemoteConfig.internalUser || !SignalStore.backup.backsUpMedia) {
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Failed to restore attachment from Archive CDN!")
      .setContentText("Tap to send a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)
  }

  private fun maybePostFailedToDownloadFromArchiveAndTransitNotification() {
    if (!RemoteConfig.internalUser || !SignalStore.backup.backsUpMedia) {
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Completely failed to restore attachment!")
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
}
