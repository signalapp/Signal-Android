package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Schedules a prekey sync.
 */
internal class PreKeysSyncMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(PreKeysSyncMigrationJob::class.java)
    const val KEY = "PreKeysSyncIndexMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    SignalStore.misc.lastFullPrekeyRefreshTime = 0
    PreKeysSyncJob.enqueue()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<PreKeysSyncMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PreKeysSyncMigrationJob {
      return PreKeysSyncMigrationJob(parameters)
    }
  }
}
