package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.BackfillDigestJob

/**
 * Finds all attachments that need new digests and schedules a [BackfillDigestJob] for each.
 */
internal class BackfillDigestsMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(BackfillDigestsMigrationJob::class.java)
    const val KEY = "BackfillDigestsMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val jobs = SignalDatabase.attachments.getAttachmentsThatNeedNewDigests()
      .map { BackfillDigestJob(it) }

    AppDependencies.jobManager.addAll(jobs)

    Log.i(TAG, "Enqueued ${jobs.size} backfill digest jobs.")
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<BackfillDigestsMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackfillDigestsMigrationJob {
      return BackfillDigestsMigrationJob(parameters)
    }
  }
}
