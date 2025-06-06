/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.whispersystems.signalservice.api.NetworkResult
import java.lang.RuntimeException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * When we delete media throughout the day, we can't delete it from the archive service right away, or we'd invalidate the last-known snapshot.
 * Instead, we have to do it after a backup is taken. This job looks at [BackupMediaSnapshotTable] in order to determine which media objects
 * can be safely deleted from the archive service.
 */
class ArchiveCommitAttachmentDeletesJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(ArchiveCommitAttachmentDeletesJob::class.java)

    const val KEY = "ArchiveCommitAttachmentDeletesJob"
    const val ARCHIVE_ATTACHMENT_QUEUE = "ArchiveAttachmentQueue"

    private const val REMOTE_DELETE_BATCH_SIZE = 1_000

    /**
     * Deletes the provided attachments from the CDN.
     *
     * @return Null if successful, or a [Result] indicating the failure.
     */
    fun deleteMediaObjectsFromCdn(tag: String, attachmentsToDelete: Set<ArchivedMediaObject>, backoffGenerator: () -> Long, cancellationSignal: () -> Boolean): Result? {
      attachmentsToDelete.chunked(REMOTE_DELETE_BATCH_SIZE).forEach { chunk ->
        if (cancellationSignal()) {
          Log.w(tag, "Job cancelled while deleting attachments from the CDN.")
          return Result.failure()
        }

        when (val result = BackupRepository.deleteAbandonedMediaObjects(chunk)) {
          is NetworkResult.Success -> {
            Log.i(tag, "Successfully deleted ${chunk.size} attachments off of the CDN. (Note: Count includes thumbnails)")
          }

          is NetworkResult.NetworkError -> {
            return Result.retry(backoffGenerator())
          }

          is NetworkResult.StatusCodeError -> {
            when (result.code) {
              429 -> {
                Log.w(tag, "Rate limited while attempting to delete media objects. Retrying later.")
                return Result.retry(result.retryAfter()?.inWholeMilliseconds ?: backoffGenerator())
              }

              in 500..599 -> {
                Log.w(tag, "Failed to delete attachments from CDN with code: ${result.code}. Retrying with a larger backoff.", result.getCause())
                return Result.retry(1.hours.inWholeMilliseconds)
              }

              else -> {
                Log.w(tag, "Failed to delete attachments from CDN with code: ${result.code}. Considering this a terminal failure.", result.getCause())
                return Result.failure()
              }
            }
          }

          is NetworkResult.ApplicationError -> {
            Log.w(tag, "Crash when trying to delete attachments from the CDN", result.getCause())
            Result.fatalFailure(RuntimeException(result.getCause()))
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

  override fun run(): Result {
    var mediaObjects = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaObjects(REMOTE_DELETE_BATCH_SIZE)

    while (mediaObjects.isNotEmpty()) {
      if (isCanceled) {
        Log.w(TAG, "Job cancelled while processing media objects for deletion.")
        return Result.failure()
      }

      deleteMediaObjectsFromCdn(TAG, mediaObjects, this::defaultBackoff, this::isCanceled)?.let { result -> return result }
      SignalDatabase.backupMediaSnapshots.deleteOldMediaObjects(mediaObjects)

      mediaObjects = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaObjects(REMOTE_DELETE_BATCH_SIZE)
    }

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<ArchiveCommitAttachmentDeletesJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ArchiveCommitAttachmentDeletesJob {
      return ArchiveCommitAttachmentDeletesJob(parameters)
    }
  }
}
