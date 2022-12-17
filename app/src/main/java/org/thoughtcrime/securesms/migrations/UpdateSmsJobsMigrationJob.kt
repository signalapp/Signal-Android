package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Data.Serializer
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Updates the data in queued jobs to reflect the new ids SMS messages get assigned during the table merge migration.
 * Normally we'd do this in a JobManager migration, but unfortunately this migration requires that a database migration
 * happened already, but we don't want the database to be accessed until the [DatabaseMigrationJob] is run, otherwise
 * we won't show the progress update.
 *
 * This ends up being more straightforward regardless because by the time this application migration is being run, it must be the
 * case that the database migration is finished (since it's enqueued after the [DatabaseMigrationJob]), so we don't have to
 * do any weird wait-notify stuff to guarantee the offset is set.
 */
internal class UpdateSmsJobsMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(UpdateSmsJobsMigrationJob::class.java)
    const val KEY = "UpdateSmsJobsMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val idOffset = SignalStore.plaintext().smsMigrationIdOffset
    check(idOffset >= 0) { "Invalid ID offset of $idOffset -- this shouldn't be possible!" }

    ApplicationDependencies.getJobManager().update { jobSpec, serializer ->
      when (jobSpec.factoryKey) {
        "PushTextSendJob" -> jobSpec.updateAndSerialize(serializer, "message_id", null, idOffset)
        "ReactionSendJob" -> jobSpec.updateAndSerialize(serializer, "message_id", "is_mms", idOffset)
        "RemoteDeleteSendJob" -> jobSpec.updateAndSerialize(serializer, "message_id", "is_mms", idOffset)
        "SmsSendJob" -> jobSpec.updateAndSerialize(serializer, "message_id", null, idOffset)
        "SmsSentJob" -> jobSpec.updateAndSerialize(serializer, "message_id", null, idOffset)
        else -> jobSpec
      }
    }
  }

  private fun JobSpec.updateAndSerialize(serializer: Serializer, idKey: String, isMmsKey: String?, offset: Long): JobSpec {
    val data = serializer.deserialize(this.serializedData)

    if (isMmsKey != null && data.getBooleanOrDefault(isMmsKey, false)) {
      return this
    }

    return if (data.hasLong(idKey)) {
      val currentValue: Long = data.getLong(idKey)
      val updatedValue: Long = currentValue + offset
      val updatedData: Data = data.buildUpon().putLong(idKey, updatedValue).build()

      Log.d(TAG, "Updating job with factory ${this.factoryKey} from $currentValue to $updatedValue")
      this.withData(serializer.serialize(updatedData))
    } else {
      this
    }
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<UpdateSmsJobsMigrationJob> {
    override fun create(parameters: Parameters, data: Data): UpdateSmsJobsMigrationJob {
      return UpdateSmsJobsMigrationJob(parameters)
    }
  }
}
