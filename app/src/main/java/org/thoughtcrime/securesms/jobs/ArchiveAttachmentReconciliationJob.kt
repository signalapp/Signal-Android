/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.ArchiveAttachmentReconciliationJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse
import org.whispersystems.signalservice.api.backup.MediaId
import java.lang.RuntimeException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * We do our best to keep our local attachments in sync with the archive CDN, but we still want to have a backstop that periodically
 * checks to make sure things are in sync, and corrects it if it isn't.
 *
 * Specifically, this job does three important things:
 *
 * 1. Ensures that orphaned attachments on the CDN (i.e. attachments that are on the CDN but are no longer tied to the most recent backup) are deleted.
 * 2. Ensures that attachments we thought were uploaded to the CDN, but are no longer there, are re-uploaded.
 * 3. Keeps the CDN numbers in sync with our local database. There are known situation after the initial restore when we actually don't know the CDN, and after
 *    the initial restore, there's always the change that something falls out of sync, so may as well check it then as well since we're getting the data anyway.
 */
class ArchiveAttachmentReconciliationJob private constructor(
  private var snapshotVersion: Long?,
  private var serverCursor: String?,
  private val forced: Boolean,
  parameters: Parameters
) : Job(parameters) {

  companion object {

    private val TAG = Log.tag(ArchiveAttachmentReconciliationJob::class)

    const val KEY = "ArchiveAttachmentReconciliationJob"

    private const val CDN_FETCH_LIMIT = 10_000
  }

  constructor(forced: Boolean = false) : this(
    snapshotVersion = null,
    serverCursor = null,
    forced = forced,
    parameters = Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setQueue(ArchiveCommitAttachmentDeletesJob.ARCHIVE_ATTACHMENT_QUEUE)
      .setMaxInstancesForQueue(2)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(1.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray = ArchiveAttachmentReconciliationJobData(
    snapshot = snapshotVersion,
    serverCursor = serverCursor ?: "",
    forced = forced
  ).encode()

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (!SignalStore.backup.hasBackupBeenUploaded) {
      Log.w(TAG, "No backup has been uploaded yet! Skipping.")
      return Result.success()
    }

    if (!SignalStore.backup.backsUpMedia) {
      Log.w(TAG, "This user doesn't back up media! Skipping.")
      return Result.success()
    }

    if (SignalStore.backup.lastAttachmentReconciliationTime < 0) {
      Log.w(TAG, "First ever time we're attempting a reconciliation. Setting the last sync time to now, so we'll run at the proper interval. Skipping this iteration.")
      SignalStore.backup.lastAttachmentReconciliationTime = System.currentTimeMillis()
      return Result.success()
    }

    val timeSinceLastSync = System.currentTimeMillis() - SignalStore.backup.lastAttachmentReconciliationTime
    val syncThreshold = if (RemoteConfig.internalUser) 12.hours.inWholeMilliseconds else RemoteConfig.archiveReconciliationSyncInterval.inWholeMilliseconds
    if (!forced && serverCursor == null && timeSinceLastSync > 0 && timeSinceLastSync < syncThreshold) {
      Log.d(TAG, "No need to do a remote sync yet. Time since last sync: $timeSinceLastSync ms")
      return Result.success()
    }

    // It's possible a new backup could be started while this job is running. If we don't keep a consistent view of the snapshot version, the logic
    // we use to determine which attachments need to be re-uploaded will possibly result in us unnecessarily re-uploading attachments.
    snapshotVersion = snapshotVersion ?: SignalDatabase.backupMediaSnapshots.getCurrentSnapshotVersion()

    return syncDataFromCdn(snapshotVersion!!) ?: Result.success()
  }

  override fun onFailure() = Unit

  /**
   * Fetches all attachment metadata from the archive CDN and ensures that our local store is in sync with it.
   *
   * Specifically, we make sure that:
   * (1) We delete any attachments from the CDN that we have no knowledge of in any backup.
   * (2) We ensure that our local store has the correct CDN for any attachments on the CDN (they should only really fall out of sync when you restore a backup
   *     that was made before all of the attachments had been uploaded).
   */
  private fun syncDataFromCdn(snapshotVersion: Long): Result? {
    do {
      if (isCanceled) {
        Log.w(TAG, "Job cancelled while syncing archived attachments from the CDN.")
        return Result.failure()
      }

      val (archivedItemPage, jobResult) = getRemoteArchiveItemPage(serverCursor)
      if (jobResult != null) {
        return jobResult
      }
      check(archivedItemPage != null)

      Log.d(TAG, "Fetched CDN page. Requested size: $CDN_FETCH_LIMIT, Actual size: ${archivedItemPage.storedMediaObjects.size}")

      syncCdnPage(archivedItemPage, snapshotVersion)?.let { return it }

      serverCursor = archivedItemPage.cursor
    } while (serverCursor != null)

    if (isCanceled) {
      Log.w(TAG, "Job cancelled while syncing archived attachments from the CDN.")
      return Result.failure()
    }

    val mediaObjectsThatMayNeedReUpload = SignalDatabase.backupMediaSnapshots.getMediaObjectsLastSeenOnCdnBeforeSnapshotVersion(snapshotVersion)
    val mayNeedReUploadCount = mediaObjectsThatMayNeedReUpload.count

    val mediaIdsThatNeedUpload = mutableSetOf<MediaId>()
    val internalUser = RemoteConfig.internalUser

    if (mayNeedReUploadCount > 0) {
      Log.w(TAG, "Found $mayNeedReUploadCount attachments that are present in the target snapshot, but could not be found on the CDN. This could be a bookkeeping error, or the upload may still be in progress. Checking.", true)

      var newBackupJobRequired = false
      var bookkeepingErrorCount = 0

      var fullSizeMismatchFound = false
      var thumbnailMismatchFound = false

      mediaObjectsThatMayNeedReUpload.forEach { mediaObjectCursor ->
        val entry = BackupMediaSnapshotTable.MediaEntry.fromCursor(mediaObjectCursor)

        if (internalUser) {
          mediaIdsThatNeedUpload += MediaId(entry.mediaId)
        }

        if (entry.isThumbnail) {
          thumbnailMismatchFound = true
          val wasReset = SignalDatabase.attachments.resetArchiveThumbnailTransferStateByPlaintextHashAndRemoteKeyIfNecessary(entry.plaintextHash, entry.remoteKey)
          if (wasReset) {
            newBackupJobRequired = true
            bookkeepingErrorCount++
          } else {
            Log.w(TAG, "[Thumbnail] Did not need to reset the transfer state by hash/key because the thumbnail either no longer exists or the upload is already in-progress.", true)
          }
        } else {
          fullSizeMismatchFound = true
          val wasReset = SignalDatabase.attachments.resetArchiveTransferStateByPlaintextHashAndRemoteKeyIfNecessary(entry.plaintextHash, entry.remoteKey)
          if (wasReset) {
            newBackupJobRequired = true
            bookkeepingErrorCount++
          } else {
            Log.w(TAG, "[Fullsize] Did not need to reset the the transfer state by hash/key because the attachment either no longer exists or the upload is already in-progress.", true)
          }
        }
      }

      if (bookkeepingErrorCount > 0) {
        Log.w(TAG, "Found that $bookkeepingErrorCount/$mayNeedReUploadCount of the CDN mismatches were bookkeeping errors.", true)
      } else {
        Log.i(TAG, "None of the $mayNeedReUploadCount CDN mismatches were bookkeeping errors.", true)
      }

      if (internalUser && mediaIdsThatNeedUpload.isNotEmpty()) {
        Log.w(TAG, "Starting internal-only lookup of matching attachments. May take a while!")

        val matchingAttachments = SignalDatabase.attachments.debugGetAttachmentsForMediaIds(mediaIdsThatNeedUpload, limit = 100)
        Log.w(TAG, "Found ${matchingAttachments.size} out of the ${mediaIdsThatNeedUpload.size} attachments we looked up (capped lookups to 100).", true)

        matchingAttachments.forEach { attachment ->
          Log.w(TAG, "Needed Upload: attachmentId=${attachment.first.attachmentId}, messageId=${attachment.first.mmsId}, thumbnail=${attachment.second}, contentType=${attachment.first.contentType}, quote=${attachment.first.quote}, transferState=${attachment.first.transferState}, archiveTransferState=${attachment.first.archiveTransferState}, hasData=${attachment.first.hasData}")
        }
      }

      if (newBackupJobRequired) {
        Log.w(TAG, "Some of the errors require re-uploading a new backup job to resolve.", true)
        maybePostReconciliationFailureNotification()
        BackupMessagesJob.enqueue()
      } else {
        if (fullSizeMismatchFound) {
          Log.d(TAG, "Full size mismatch found. Enqueuing an attachment backfill job to be safe.", true)
          AppDependencies.jobManager.add(ArchiveAttachmentBackfillJob())
        }
        if (thumbnailMismatchFound) {
          Log.d(TAG, "Thumbnail mismatch found. Enqueuing a thumbnail backfill job to be safe.", true)
          AppDependencies.jobManager.add(ArchiveThumbnailBackfillJob())
        }
      }
    } else {
      Log.d(TAG, "No attachments need to be repaired.")
    }

    SignalStore.backup.remoteStorageGarbageCollectionPending = false
    SignalStore.backup.lastAttachmentReconciliationTime = System.currentTimeMillis()

    return null
  }

  /**
   * Given a page of archived media items, this method will:
   * - Mark that page as seen on the remote.
   * - Fix any CDN mismatches by updating our local store with the correct CDN.
   * - Delete any orphaned attachments that are on the CDN but not in our local store.
   *
   * @return Null if successful, or a [Result] indicating the failure reason.
   */
  private fun syncCdnPage(archivedItemPage: ArchiveGetMediaItemsResponse, currentSnapshotVersion: Long): Result? {
    val mediaObjects = archivedItemPage.storedMediaObjects.map {
      ArchivedMediaObject(
        mediaId = it.mediaId,
        cdn = it.cdn
      )
    }

    SignalDatabase.backupMediaSnapshots.markSeenOnRemote(
      mediaIdBatch = mediaObjects.map { it.mediaId },
      snapshotVersion = currentSnapshotVersion
    )

    val mediaOnRemoteButNotLocal = SignalDatabase.backupMediaSnapshots.getMediaObjectsThatCantBeFound(mediaObjects)
    val mediaObjectsOnBothRemoteAndLocal = mediaObjects - mediaOnRemoteButNotLocal

    if (RemoteConfig.internalUser && mediaOnRemoteButNotLocal.isNotEmpty()) {
      Log.w(TAG, "MediaIds of items on remote but not local: ${mediaOnRemoteButNotLocal.joinToString(", ") { it.mediaId }}", true)
    }

    val cdnMismatches = SignalDatabase.backupMediaSnapshots.getMediaObjectsWithNonMatchingCdn(mediaObjectsOnBothRemoteAndLocal)
    if (cdnMismatches.isNotEmpty()) {
      Log.w(TAG, "Found ${cdnMismatches.size} items with CDNs that differ from what we have locally. Updating our local store.")
      for (mismatch in cdnMismatches) {
        SignalDatabase.attachments.setArchiveCdnByPlaintextHashAndRemoteKey(mismatch.plaintextHash, mismatch.remoteKey, mismatch.cdn)
      }
    }

    val deleteResult = ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(TAG, mediaOnRemoteButNotLocal, this::defaultBackoff, this::isCanceled)
    if (deleteResult != null) {
      Log.w(TAG, "Failed to delete orphaned attachments from the CDN. Returning failure.")
      return deleteResult
    }

    return null
  }

  /**
   * Fetches a page of archived media items from the CDN.
   *
   * @param cursor The cursor to use for pagination, or null to start from the beginning.
   * @return The [ArchiveGetMediaItemsResponse] if successful, or null with a [Result] indicating the failure reason.
   */
  private fun getRemoteArchiveItemPage(cursor: String?): Pair<ArchiveGetMediaItemsResponse?, Result?> {
    return when (val result = BackupRepository.listRemoteMediaObjects(CDN_FETCH_LIMIT, cursor)) {
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

  private fun maybePostReconciliationFailureNotification() {
    if (!RemoteConfig.internalUser) {
      return
    }

    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("[Internal-only] Archive reconciliation found an error!")
      .setContentText("Tap to send a debug log")
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.RECONCILIATION_ERROR, notification)
  }

  class Factory : Job.Factory<ArchiveAttachmentReconciliationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ArchiveAttachmentReconciliationJob {
      val data = ArchiveAttachmentReconciliationJobData.ADAPTER.decode(serializedData!!)

      return ArchiveAttachmentReconciliationJob(
        snapshotVersion = data.snapshot,
        serverCursor = data.serverCursor.nullIfBlank(),
        forced = data.forced,
        parameters = parameters
      )
    }
  }
}
