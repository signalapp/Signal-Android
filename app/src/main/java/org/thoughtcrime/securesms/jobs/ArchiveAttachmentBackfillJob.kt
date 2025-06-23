/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.time.Duration.Companion.days

/**
 * When run, this will find all of the attachments that need to be uploaded to the archive tier and enqueue [UploadAttachmentToArchiveJob]s for them.
 */
class ArchiveAttachmentBackfillJob private constructor(parameters: Parameters) : Job(parameters) {
  companion object {
    private val TAG = Log.tag(ArchiveAttachmentBackfillJob::class.java)

    const val KEY = "ArchiveAttachmentBackfillJob"
  }

  constructor() : this(
    parameters = Parameters.Builder()
      .setQueue("ArchiveAttachmentBackfillJob")
      .setMaxInstancesForQueue(2)
      .setLifespan(30.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (!SignalStore.backup.backsUpMedia) {
      Log.w(TAG, "This user doesn't back up media! Skipping. Tier: ${SignalStore.backup.backupTier}")
      return Result.success()
    }

    val jobs = SignalDatabase.attachments.getAttachmentsThatNeedArchiveUpload()
      .map { attachmentId -> UploadAttachmentToArchiveJob(attachmentId) }

    SignalDatabase.attachments.createKeyIvDigestForAttachmentsThatNeedArchiveUpload()

    ArchiveUploadProgress.onAttachmentsStarted(SignalDatabase.attachments.getPendingArchiveUploadBytes())

    if (!isCanceled) {
      Log.i(TAG, "Adding ${jobs.size} jobs to backfill attachments.")
      AppDependencies.jobManager.addAll(jobs)
    } else {
      Log.w(TAG, "Job was canceled. Not enqueuing backfill.")
    }

    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<ArchiveAttachmentBackfillJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ArchiveAttachmentBackfillJob {
      return ArchiveAttachmentBackfillJob(parameters)
    }
  }
}
