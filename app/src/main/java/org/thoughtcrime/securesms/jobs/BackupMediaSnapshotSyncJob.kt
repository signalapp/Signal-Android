/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.BackupMediaSnapshotSyncJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse
import java.lang.RuntimeException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * When we delete attachments locally, we can't immediately delete them from the archive CDN. This is because there is still a backup that exists that
 * references that attachment -- at least until a new backup is made.
 *
 * This job uses data we store locally in [org.thoughtcrime.securesms.database.BackupMediaSnapshotTable] to determine which media objects can be safely
 * deleted from the archive CDN, and then deletes them.
 */
class BackupMediaSnapshotSyncJob private constructor(
  private val syncTime: Long,
  private var serverCursor: String?,
  parameters: Parameters
) : Job(parameters) {

  companion object {

    private val TAG = Log.tag(BackupMediaSnapshotSyncJob::class)

    const val KEY = "BackupMediaSnapshotSyncJob"

    private const val REMOTE_DELETE_BATCH_SIZE = 750
    private const val CDN_PAGE_SIZE = 10_000
    private val BACKUP_MEDIA_SYNC_INTERVAL = 7.days.inWholeMilliseconds

    fun enqueue(syncTime: Long) {
      AppDependencies.jobManager.add(
        BackupMediaSnapshotSyncJob(
          syncTime = syncTime,
          serverCursor = null,
          parameters = Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue("BackupMediaSnapshotSyncJob")
            .setMaxAttempts(Parameters.UNLIMITED)
            .setLifespan(12.hours.inWholeMilliseconds)
            .build()
        )
      )
    }
  }

  override fun serialize(): ByteArray = BackupMediaSnapshotSyncJobData(syncTime, serverCursor ?: "").encode()

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (serverCursor == null) {
      removeLocallyDeletedAttachmentsFromCdn()?.let { result -> return result }
    } else {
      Log.d(TAG, "Already deleted old attachments from CDN. Skipping to syncing with remote.")
    }

    val timeSinceLastRemoteSync = System.currentTimeMillis() - SignalStore.backup.lastMediaSyncTime
    if (serverCursor == null && timeSinceLastRemoteSync > 0 && timeSinceLastRemoteSync < BACKUP_MEDIA_SYNC_INTERVAL) {
      Log.d(TAG, "No need to do a remote sync yet. Time since last sync: $timeSinceLastRemoteSync ms")
      return Result.success()
    }

    return syncDataFromCdn() ?: Result.success()
  }

  override fun onFailure() {
    SignalDatabase.backupMediaSnapshots.clearLastSeenOnRemote()
  }

  /**
   * Looks through our local snapshot of what attachments we put in the last backup file, and uses that to delete any old attachments from the archive CDN
   * that we no longer need.
   */
  private fun removeLocallyDeletedAttachmentsFromCdn(): Result? {
    var mediaObjects = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaObjects(syncTime, REMOTE_DELETE_BATCH_SIZE)

    while (mediaObjects.isNotEmpty()) {
      deleteMediaObjectsFromCdn(mediaObjects)?.let { result -> return result }
      SignalDatabase.backupMediaSnapshots.deleteMediaObjects(mediaObjects)

      mediaObjects = SignalDatabase.backupMediaSnapshots.getPageOfOldMediaObjects(syncTime, CDN_PAGE_SIZE)
    }

    return null
  }

  /**
   * Fetches all attachment metadata from the archive CDN and ensures that our local store is in sync with it.
   *
   * Specifically, we make sure that:
   * (1) We delete any attachments from the CDN that we have no knowledge of in any backup.
   * (2) We ensure that our local store has the correct CDN for any attachments on the CDN (they should only really fall out of sync when you restore a backup
   *     that was made before all of the attachments had been uploaded).
   */
  private fun syncDataFromCdn(): Result? {
    val attachmentsToDelete = HashSet<ArchivedMediaObject>()
    var cursor: String? = serverCursor

    do {
      val (archivedItemPage, jobResult) = getRemoteArchiveItemPage(cursor)
      if (jobResult != null) {
        return jobResult
      }
      check(archivedItemPage != null)

      cursor = archivedItemPage.cursor
      attachmentsToDelete += syncCdnPage(archivedItemPage)

      if (attachmentsToDelete.size >= REMOTE_DELETE_BATCH_SIZE) {
        deleteMediaObjectsFromCdn(attachmentsToDelete)?.let { result -> return result }
        attachmentsToDelete.clear()
      }

      // We don't persist attachmentsToDelete, so we can only update the persisted serverCursor if there's no pending deletes
      if (attachmentsToDelete.isEmpty()) {
        serverCursor = archivedItemPage.cursor
      }
    } while (cursor != null)

    if (attachmentsToDelete.isNotEmpty()) {
      deleteMediaObjectsFromCdn(attachmentsToDelete)?.let { result -> return result }
    }

    val entriesNeedingRepairCursor = SignalDatabase.backupMediaSnapshots.getMediaObjectsLastSeenOnCdnBeforeTime(syncTime)
    val needRepairCount = entriesNeedingRepairCursor.count

    if (needRepairCount > 0) {
      Log.w(TAG, "Found $needRepairCount attachments that we thought were uploaded, but could not be found on the CDN. Clearing state and enqueuing uploads.")

      entriesNeedingRepairCursor.forEach {
        val entry = BackupMediaSnapshotTable.MediaEntry.fromCursor(it)
        // TODO [backup] Re-enqueue thumbnail uploads if necessary
        if (!entry.isThumbnail) {
          SignalDatabase.attachments.resetArchiveTransferStateByDigest(entry.digest)
        }
      }

      BackupMessagesJob.enqueue()
    } else {
      Log.d(TAG, "No attachments need to be repaired.")
    }

    SignalStore.backup.lastMediaSyncTime = System.currentTimeMillis()

    return null
  }

  /**
   * Update CDNs of archived media items. Returns list of objects that don't match
   * to a local attachment DB row.
   */
  private fun syncCdnPage(archivedItemPage: ArchiveGetMediaItemsResponse): List<ArchivedMediaObject> {
    val mediaObjects = archivedItemPage.storedMediaObjects.map {
      ArchivedMediaObject(
        mediaId = it.mediaId,
        cdn = it.cdn
      )
    }

    SignalDatabase.backupMediaSnapshots.markSeenOnRemote(
      mediaIdBatch = mediaObjects.map { it.mediaId },
      time = syncTime
    )

    val notFoundMediaObjects = SignalDatabase.backupMediaSnapshots.getMediaObjectsThatCantBeFound(mediaObjects)
    val remainingObjects = mediaObjects - notFoundMediaObjects

    val cdnMismatches = SignalDatabase.backupMediaSnapshots.getMediaObjectsWithNonMatchingCdn(remainingObjects)
    if (cdnMismatches.isNotEmpty()) {
      Log.w(TAG, "Found ${cdnMismatches.size} items with CDNs that differ from what we have locally. Updating our local store.")
      for (mismatch in cdnMismatches) {
        SignalDatabase.attachments.setArchiveCdnByDigest(mismatch.digest, mismatch.cdn)
      }
    }

    return notFoundMediaObjects
  }

  private fun getRemoteArchiveItemPage(cursor: String?): Pair<ArchiveGetMediaItemsResponse?, Result?> {
    return when (val result = BackupRepository.listRemoteMediaObjects(100, cursor)) {
      is NetworkResult.Success -> result.result to null
      is NetworkResult.NetworkError -> return null to Result.retry(defaultBackoff())
      is NetworkResult.StatusCodeError -> {
        if (result.code == 429) {
          Log.w(TAG, "Rate limited while attempting to list media objects. Retrying later.")
          return null to Result.retry(result.retryAfter()?.inWholeMilliseconds ?: defaultBackoff())
        } else {
          Log.w(TAG, "Failed to list remote media objects with code: ${result.code}. Unable to proceed.", result.getCause())
          return null to Result.failure()
        }
      }
      is NetworkResult.ApplicationError -> {
        Log.w(TAG, "Failed to list remote media objects due to a crash.", result.getCause())
        return null to Result.fatalFailure(RuntimeException(result.getCause()))
      }
    }
  }

  private fun deleteMediaObjectsFromCdn(attachmentsToDelete: Set<ArchivedMediaObject>): Result? {
    when (val result = BackupRepository.deleteAbandonedMediaObjects(attachmentsToDelete)) {
      is NetworkResult.Success -> {
        Log.i(TAG, "Successfully deleted ${attachmentsToDelete.size} attachments off of the CDN.")
      }
      is NetworkResult.NetworkError -> {
        return Result.retry(defaultBackoff())
      }
      is NetworkResult.StatusCodeError -> {
        if (result.code == 429) {
          Log.w(TAG, "Rate limited while attempting to delete media objects. Retrying later.")
          return Result.retry(result.retryAfter()?.inWholeMilliseconds ?: defaultBackoff())
        } else {
          Log.w(TAG, "Failed to delete attachments from CDN with code: ${result.code}. Not failing job, just skipping and trying next page.", result.getCause())
        }
      }
      else -> {
        Log.w(TAG, "Crash when trying to delete attachments from the CDN", result.getCause())
        return Result.fatalFailure(RuntimeException(result.getCause()))
      }
    }

    return null
  }

  class Factory : Job.Factory<BackupMediaSnapshotSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupMediaSnapshotSyncJob {
      val data = BackupMediaSnapshotSyncJobData.ADAPTER.decode(serializedData!!)

      return BackupMediaSnapshotSyncJob(
        syncTime = data.syncTime,
        serverCursor = data.serverCursor.nullIfBlank(),
        parameters = parameters
      )
    }
  }
}
