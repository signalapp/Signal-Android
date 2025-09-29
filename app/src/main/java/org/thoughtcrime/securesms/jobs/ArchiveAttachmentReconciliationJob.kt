/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.signal.core.util.EventTimer
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.Stopwatch
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
    private const val DELETE_BATCH_SIZE = 10_000

    /**
     * Enqueues a reconciliation job if the retry limit hasn't been exceeded.
     *
     * @param forced If true, forces the job run to bypass any sync interval constraints.
     */
    fun enqueueIfRetryAllowed(forced: Boolean) {
      if (SignalStore.backup.archiveAttachmentReconciliationAttempts < 3) {
        SignalStore.backup.archiveAttachmentReconciliationAttempts++
        AppDependencies.jobManager.add(ArchiveAttachmentReconciliationJob(forced = forced))
      } else {
        Log.i(TAG, "Skip enqueueing reconciliation job: attempt limit exceeded.")
      }
    }
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
      Log.w(TAG, "First ever time we're attempting a reconciliation. Setting the last sync time to now, so we'll run at the proper interval. Skipping this iteration.", true)
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
    val stopwatch = Stopwatch("sync")
    val eventTimer = EventTimer()
    val pendingRemoteDeletes: MutableSet<ArchivedMediaObject> = mutableSetOf()
    do {
      if (isCanceled) {
        Log.w(TAG, "Job cancelled while syncing archived attachments from the CDN.", true)
        return Result.failure()
      }

      val (archivedItemPage, jobResult) = getRemoteArchiveItemPage(serverCursor)
      if (jobResult != null) {
        return jobResult
      }
      check(archivedItemPage != null)

      Log.d(TAG, "Fetched CDN page. Requested size: $CDN_FETCH_LIMIT, Actual size: ${archivedItemPage.storedMediaObjects.size}")

      pendingRemoteDeletes += syncCdnPage(archivedItemPage, snapshotVersion)
      if (pendingRemoteDeletes.size > DELETE_BATCH_SIZE) {
        validateAndDeleteFromRemote(pendingRemoteDeletes)?.let { return it }
        pendingRemoteDeletes.clear()
      }
      eventTimer.emit("page")

      serverCursor = archivedItemPage.cursor
    } while (serverCursor != null)

    if (isCanceled) {
      Log.w(TAG, "Job cancelled while syncing archived attachments from the CDN.", true)
      return Result.failure()
    }
    stopwatch.split("fetch-and-delete")

    if (pendingRemoteDeletes.isNotEmpty()) {
      validateAndDeleteFromRemote(pendingRemoteDeletes)?.let { return it }
      pendingRemoteDeletes.clear()
    }
    stopwatch.split("final-delete")

    Log.d(TAG, eventTimer.stop().summary)

    Log.d(TAG, "BEFORE:\n" + SignalDatabase.attachments.debugGetAttachmentStats().shortPrettyString(), true)
    stopwatch.split("stats-before")

    val mediaObjectsThatMayNeedReUpload = SignalDatabase.backupMediaSnapshots.getMediaObjectsLastSeenOnCdnBeforeSnapshotVersion(snapshotVersion)
    val mayNeedReUploadCount = mediaObjectsThatMayNeedReUpload.count
    stopwatch.split("last-seen")

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
      stopwatch.split("mark-reupload")

      if (bookkeepingErrorCount > 0) {
        Log.w(TAG, "Found that $bookkeepingErrorCount/$mayNeedReUploadCount of the CDN mismatches were bookkeeping errors.", true)
      } else {
        Log.i(TAG, "None of the $mayNeedReUploadCount CDN mismatches were bookkeeping errors.", true)
      }

      Log.d(TAG, "AFTER:\n" + SignalDatabase.attachments.debugGetAttachmentStats().shortPrettyString(), true)
      stopwatch.split("stats-after")

      if (internalUser && mediaIdsThatNeedUpload.isNotEmpty()) {
        Log.w(TAG, "Starting internal-only lookup of matching attachments. May take a while!", true)

        val matchingAttachments = SignalDatabase.attachments.debugGetAttachmentsForMediaIds(mediaIdsThatNeedUpload, limit = 10_000)
        Log.w(TAG, "Found ${matchingAttachments.size} out of the ${mediaIdsThatNeedUpload.size} attachments we looked up (capped lookups to 10k).", true)

        matchingAttachments.forEach { pair ->
          val (attachment, isThumbnail) = pair
          if (isThumbnail) {
            val thumbnailTransferState = SignalDatabase.attachments.getArchiveThumbnailTransferState(attachment.attachmentId)
            Log.w(TAG, "[Thumbnail] Needed Upload: attachmentId=${attachment.attachmentId}, messageId=${attachment.mmsId}, contentType=${attachment.contentType}, quote=${attachment.quote}, transferState=${attachment.transferState}, archiveTransferState=${attachment.archiveTransferState}, archiveThumbnailTransferState=$thumbnailTransferState, hasData=${attachment.hasData}", true)
          } else {
            Log.w(TAG, "[Fullsize] Needed Upload: attachmentId=${attachment.attachmentId}, messageId=${attachment.mmsId}, contentType=${attachment.contentType}, quote=${attachment.quote}, transferState=${attachment.transferState}, archiveTransferState=${attachment.archiveTransferState}, hasData=${attachment.hasData}", true)
          }
        }
        stopwatch.split("internal-lookup")
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
      Log.d(TAG, "No attachments need to be repaired.", true)
    }

    SignalStore.backup.remoteStorageGarbageCollectionPending = false
    SignalStore.backup.lastAttachmentReconciliationTime = System.currentTimeMillis()

    stopwatch.stop(TAG)

    return null
  }

  /**
   * Given a page of archived media items, this method will:
   * - Mark that page as seen on the remote.
   * - Fix any CDN mismatches by updating our local store with the correct CDN.
   * - Delete any orphaned attachments that are on the CDN but not in our local store.
   *
   * @return A list of media objects that should be deleted (after being verified)
   */
  private fun syncCdnPage(archivedItemPage: ArchiveGetMediaItemsResponse, currentSnapshotVersion: Long): Set<ArchivedMediaObject> {
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

    val cdnMismatches = SignalDatabase.backupMediaSnapshots.getMediaObjectsWithNonMatchingCdn(mediaObjectsOnBothRemoteAndLocal)
    if (cdnMismatches.isNotEmpty()) {
      Log.w(TAG, "Found ${cdnMismatches.size} items with CDNs that differ from what we have locally. Updating our local store.")
      for (mismatch in cdnMismatches) {
        SignalDatabase.attachments.setArchiveCdnByPlaintextHashAndRemoteKey(mismatch.plaintextHash, mismatch.remoteKey, mismatch.cdn)
      }
    }

    return mediaOnRemoteButNotLocal
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

  /**
   * Deletes attachments from the archive CDN, after verifying that they also can't be found anywhere in [org.thoughtcrime.securesms.database.AttachmentTable]
   * either. Checking the attachment table is very expensive and independent of query size, which is why we batch the lookups.
   *
   * @return A non-successful [Result] in the case of failure, otherwise null for success.
   */
  private fun validateAndDeleteFromRemote(deletes: Set<ArchivedMediaObject>): Result? {
    val stopwatch = Stopwatch("remote-delete")
    val validatedDeletes = SignalDatabase.attachments.getMediaObjectsThatCantBeFound(deletes)
    Log.d(TAG, "Found that ${validatedDeletes.size}/${deletes.size} requested remote deletes were valid based on current attachment table state.")
    stopwatch.split("validate")

    if (validatedDeletes.isEmpty()) {
      return null
    }

    val deleteResult = ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(TAG, validatedDeletes, this::defaultBackoff, this::isCanceled)
    if (deleteResult != null) {
      Log.w(TAG, "Failed to delete orphaned attachments from the CDN. Returning failure.")
      return deleteResult
    }
    stopwatch.split("network")

    stopwatch.stop(TAG)

    return null
  }

  private fun maybePostReconciliationFailureNotification() {
    if (!RemoteConfig.internalUser) {
      return
    }

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
