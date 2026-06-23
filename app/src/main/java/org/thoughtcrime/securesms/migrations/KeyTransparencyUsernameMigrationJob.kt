package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Reuploads user's attributes followed by a download of their profile and a reset of their KT data
 */
internal class KeyTransparencyUsernameMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {

    const val KEY = "KeyTransparencyUsernameMigrationJob"

    private val TAG: String = tag(KeyTransparencyUsernameMigrationJob::class.java)
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    Log.i(TAG, "Resetting KT data and refreshing attributes")
    SignalStore.account.distinguishedHead = null
    SignalStore.misc.nextKeyTransparencyTime = 0
    SignalDatabase.recipients.clearAllKeyTransparencyData()

    AppDependencies.jobManager.startChain(RefreshAttributesJob())
      .then(RefreshOwnProfileJob())
      .enqueue()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<KeyTransparencyUsernameMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): KeyTransparencyUsernameMigrationJob {
      return KeyTransparencyUsernameMigrationJob(parameters)
    }
  }
}
