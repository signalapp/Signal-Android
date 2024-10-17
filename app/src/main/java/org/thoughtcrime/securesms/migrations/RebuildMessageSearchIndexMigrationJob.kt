package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job

/**
 * Rebuilds the full-text search index for the messages table.
 */
@Deprecated("Do not use! Perform the index rebuild synchronously instead.")
internal class RebuildMessageSearchIndexMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(RebuildMessageSearchIndexMigrationJob::class.java)
    const val KEY = "RebuildMessageSearchIndexMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val startTime = System.currentTimeMillis()

    val success = SignalDatabase.messageSearch.rebuildIndex()

    if (!success) {
      Log.w(TAG, "Failed to rebuild search index. Resetting tables. That will enqueue a job to reset the index as a side-effect.")
      SignalDatabase.messageSearch.fullyResetTables()
    }

    Log.d(TAG, "It took ${System.currentTimeMillis() - startTime} ms to rebuild the search index.")
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<RebuildMessageSearchIndexMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RebuildMessageSearchIndexMigrationJob {
      return RebuildMessageSearchIndexMigrationJob(parameters)
    }
  }
}
