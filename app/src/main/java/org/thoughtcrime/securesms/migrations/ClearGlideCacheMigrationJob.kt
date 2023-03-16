package org.thoughtcrime.securesms.migrations

import com.bumptech.glide.Glide
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Job

/**
 * Clears the Glide disk cache.
 */
internal class ClearGlideCacheMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(ClearGlideCacheMigrationJob::class.java)
    const val KEY = "ClearGlideCacheMigrationJog"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    Glide.get(context).clearDiskCache()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<ClearGlideCacheMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ClearGlideCacheMigrationJob {
      return ClearGlideCacheMigrationJob(parameters)
    }
  }
}
