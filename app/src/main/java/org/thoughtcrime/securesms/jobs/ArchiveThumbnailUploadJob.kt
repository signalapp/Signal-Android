/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.protos.resumableuploads.ResumableUpload
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.backup.v2.ArchiveDatabaseExecutor
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.hadIntegrityCheckPerformed
import org.thoughtcrime.securesms.backup.v2.requireThumbnailMediaName
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.BackupMessagesConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NoRemoteArchiveGarbageCollectionPendingConstraint
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.jobs.protos.ArchiveThumbnailUploadJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.DecryptableUri
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.ImageCompressionUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Optional
import kotlin.math.floor
import kotlin.math.max
import kotlin.time.Duration.Companion.days

/**
 * Uploads a thumbnail for the specified attachment to the archive service, if possible.
 */
class ArchiveThumbnailUploadJob private constructor(
  params: Parameters,
  val attachmentId: AttachmentId
) : Job(params) {

  companion object {
    const val KEY = "ArchiveThumbnailUploadJob"
    private val TAG = Log.tag(ArchiveThumbnailUploadJob::class.java)

    private const val STARTING_IMAGE_QUALITY = 75f
    private const val MINIMUM_IMAGE_QUALITY = 10f
    private const val MAX_PIXEL_DIMENSION = 256
    private const val ADDITIONAL_QUALITY_DECREASE = 10f

    /** A set of possible queues this job may use. The number of queues determines the parallelism. */
    val QUEUES = setOf(
      "ArchiveThumbnailUploadJob_1",
      "ArchiveThumbnailUploadJob_2",
      "ArchiveThumbnailUploadJob_3",
      "ArchiveThumbnailUploadJob_4"
    )

    fun enqueueIfNecessary(attachmentId: AttachmentId) {
      if (SignalStore.backup.backsUpMedia) {
        AppDependencies.jobManager.add(ArchiveThumbnailUploadJob(attachmentId))
      }
    }

    fun JobSpec.isForArchiveThumbnailUploadJob(attachmentId: AttachmentId): Boolean {
      return this.factoryKey == KEY && this.serializedData?.let { ArchiveThumbnailUploadJobData.ADAPTER.decode(it).attachmentId } == attachmentId.id
    }
  }

  constructor(attachmentId: AttachmentId) : this(
    Parameters.Builder()
      .setQueue(QUEUES.random())
      .addConstraint(BackupMessagesConstraint.KEY)
      .addConstraint(NoRemoteArchiveGarbageCollectionPendingConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setGlobalPriority(Parameters.PRIORITY_LOWER)
      .build(),
    attachmentId
  )

  override fun serialize(): ByteArray {
    return ArchiveThumbnailUploadJobData(
      attachmentId = attachmentId.id
    ).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun onAdded() {
    val transferStatus = SignalDatabase.attachments.getArchiveThumbnailTransferState(attachmentId) ?: return

    if (transferStatus == AttachmentTable.ArchiveTransferState.NONE) {
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS)
      }
    }
  }

  override fun run(): Result {
    // TODO [cody] Remove after a few releases as we migrate to the correct constraint
    if (!BackupMessagesConstraint.isMet(context)) {
      return Result.failure()
    }

    val attachment = SignalDatabase.attachments.getAttachment(attachmentId)
    if (attachment == null) {
      Log.w(TAG, "$attachmentId not found, assuming this job is no longer necessary.")
      return Result.success()
    }

    if (!MediaUtil.isImageOrVideoType(attachment.contentType)) {
      Log.w(TAG, "$attachmentId isn't visual media (contentType = ${attachment.contentType}). Skipping.")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    if (attachment.quote) {
      Log.w(TAG, "$attachmentId is a quote. Skipping.")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    if (attachment.dataHash == null || attachment.remoteKey == null) {
      Log.w(TAG, "$attachmentId is missing necessary ingredients for a mediaName!")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    if (!attachment.hadIntegrityCheckPerformed()) {
      Log.w(TAG, "$attachmentId has no integrity check! Cannot proceed.")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    if (SignalDatabase.messages.isStory(attachment.mmsId)) {
      Log.w(TAG, "$attachmentId is a story. Skipping.")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
      return Result.success()
    }

    // TODO [backups] Determine if we actually need to upload or are reusing a thumbnail from another attachment

    val thumbnailResult = generateThumbnailIfPossible(attachment)
    if (thumbnailResult == null) {
      Log.w(TAG, "Unable to generate a thumbnail result for $attachmentId")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.PERMANENT_FAILURE)
      }
      return Result.success()
    }

    if (isCanceled) {
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE)
      }
      return Result.failure()
    }

    val mediaRootBackupKey = SignalStore.backup.mediaRootBackupKey

    val specResult = BackupRepository
      .getAttachmentUploadForm()
      .then { form ->
        SignalNetwork.attachments.getResumableUploadSpec(
          key = mediaRootBackupKey.deriveThumbnailTransitKey(attachment.requireThumbnailMediaName()),
          iv = Util.getSecretBytes(16),
          uploadForm = form
        )
      }

    if (isCanceled) {
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE)
      }
      return Result.failure()
    }

    val resumableUpload = when (specResult) {
      is NetworkResult.Success -> {
        Log.d(TAG, "Got an upload spec!")
        specResult.result.toProto()
      }

      is NetworkResult.ApplicationError -> {
        Log.w(TAG, "Failed to get an upload spec due to an application error. Retrying.", specResult.throwable)
        return Result.retry(defaultBackoff())
      }

      is NetworkResult.NetworkError -> {
        Log.w(TAG, "Encountered a transient network error when getting upload spec. Retrying.")
        return Result.retry(defaultBackoff())
      }

      is NetworkResult.StatusCodeError -> {
        Log.w(TAG, "Failed to get an upload spec with status code ${specResult.code}")
        return Result.retry(defaultBackoff())
      }
    }

    if (isCanceled) {
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE)
      }
      return Result.failure()
    }

    val attachmentPointer = try {
      buildSignalServiceAttachmentStream(thumbnailResult, resumableUpload).use { stream ->
        val pointer = AppDependencies.signalServiceMessageSender.uploadAttachment(stream)
        PointerAttachment.forPointer(Optional.of(pointer)).get()
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to upload attachment", e)
      return Result.retry(defaultBackoff())
    }

    if (isCanceled) {
      return Result.failure()
    }

    return when (val result = BackupRepository.copyThumbnailToArchive(attachmentPointer, attachment)) {
      is NetworkResult.Success -> {
        // save attachment thumbnail
        ArchiveDatabaseExecutor.runBlocking {
          SignalDatabase.attachments.finalizeAttachmentThumbnailAfterUpload(
            attachmentId = attachmentId,
            attachmentPlaintextHash = attachment.dataHash,
            attachmentRemoteKey = attachment.remoteKey,
            data = thumbnailResult.data
          )

          Log.d(TAG, "Successfully archived thumbnail for $attachmentId")
          SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
        }
        Result.success()
      }

      is NetworkResult.NetworkError -> {
        Log.w(TAG, "Hit a network error when trying to archive thumbnail for $attachmentId", result.exception)
        Result.retry(defaultBackoff())
      }

      is NetworkResult.StatusCodeError -> {
        Log.w(TAG, "Hit a status code error of ${result.code} when trying to archive thumbnail for $attachmentId")
        Result.retry(defaultBackoff())
      }

      is NetworkResult.ApplicationError -> Result.fatalFailure(RuntimeException(result.throwable))
    }
  }

  override fun onFailure() {
    if (this.isCanceled) {
      Log.w(TAG, "[$attachmentId] Job was canceled, updating archive thumbnail transfer state to ${AttachmentTable.ArchiveTransferState.NONE}.")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
      }
    } else {
      Log.w(TAG, "[$attachmentId] Job failed, updating archive thumbnail transfer state to ${AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE} (if not already a permanent failure).")
      ArchiveDatabaseExecutor.runBlocking {
        SignalDatabase.attachments.setArchiveThumbnailTransferStateFailure(attachmentId, AttachmentTable.ArchiveTransferState.TEMPORARY_FAILURE)
      }
    }
  }

  private fun generateThumbnailIfPossible(attachment: DatabaseAttachment): ImageCompressionUtil.Result? {
    try {
      val uri: DecryptableUri = attachment.uri?.let { DecryptableUri(it) } ?: return null

      return if (MediaUtil.isImageType(attachment.contentType)) {
        compress(uri, attachment.contentType ?: "")
      } else if (MediaUtil.isVideoType(attachment.contentType)) {
        MediaUtil.getVideoThumbnail(context, attachment.uri)?.let {
          compress(uri, attachment.contentType ?: "")
        }
      } else {
        null
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to generate thumbnail for $attachmentId", e)
      return null
    }
  }

  private fun compress(uri: DecryptableUri, contentType: String): ImageCompressionUtil.Result? {
    val maxFileSize = RemoteConfig.backupMaxThumbnailFileSize.inWholeBytes.toFloat()
    var attempts = 0
    var quality = STARTING_IMAGE_QUALITY

    var result: ImageCompressionUtil.Result? = ImageCompressionUtil.compress(context, contentType, MediaUtil.IMAGE_WEBP, uri, MAX_PIXEL_DIMENSION, quality.toInt())

    while (result != null && result.data.size > maxFileSize && attempts < 5 && quality > MINIMUM_IMAGE_QUALITY) {
      val maxSizeToActualRatio = maxFileSize / result.data.size.toFloat()
      val newQuality = quality * maxSizeToActualRatio - ADDITIONAL_QUALITY_DECREASE

      quality = floor(max(MINIMUM_IMAGE_QUALITY, newQuality))
      result = ImageCompressionUtil.compress(context, contentType, MediaUtil.IMAGE_WEBP, uri, MAX_PIXEL_DIMENSION, quality.toInt())
      attempts++
    }
    return result
  }

  private fun buildSignalServiceAttachmentStream(result: ImageCompressionUtil.Result, uploadSpec: ResumableUpload): SignalServiceAttachmentStream {
    return SignalServiceAttachment.newStreamBuilder()
      .withStream(ByteArrayInputStream(result.data))
      .withContentType(result.mimeType)
      .withLength(result.data.size.toLong())
      .withWidth(result.width)
      .withHeight(result.height)
      .withUploadTimestamp(System.currentTimeMillis())
      .withResumableUploadSpec(ResumableUploadSpec.from(uploadSpec))
      .build()
  }

  class Factory : Job.Factory<ArchiveThumbnailUploadJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ArchiveThumbnailUploadJob {
      val data = ArchiveThumbnailUploadJobData.ADAPTER.decode(serializedData!!)
      return ArchiveThumbnailUploadJob(
        params = parameters,
        attachmentId = AttachmentId(data.attachmentId)
      )
    }
  }
}
