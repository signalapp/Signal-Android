package org.thoughtcrime.securesms.migrations

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.MultiDeviceKeysUpdateJob
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * Migration to sync keys with linked devices.
 */
internal class SyncKeysMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    const val KEY = "SyncKeysMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (TextSecurePreferences.isMultiDevice(context)) {
      AppDependencies.jobManager.add(MultiDeviceKeysUpdateJob())
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<SyncKeysMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SyncKeysMigrationJob {
      return SyncKeysMigrationJob(parameters)
    }
  }
}
