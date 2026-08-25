/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import arrow.core.Either
import org.signal.core.models.backup.MediaId
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.network.service.ArchiveError
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * When we delete media throughout the day, we can't delete it from the archive service right away, or we'd invalidate the last-known snapshot.
 * Instead, we have to do it after a backup is taken. This job looks at [BackupMediaSnapshotTable] in order to determine which media objects
 * can be safely deleted from the archive service.
 */
class ArchiveCommitAttachmentDeletesJob private constructor(parameters: Parameters) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(ArchiveCommitAttachmentDeletesJob::class.java)

    const val KEY = "ArchiveCommitAttachmentDeletesJob"
    const val ARCHIVE_ATTACHMENT_QUEUE = "ArchiveAttachmentQueue"

    @VisibleForTesting
    internal const val REMOTE_DELETE_BATCH_SIZE = 1_000

    /**
     * Deletes the provided attachments from the CDN.
     *
     * @return Null if successful, or a [Result] indicating the failure.
     */
    suspend fun deleteMediaObjectsFromCdn(tag: String, attachmentsToDelete: Set<ArchivedMediaObject>, backoffGenerator: () -> Long, cancellationSignal: () -> Boolean): Result? {
      if (RemoteConfig.internalUser) {
        val mediaIds = attachmentsToDelete.take(250).map { MediaId(Base64.decode(it.mediaId)) }
        Log.w(TAG, "Deleting MediaIds (showing ${mediaIds.size}/${attachmentsToDelete.size}): ${mediaIds.joinToString() }")
      }

      attachmentsToDelete.chunked(REMOTE_DELETE_BATCH_SIZE).forEach { chunk ->
        if (cancellationSignal()) {
          Log.w(tag, "Job cancelled while deleting attachments from the CDN.", true)
          return Result.failure()
        }

        val mediaToDelete = chunk.filter { it.cdn == Cdn.CDN_3.cdnNumber }.map { it.toDeleteBackupMediaItem() }

        when (val result = AppDependencies.archiveService.deleteArchivedMedia(mediaToDelete)) {
          is Either.Right -> {
            Log.i(tag, "Successfully deleted ${chunk.size} attachments off of the CDN. (Note: Count includes thumbnails)", true)
          }

          is Either.Left -> when (val error = result.value) {
            is ArchiveError.NetworkError -> {
              return if (error.isServerSide) {
                Log.w(tag, "Server error while deleting attachments from the CDN. Retrying with a larger backoff.", error.exception, true)
                Result.retry(1.hours.inWholeMilliseconds)
              } else {
                Result.retry(backoffGenerator())
              }
            }

            is ArchiveError.CredentialError.RateLimited -> {
              Log.w(tag, "Rate limited while attempting to delete media objects. Retrying later.", true)
              return Result.retry(error.retryAfter?.inWholeMilliseconds ?: backoffGenerator())
            }

            is ArchiveError.ApplicationError -> {
              Log.w(tag, "Crash when trying to delete attachments from the CDN", error.exception, true)
              return Result.fatalFailure(RuntimeException(error.exception))
            }

            is ArchiveError.CredentialError.Unauthorized,
            is ArchiveError.CredentialError.NotFound,
            is ArchiveError.CredentialError.InvalidRequest,
            is ArchiveError.CredentialError.ZkVerificationFailed -> {
              Log.w(tag, "Failed to delete attachments from CDN: ${error::class.simpleName}. Considering this a terminal failure.", error.cause, true)
              return Result.failure()
            }
          }
        }
      }

      return null
    }
  }

  constructor() : this(
    parameters = Parameters.Builder()
      .setQueue(ARCHIVE_ATTACHMENT_QUEUE)
      .setMaxInstancesForQueue(1)
      .setLifespan(30.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!SignalStore.backup.backsUpMedia) {
      Log.w(TAG, "This user doesn't back up media! Skipping.")
      return Result.success()
    }

    // Read once so every page judges against the same threshold, even if a backup finishes while we're paging.
    val messageInclusionCutoffTime = SignalStore.backup.lastUsedMessageCutoffTime

    var retainedCount = 0
    var unknownCdnCount = 0
    var lastId = 0L
    var page = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaEntries(pageSize = REMOTE_DELETE_BATCH_SIZE, afterId = lastId)

    while (page.isNotEmpty()) {
      if (isCanceled) {
        Log.w(TAG, "Job cancelled while processing media objects for deletion.")
        return Result.failure()
      }

      lastId = page.last().id

      // Full-size and thumbnail rows share a mediaName, so they have to be judged separately. A surviving attachment keeps its full-size object alive but says
      // nothing about whether that attachment still wants a thumbnail archived.
      val (thumbnailPage, fullSizePage) = page.partition { it.isThumbnail }

      val fullSizeByMediaName = fullSizePage.groupBy { it.mediaNameParts() }
      val unreferencedFullSizeNames = SignalDatabase.attachments.getMediaNamesWithNoAttachment(fullSizeByMediaName.keys, messageInclusionCutoffTime)
      val unreferencedFullSize = fullSizeByMediaName.filterKeys { it in unreferencedFullSizeNames }.values.flatten()

      val thumbnailsByMediaName = thumbnailPage.groupBy { it.mediaNameParts() }
      val unreferencedThumbnailNames = SignalDatabase.attachments.getMediaNamesWithNoEligibleThumbnail(thumbnailsByMediaName.keys, messageInclusionCutoffTime)
      val unreferencedThumbnails = thumbnailsByMediaName.filterKeys { it in unreferencedThumbnailNames }.values.flatten()

      val unreferencedEntries = unreferencedFullSize + unreferencedThumbnails

      val safeToDelete = unreferencedEntries.mapNotNull { entry -> entry.cdn?.let { ArchivedMediaObject(mediaId = entry.mediaId, cdn = it) } }.toSet()

      retainedCount += page.size - unreferencedEntries.size
      unknownCdnCount += unreferencedEntries.size - safeToDelete.size

      if (safeToDelete.isNotEmpty()) {
        deleteMediaObjectsFromCdn(
          tag = TAG,
          attachmentsToDelete = safeToDelete,
          backoffGenerator = this::defaultBackoff,
          cancellationSignal = this::isCanceled
        )?.let { result -> return result }

        SignalDatabase.backupMediaSnapshots.deleteOldMediaObjects(safeToDelete.map { it.mediaId })
      }

      page = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaEntries(pageSize = REMOTE_DELETE_BATCH_SIZE, afterId = lastId)
    }

    if (retainedCount > 0) {
      Log.w(TAG, "Retained $retainedCount media objects that dropped out of the latest snapshot but are still referenced by an attachment. They stay tracked and will be reconsidered after the next backup.", true)
    }

    if (unknownCdnCount > 0) {
      Log.w(TAG, "Retained $unknownCdnCount unreferenced media objects that have no recorded CDN, will get resolved during reconciliation rather than here.", true)
    }

    return Result.success()
  }

  override fun onFailure() = Unit

  private fun BackupMediaSnapshotTable.ExistingMediaEntry.mediaNameParts(): AttachmentTable.MediaNameParts {
    return AttachmentTable.MediaNameParts(
      plaintextHash = Base64.encodeWithPadding(plaintextHash),
      remoteKey = Base64.encodeWithPadding(remoteKey)
    )
  }

  class Factory : Job.Factory<ArchiveCommitAttachmentDeletesJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ArchiveCommitAttachmentDeletesJob {
      return ArchiveCommitAttachmentDeletesJob(parameters)
    }
  }
}
