package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.MultiDeviceKeysUpdateJob
import org.thoughtcrime.securesms.jobs.StorageForcePushJob
import org.thoughtcrime.securesms.jobs.Svr2MirrorJob
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Migration for when we introduce the Account Entropy Pool (AEP).
 */
internal class AepMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(AepMigrationJob::class.java)
    const val KEY = "AepMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "Not registered! Skipping.")
      return
    }

    if (SignalStore.account.isLinkedDevice) {
      Log.i(TAG, "Not primary, skipping.")
      return
    }

    AppDependencies.jobManager.add(Svr2MirrorJob())
    if (SignalStore.account.isMultiDevice) {
      AppDependencies.jobManager.add(MultiDeviceKeysUpdateJob())
    }
    AppDependencies.jobManager.add(StorageForcePushJob())
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<AepMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AepMigrationJob {
      return AepMigrationJob(parameters)
    }
  }
}
