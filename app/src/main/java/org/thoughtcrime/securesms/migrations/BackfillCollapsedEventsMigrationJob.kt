package org.thoughtcrime.securesms.migrations

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.BackfillCollapsedMessageJob

/**
 * Schedules the [BackfillCollapsedMessageJob] to run.
 */
internal class BackfillCollapsedEventsMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {
    const val KEY = "BackfillCollapsedEventsMigrationJob"
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    val jobManager = AppDependencies.jobManager
    jobManager.add(BackfillCollapsedMessageJob())
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<BackfillCollapsedEventsMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackfillCollapsedEventsMigrationJob {
      return BackfillCollapsedEventsMigrationJob(parameters)
    }
  }
}
