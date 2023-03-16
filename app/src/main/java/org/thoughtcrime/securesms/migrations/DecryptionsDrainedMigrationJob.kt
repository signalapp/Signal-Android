package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.PushDecryptDrainedJob

/**
 * Kicks off a job to notify the [org.thoughtcrime.securesms.messages.IncomingMessageObserver] when the decryption queue is empty.
 */
internal class DecryptionsDrainedMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(DecryptionsDrainedMigrationJob::class.java)
    const val KEY = "DecryptionsDrainedMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    ApplicationDependencies.getJobManager().add(PushDecryptDrainedJob())
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<DecryptionsDrainedMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): DecryptionsDrainedMigrationJob {
      return DecryptionsDrainedMigrationJob(parameters)
    }
  }
}
