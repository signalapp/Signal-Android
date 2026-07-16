/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.ArchiveAttachmentReconciliationJob
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * There was a bug where media restored from a local backup was incorrectly marked as already being on the archive CDN, which prevented it from ever being
 * uploaded. This migration repairs that state:
 *
 * - Non-media-backup users can't have anything legitimately on the CDN, so we just reset the bogus state locally; the normal backfill re-uploads it if they
 *   later enable media backups.
 * - Media-backup users may have some of that media genuinely on the CDN, so we can't tell locally which entries are bogus, and instead reconcile against it.
 *
 * Reconciliation is expensive server-side, so we only expedite it for media-backup users who are actually in the bad state (i.e. still have local-restore media
 * marked as archived), rather than for everyone.
 */
internal class LocalArchiveReconciliationMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(LocalArchiveReconciliationMigrationJob::class.java)
    const val KEY = "LocalArchiveReconciliationMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (!SignalStore.backup.backsUpMedia) {
      val resetCount = SignalDatabase.attachments.resetArchiveTransferStateForLocalBackupMedia()
      Log.i(TAG, "User does not back up media. Reset $resetCount local-restore attachment(s) incorrectly marked as archived so they'll upload if media backups are enabled later.")
      return
    }

    if (!SignalDatabase.attachments.hasArchiveFinishedLocalBackupMedia()) {
      Log.i(TAG, "No archive-finished media from a local backup. Not in the bad state, so skipping.")
      return
    }

    Log.i(TAG, "Expediting an archive reconciliation to repair any media incorrectly marked as archived after a local restore.")
    ArchiveAttachmentReconciliationJob.enqueueReconcileFirstForLocalRestore()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<LocalArchiveReconciliationMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): LocalArchiveReconciliationMigrationJob {
      return LocalArchiveReconciliationMigrationJob(parameters)
    }
  }
}
