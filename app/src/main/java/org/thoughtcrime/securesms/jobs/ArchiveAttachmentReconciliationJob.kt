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
import arrow.core.Either
import org.signal.core.models.backup.MediaId
import org.signal.core.util.Base64.decodeBase64
import org.signal.core.util.EventTimer
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.Stopwatch
import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.signal.network.api.ArchiveApiV2
import org.signal.network.service.ArchiveError
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferStateResetResult
import org.thoughtcrime.securesms.database.AttachmentTable.MediaNameParts
import org.thoughtcrime.securesms.database.BackupMediaSnapshotTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.ArchiveAttachmentReconciliationJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.wallpaper.WallpaperStorage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

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
) : CoroutineJob(parameters) {

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

    /**
     * Rate-limited because the caller runs after every backup.
     */
    fun enqueueToVerifyMediaBeforeOffloading(minTimeBetweenAttempts: Duration) {
      val sinceLastAttempt = (System.currentTimeMillis() - SignalStore.backup.lastForcedReconciliationAttemptTime).milliseconds

      if (sinceLastAttempt > Duration.ZERO && sinceLastAttempt < minTimeBetweenAttempts) {
        Log.i(TAG, "Already forced a reconciliation $sinceLastAttempt ago. Waiting before forcing another.")
        return
      }

      Log.i(TAG, "Forcing a reconciliation so we can verify our media against the archive CDN.", true)
      AppDependencies.jobManager.add(ArchiveAttachmentReconciliationJob(forced = true))
    }

    /**
     * Reconcile-first entry point for after a local restore. Runs a [BackupMessagesJob] (to capture a snapshot) chained into a forced reconciliation.
     * Sets [BackupValues.localRestoreReconcilePending] so the backup holds off on the bulk attachment backfill, letting reconciliation run first. Reconciliation
     * then clears the flag and re-triggers the backfill for whatever genuinely still needs uploading.
     */
    fun enqueueReconcileFirstForLocalRestore() {
      SignalStore.backup.localRestoreReconcilePending = true
      AppDependencies.jobManager
        .startChain(BackupMessagesJob())
        .then(ArchiveAttachmentReconciliationJob(forced = true))
        .enqueue()
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

  override suspend fun doRun(): Result {
    if (!SignalStore.backup.hasBackupBeenUploaded) {
      Log.w(TAG, "No backup has been uploaded yet! Skipping.")
      return Result.success()
    }

    if (!SignalStore.backup.backsUpMedia) {
      Log.w(TAG, "This user doesn't back up media! Skipping.")
      return Result.success()
    }

    if (!forced && SignalStore.backup.lastAttachmentReconciliationTime < 0) {
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

    if (!AppDependencies.jobManager.areQueuesEmpty(UploadAttachmentToArchiveJob.QUEUES + ArchiveThumbnailUploadJob.QUEUES)) {
      Log.i(TAG, "There are still uploads in progress. Retrying later.")
      return Result.retry(defaultBackoff())
    }

    // It's possible a new backup could be started while this job is running. If we don't keep a consistent view of the snapshot version, the logic
    // we use to determine which attachments need to be re-uploaded will possibly result in us unnecessarily re-uploading attachments.
    snapshotVersion = snapshotVersion ?: SignalDatabase.backupMediaSnapshots.getCurrentSnapshotVersion()

    // Only spend the forced-attempt budget on a crawl that could actually open the offload gate. Without a snapshot it can't, and the rate limit would then keep
    // us from retrying once a backup has built one.
    if (forced && snapshotVersion!! > 0) {
      SignalStore.backup.lastForcedReconciliationAttemptTime = System.currentTimeMillis()
    }

    syncDataFromCdn(snapshotVersion!!)?.let { return it }

    clearPendingLocalRestoreReconcile()
    return Result.success()
  }

  /**
   * Once a reconciliation has fully crawled the CDN, any media that was already archived has been marked finished, so the bulk backfill that was held off
   * during a local restore can proceed for whatever genuinely still needs uploading.
   */
  private fun clearPendingLocalRestoreReconcile() {
    if (SignalStore.backup.localRestoreReconcilePending) {
      Log.i(TAG, "Local restore reconciliation complete. Clearing the pending flag and enqueueing a backup to track and upload any remaining media.", true)
      SignalStore.backup.localRestoreReconcilePending = false
      BackupMessagesJob.enqueue()
    }
  }

  override fun onFailure() {
    clearPendingLocalRestoreReconcile()
  }

  /**
   * Fetches all attachment metadata from the archive CDN and ensures that our local store is in sync with it.
   *
   * Specifically, we make sure that:
   * (1) We delete any attachments from the CDN that we have no knowledge of in any backup.
   * (2) We ensure that our local store has the correct CDN for any attachments on the CDN (they should only really fall out of sync when you restore a backup
   *     that was made before all of the attachments had been uploaded).
   */
  private suspend fun syncDataFromCdn(snapshotVersion: Long): Result? {
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

      val tally = RepairTally()
      val batch = ArrayList<BackupMediaSnapshotTable.MediaEntry>(AttachmentTable.ARCHIVE_MEDIA_KEY_BATCH_SIZE)

      mediaObjectsThatMayNeedReUpload.forEach { mediaObjectCursor ->
        val entry = BackupMediaSnapshotTable.MediaEntry.fromCursor(mediaObjectCursor)
        batch += entry

        if (internalUser) {
          mediaIdsThatNeedUpload += MediaId(entry.mediaId)
        }

        if (batch.size >= AttachmentTable.ARCHIVE_MEDIA_KEY_BATCH_SIZE) {
          repairBatch(batch, tally, internalUser)
          batch.clear()
        }
      }
      repairBatch(batch, tally, internalUser)
      stopwatch.split("mark-reupload")

      if (tally.resetCount > 0) {
        Log.w(TAG, "Found that ${tally.resetCount}/$mayNeedReUploadCount of the CDN mismatches were bookkeeping errors.", true)
        maybePostReconciliationFailureNotification()
      } else {
        Log.i(TAG, "None of the $mayNeedReUploadCount CDN mismatches were bookkeeping errors.", true)
      }

      if (tally.unrecoverableCount > 0) {
        Log.w(TAG, "Found that ${tally.unrecoverableCount}/$mayNeedReUploadCount of the CDN mismatches have no local data to re-upload. That media is not recoverable from this device.", true)
      }

      if (tally.markedUnrecoverableCount > 0) {
        Log.w(TAG, "Marked ${tally.markedUnrecoverableCount}/$mayNeedReUploadCount of the CDN mismatches as permanently failed thumbnails, since there is no local data to rebuild them from. They will stop being tracked until the media is restored.", true)
      }

      if (tally.notNeededCount > 0) {
        Log.i(TAG, "Did not need to reset ${tally.notNeededCount}/$mayNeedReUploadCount of the CDN mismatches, because they either no longer exist or an upload is already in-progress.", true)
      }

      Log.d(TAG, "AFTER:\n" + SignalDatabase.attachments.debugGetAttachmentStats().shortPrettyString(), true)
      stopwatch.split("stats-after")

      if (internalUser && mediaIdsThatNeedUpload.isNotEmpty()) {
        Log.w(TAG, "Starting internal-only lookup of matching attachments. Looking up (showing ${mediaIdsThatNeedUpload.size.coerceAtMost(250)}/${mediaIdsThatNeedUpload.size}): ${mediaIdsThatNeedUpload.take(250).joinToString()}", true)

        val matchingAttachments = SignalDatabase.attachments.getAttachmentDataForMediaIds(mediaIdsThatNeedUpload)
        Log.w(TAG, "Found ${matchingAttachments.size} out of the ${mediaIdsThatNeedUpload.size} attachments we looked up (limiting log input to the first 250).", true)

        matchingAttachments.take(250).forEach { match ->
          if (match.isThumbnail) {
            val thumbnailTransferState = SignalDatabase.attachments.getArchiveThumbnailTransferState(match.attachment.attachmentId)
            Log.w(TAG, "[Thumbnail] Needed Upload: $match, archiveThumbnailTransferState: $thumbnailTransferState", true)
          } else {
            Log.w(TAG, "[Fullsize] Needed Upload: $match", true)
          }
        }
        stopwatch.split("internal-lookup")
      }

      // No backup is started here on purpose. Re-uploading is the whole repair, and [ArchiveUploadProgress] is what decides whether the resulting CDN numbers
      // warrant a fresh export once the backfill finishes uploading.
      if (tally.fullSizeReUploadNeeded) {
        Log.d(TAG, "Full size mismatch found. Enqueuing an attachment backfill job.", true)
        AppDependencies.jobManager.add(ArchiveAttachmentBackfillJob())
      }
      if (tally.thumbnailReUploadNeeded) {
        Log.d(TAG, "Thumbnail mismatch found. Enqueuing a thumbnail backfill job.", true)
        AppDependencies.jobManager.add(ArchiveThumbnailBackfillJob())
      }
    } else {
      Log.d(TAG, "No attachments need to be repaired.", true)
    }

    if (snapshotVersion > 0) {
      val prunedCount = SignalDatabase.backupMediaSnapshots.deleteOldMediaObjectsNeverSeenOnCdn(snapshotVersion)
      if (prunedCount > 0) {
        Log.i(TAG, "Pruned $prunedCount snapshot entries that left the latest snapshot and have never been seen on the CDN.", true)
      }
    }
    stopwatch.split("prune-absent")

    val completionTime = System.currentTimeMillis()

    SignalStore.backup.remoteStorageGarbageCollectionPending = false
    SignalStore.backup.lastAttachmentReconciliationTime = completionTime

    // A crawl with no snapshot to compare against verified nothing, so it must not satisfy the gate that lets us delete local copies of media.
    if (snapshotVersion > 0) {
      SignalStore.backup.lastCompletedReconciliationSnapshotVersion = snapshotVersion
      SignalStore.backup.lastCompletedReconciliationTime = completionTime
    }

    stopwatch.stop(TAG)

    return null
  }

  /**
   * Given a page of archived media items, this method will:
   * - Mark that page as seen on the remote.
   * - Fix any CDN mismatches by updating our local store with the correct CDN.
   * - Delete any orphaned attachments that are on the CDN but not in our local store.
   * - During the local-restore reconcile-first flow, mark media confirmed present on the CDN as finished. A local restore resets everything to NONE (we don't
   *   trust the backup's CDN claims), so this is what promotes the media that genuinely is on the CDN back to finished, preventing a needless re-upload of it.
   *
   * @return A list of media objects that should be deleted (after being verified)
   */
  private fun syncCdnPage(archivedItemPage: ArchiveApiV2.MediaItemsPage, currentSnapshotVersion: Long): Set<ArchivedMediaObject> {
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
      Log.w(TAG, "Found ${cdnMismatches.size} items with CDNs that differ from what we have locally. Updating our local store.", true)
      for (mismatch in cdnMismatches) {
        SignalDatabase.attachments.setArchiveCdnByPlaintextHashAndRemoteKey(mismatch.plaintextHash, mismatch.remoteKey, mismatch.cdn)
      }
    }

    if (SignalStore.backup.localRestoreReconcilePending) {
      val markedFinished = SignalDatabase.attachments.setArchiveFinishedForMatchingMediaObjects(mediaObjectsOnBothRemoteAndLocal.toSet())
      if (markedFinished > 0) {
        Log.i(TAG, "Marked $markedFinished media object group(s) as finished after confirming they are present on the CDN.", true)
      }
    }

    // TODO [cody] Fix perf problems of calling setArchiveThumbnailFinishedForMatchingMediaObjects
    // Takes the whole listing, since a restored device has no thumbnail snapshot rows yet
//    val thumbnailsMarkedFinished = SignalDatabase.attachments.setArchiveThumbnailFinishedForMatchingMediaObjects(mediaObjects.toSet())
//    if (thumbnailsMarkedFinished > 0) {
//      Log.i(TAG, "Marked $thumbnailsMarkedFinished thumbnail group(s) as finished after finding them on the CDN.", true)
//    }

    return mediaOnRemoteButNotLocal
  }

  /**
   * Fetches a page of archived media items from the CDN.
   *
   * @param cursor The cursor to use for pagination, or null to start from the beginning.
   * @return The [ArchiveApiV2.MediaItemsPage] if successful, or null with a [Result] indicating the failure reason.
   */
  private suspend fun getRemoteArchiveItemPage(cursor: String?): Pair<ArchiveApiV2.MediaItemsPage?, Result?> {
    return when (val result = AppDependencies.archiveService.listRemoteMediaObjects(CDN_FETCH_LIMIT, cursor)) {
      is Either.Right -> result.value to null
      is Either.Left -> when (val error = result.value) {
        is ArchiveError.NetworkError -> null to Result.retry(defaultBackoff())

        is ArchiveError.CredentialError.RateLimited -> {
          Log.w(TAG, "Rate limited while attempting to list media objects. Retrying later.", true)
          null to Result.retry(error.retryAfter?.inWholeMilliseconds ?: defaultBackoff())
        }

        is ArchiveError.ApplicationError -> {
          Log.w(TAG, "Failed to list remote media objects due to a crash.", error.exception, true)
          null to Result.fatalFailure(RuntimeException(error.exception))
        }

        is ArchiveError.CredentialError.Unauthorized,
        is ArchiveError.EntitlementError.NotEntitled,
        is ArchiveError.CredentialError.NotFound,
        is ArchiveError.CredentialError.InvalidRequest,
        is ArchiveError.CredentialError.ZkVerificationFailed -> {
          Log.w(TAG, "Failed to list remote media objects: ${error::class.simpleName}. Unable to proceed.", error.cause, true)
          null to Result.failure()
        }
      }
    }
  }

  /**
   * Deletes attachments from the archive CDN, after verifying that they also can't be found anywhere in [org.thoughtcrime.securesms.database.AttachmentTable]
   * either. Checking the attachment table is very expensive and independent of query size, which is why we batch the lookups.
   *
   * Also fixes archive transfer state for attachments that ARE found locally but may have incorrect state
   * (e.g., restored from a backup before archive upload completed).
   *
   * @return A non-successful [Result] in the case of failure, otherwise null for success.
   */
  private suspend fun validateAndDeleteFromRemote(deletes: Set<ArchivedMediaObject>): Result? {
    if (RemoteConfig.internalUser) {
      val mediaIds = deletes.take(250).map { MediaId(it.mediaId.decodeBase64()!!) }
      Log.w(TAG, "Want to delete (showing ${mediaIds.size}/${deletes.size}): ${mediaIds.take(250).joinToString() }")
    }

    val stopwatch = Stopwatch("remote-delete")
    val validatedDeletes: MutableSet<ArchivedMediaObject> = SignalDatabase.attachments.getMediaObjectsThatCantBeFound(deletes, SignalStore.backup.lastUsedMessageCutoffTime).toMutableSet()
    Log.d(TAG, "Found that ${validatedDeletes.size}/${deletes.size} requested remote deletes are no longer referenced by any attachment, and are therefore safe to delete.", true)
    stopwatch.split("validate")

    // Fix archive state for attachments that are found locally but weren't in the latest snapshot.
    // This can happen when restoring from a backup that was made before archive upload completed. The files would be uploaded, but no CDN info would be in the backup.
    val foundLocally = deletes - validatedDeletes

    if (foundLocally.isNotEmpty()) {
      Log.w(TAG, "Starting lookup of attachments that we thought we could delete remotely, but still had record of locally. It may be that we can actually delete them.", true)
      val matches = SignalDatabase.attachments.getAttachmentDataForMediaIds(foundLocally.map { MediaId(it.mediaId) })
      for (match in matches) {
        if (match.messageRecord?.fromRecipient != null && match.messageRecord.fromRecipient.id == SignalStore.releaseChannel.releaseChannelRecipientId) {
          Log.i(TAG, "[${match.attachment.attachmentId}] Attachment is from the release channel. We can delete it remotely.")
          val stringMediaId = match.mediaId.encode()
          validatedDeletes += foundLocally.first { it.mediaId == stringMediaId }
        } else if (match.attachment.mmsId == AttachmentTable.WALLPAPER_MESSAGE_ID && match.isThumbnail) {
          Log.i(TAG, "[${match.attachment.attachmentId}] Attachment is a wallpaper thumbnail. We can delete it remotely.")
          val stringMediaId = match.mediaId.encode()
          validatedDeletes += foundLocally.first { it.mediaId == stringMediaId }
        } else if (match.attachment.mmsId == AttachmentTable.WALLPAPER_MESSAGE_ID && !WallpaperStorage.isWallpaperUriUsed(match.attachment.uri!!)) {
          Log.i(TAG, "[${match.attachment.attachmentId}] Attachment is an unused wallpaper. We can delete it remotely. We'll also delete it locally.")
          val stringMediaId = match.mediaId.encode()
          validatedDeletes += foundLocally.first { it.mediaId == stringMediaId }
          SignalDatabase.attachments.deleteAttachment(match.attachment.attachmentId)
        } else if (RemoteConfig.internalUser) {
          Log.w(TAG, "[PreventedDelete] $match")
        }
      }
      stopwatch.split("lookup")
    }

    val updatedFoundLocally = deletes - validatedDeletes
    if (updatedFoundLocally.isNotEmpty()) {
      val fixedCount = SignalDatabase.attachments.setArchiveFinishedForMatchingMediaObjects(updatedFoundLocally)
      if (fixedCount > 0) {
        Log.i(TAG, "Fixed archive transfer state for $fixedCount attachment groups that were found on CDN but had incorrect local state.", true)
      }
      stopwatch.split("fix-state")
    }

    if (validatedDeletes.isEmpty()) {
      return null
    }

    val deleteResult = ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(TAG, validatedDeletes, this::defaultBackoff, this::isCanceled)
    if (deleteResult != null) {
      Log.w(TAG, "Failed to delete orphaned attachments from the CDN. Returning failure.", true)
      return deleteResult
    }
    stopwatch.split("network")

    // Any snapshot row left behind here would keep claiming an object we just removed, and reviving that row would present it as still CDN-confirmed.
    SignalDatabase.backupMediaSnapshots.deleteOldMediaObjects(validatedDeletes.map { it.mediaId })

    stopwatch.stop(TAG)

    return null
  }

  /**
   * Clears the archive transfer state for a batch of media objects that a crawl couldn't find on the CDN, so they get re-uploaded, folding the outcomes into
   * [tally].
   */
  private fun repairBatch(batch: List<BackupMediaSnapshotTable.MediaEntry>, tally: RepairTally, internalUser: Boolean) {
    if (batch.isEmpty()) {
      return
    }

    for ((isThumbnail, entries) in batch.groupBy { it.isThumbnail }) {
      val mediaNames = entries.map { MediaNameParts.fromBytes(plaintextHash = it.plaintextHash, remoteKey = it.remoteKey) }

      val results = if (isThumbnail) {
        SignalDatabase.attachments.resetArchiveThumbnailTransferStateByPlaintextHashAndRemoteKeyIfNecessary(mediaNames)
      } else {
        SignalDatabase.attachments.resetArchiveTransferStateByPlaintextHashAndRemoteKeyIfNecessary(mediaNames)
      }

      for ((entry, result) in entries.zip(results)) {
        when (result) {
          ArchiveTransferStateResetResult.RESET -> {
            val mediaIdLog = if (internalUser) "[${MediaId(entry.mediaId)}]" else ""
            val logPrefix = if (isThumbnail) "[Thumbnail]$mediaIdLog" else "[Fullsize]$mediaIdLog"
            Log.w(TAG, "$logPrefix Reset transfer state by hash/key.", true)

            tally.resetCount++
            tally.markReUploadNeeded(isThumbnail)
          }

          ArchiveTransferStateResetResult.SKIPPED_NO_LOCAL_DATA -> {
            tally.unrecoverableCount++
          }

          ArchiveTransferStateResetResult.MARKED_UNRECOVERABLE -> {
            tally.markedUnrecoverableCount++
          }

          ArchiveTransferStateResetResult.NOT_NEEDED -> {
            tally.notNeededCount++

            // Deliberately not done for SKIPPED_NO_LOCAL_DATA, since the precautionary backfills these drive could never upload media that has no local bytes.
            tally.markReUploadNeeded(isThumbnail)
          }
        }
      }
    }
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

  private class RepairTally {
    var resetCount = 0
    var unrecoverableCount = 0
    var markedUnrecoverableCount = 0
    var notNeededCount = 0
    var fullSizeReUploadNeeded = false
    var thumbnailReUploadNeeded = false

    fun markReUploadNeeded(isThumbnail: Boolean) {
      if (isThumbnail) {
        thumbnailReUploadNeeded = true
      } else {
        fullSizeReUploadNeeded = true
      }
    }
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
