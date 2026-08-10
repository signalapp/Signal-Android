package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.CheckKeyTransparencyJob
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Previously had a bug in KT that affected those with usernames who did a device transfer.
 * Check if there is a KT failure, and reset it so that KT will start again.
 */
internal class KeyTransparencyFailureMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {

    const val KEY = "KeyTransparencyFailureMigrationJob"

    private val TAG: String = tag(KeyTransparencyFailureMigrationJob::class.java)
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    if (SignalStore.misc.hasKeyTransparencyFailure) {
      Log.i(TAG, "Has KT failure, resetting and enqueuing again")
      SignalStore.account.distinguishedHead = null
      SignalStore.misc.nextKeyTransparencyTime = 0
      SignalDatabase.recipients.clearAllKeyTransparencyData()
      CheckKeyTransparencyJob.enqueueIfNecessary(addDelay = false, force = true)
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<KeyTransparencyFailureMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): KeyTransparencyFailureMigrationJob {
      return KeyTransparencyFailureMigrationJob(parameters)
    }
  }
}
