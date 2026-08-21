/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import org.signal.core.util.DiskUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Optimizes media storage by relying on backups for full copies of files and only keeping thumbnails locally.
 */
class OptimizeMediaJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(OptimizeMediaJob::class)
    const val KEY = "OptimizeMediaJob"

    private const val LOW_STORAGE_THRESHOLD_PERCENT = 5f

    /** How many reconciliation intervals a completed crawl stays trustworthy for. */
    private const val EVIDENCE_AGE_INTERVAL_MULTIPLIER = 4

    fun enqueue() {
      if (!SignalStore.backup.optimizeStorage || !SignalStore.backup.backsUpMedia) {
        Log.i(TAG, "Optimize media is not enabled, skipping. backsUpMedia: ${SignalStore.backup.backsUpMedia} optimizeStorage: ${SignalStore.backup.optimizeStorage}")
        return
      }

      AppDependencies.jobManager.add(OptimizeMediaJob())
    }
  }

  constructor() : this(
    parameters = Parameters.Builder()
      .setQueue("OptimizeMediaJob")
      .setMaxInstancesForQueue(2)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(3)
      .build()
  )

  override fun run(): Result {
    if (!SignalStore.backup.optimizeStorage || !SignalStore.backup.backsUpMedia) {
      Log.i(TAG, "Optimize media is not enabled, aborting. backsUpMedia: ${SignalStore.backup.backsUpMedia} optimizeStorage: ${SignalStore.backup.optimizeStorage}")
      return Result.success()
    }

    if (SignalStore.backup.backupDownloadNotifierState != null) {
      Log.i(TAG, "Backup subscription is pending cancellation, skipping media optimization.")
      return Result.success()
    }

    if (ArchiveRestoreProgress.state.activelyRestoring()) {
      ArchiveRestoreProgress.onCancelMediaRestore()

      Log.i(TAG, "Canceling any previous restore optimized media jobs and cleanup progress")
      AppDependencies.jobManager.cancelAllInQueues(RestoreAttachmentJob.Queues.OFFLOAD_RESTORE)
      RestoreAttachmentJob.Queues.OFFLOAD_RESTORE.forEach { queue -> AppDependencies.jobManager.add(CheckRestoreMediaLeftJob(queue)) }
    }

    val available = DiskUtil.getAvailableSpace(context).bytes.toFloat()
    val total = DiskUtil.getTotalDiskSize(context).bytes.toFloat()
    val percentAvailable = if (total > 0f) available / total * 100 else 100f
    val minimumAge = if (percentAvailable > LOW_STORAGE_THRESHOLD_PERCENT) 30.days else 15.days

    Log.i(TAG, "${"%.1f".format(percentAvailable)}% storage available")

    offloadVerifiedMedia(minimumAge, RemoteConfig.archiveReconciliationSyncInterval)

    // Reclaims files nothing references anymore, which doesn't depend on CDN confirmation, so it has to run even when offloading is gated off.
    Log.i(TAG, "Deleting abandoned attachment files")
    val count = SignalDatabase.attachments.deleteAbandonedAttachmentFiles()
    Log.i(TAG, "Deleted $count attachments")

    return Result.success()
  }

  /** Offloads the local copy of media the archive CDN confirmed during a completed crawl, and nothing else. */
  @VisibleForTesting
  internal fun offloadVerifiedMedia(minimumAge: Duration, reconciliationInterval: Duration) {
    val crawlInterval = reconciliationInterval.coerceAtLeast(1.days)
    val lastCompletedCrawlVersion = SignalStore.backup.lastCompletedReconciliationSnapshotVersion

    if (lastCompletedCrawlVersion < 0) {
      Log.w(TAG, "No archive reconciliation has completed on this device yet. Not offloading anything until our media has been verified against the archive CDN.", true)
      ArchiveAttachmentReconciliationJob.enqueueToVerifyMediaBeforeOffloading(crawlInterval)
      return
    }

    val maxEvidenceAge = crawlInterval * EVIDENCE_AGE_INTERVAL_MULTIPLIER
    val evidenceAge = (System.currentTimeMillis() - SignalStore.backup.lastCompletedReconciliationTime).milliseconds

    // Refusing alone isn't enough here: a far-future timestamp keeps reading as untrustworthy until the clock catches up to it, which could be years.
    if (evidenceAge.isNegative()) {
      Log.w(TAG, "The last completed archive reconciliation is timestamped ${-evidenceAge} in the future, likely from a clock change. Discarding our verification state so a fresh crawl has to confirm our media again.", true)
      SignalStore.backup.clearArchiveVerificationState()
      ArchiveAttachmentReconciliationJob.enqueueToVerifyMediaBeforeOffloading(crawlInterval)
      return
    }

    if (evidenceAge > maxEvidenceAge) {
      Log.w(TAG, "The last completed archive reconciliation is $evidenceAge old, past the $maxEvidenceAge we trust. Not offloading anything until our media has been verified again.", true)
      ArchiveAttachmentReconciliationJob.enqueueToVerifyMediaBeforeOffloading(crawlInterval)
      return
    }

    Log.i(TAG, "Optimizing attachments older than $minimumAge")
    SignalDatabase.attachments.markEligibleAttachmentsAsOptimized(lastCompletedCrawlVersion, minimumAge)
  }

  override fun serialize(): ByteArray? = null
  override fun getFactoryKey(): String = KEY
  override fun onFailure() = Unit

  class Factory : Job.Factory<OptimizeMediaJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): OptimizeMediaJob {
      return OptimizeMediaJob(parameters)
    }
  }
}
