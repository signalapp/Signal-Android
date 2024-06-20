package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.MultiDeviceKeysUpdateJob
import org.thoughtcrime.securesms.jobs.StorageSyncJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * Remove local unknown storage ids not in local storage service manifest.
 */
internal class StorageFixLocalUnknownMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(StorageFixLocalUnknownMigrationJob::class.java)
    const val KEY = "StorageFixLocalUnknownMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  @Suppress("UsePropertyAccessSyntax")
  override fun performMigration() {
    val localStorageIds = SignalStore.storageService.getManifest().storageIds.toSet()
    val unknownLocalIds = SignalDatabase.unknownStorageIds.getAllUnknownIds().toSet()
    val danglingLocalUnknownIds = unknownLocalIds - localStorageIds

    if (danglingLocalUnknownIds.isEmpty()) {
      return
    }

    Log.w(TAG, "Removing ${danglingLocalUnknownIds.size} dangling unknown ids")

    SignalDatabase.rawDatabase.withinTransaction {
      SignalDatabase.unknownStorageIds.delete(danglingLocalUnknownIds)
    }

    val jobManager = AppDependencies.jobManager

    if (TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Multi-device.")
      jobManager.startChain(StorageSyncJob())
        .then(MultiDeviceKeysUpdateJob())
        .enqueue()
    } else {
      Log.i(TAG, "Single-device.")
      jobManager.add(StorageSyncJob())
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<StorageFixLocalUnknownMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): StorageFixLocalUnknownMigrationJob {
      return StorageFixLocalUnknownMigrationJob(parameters)
    }
  }
}
