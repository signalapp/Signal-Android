package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.ArchiveBackupIdReservationJob

/**
 * Simple migration job to just enqueue a [ArchiveBackupIdReservationJob] to ensure that all users reserve a backupId.
 */
internal class ArchiveBackupIdReservationMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(ArchiveBackupIdReservationMigrationJob::class.java)
    const val KEY = "ArchiveBackupIdReservationMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    AppDependencies.jobManager.add(ArchiveBackupIdReservationJob())
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<ArchiveBackupIdReservationMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ArchiveBackupIdReservationMigrationJob {
      return ArchiveBackupIdReservationMigrationJob(parameters)
    }
  }
}
