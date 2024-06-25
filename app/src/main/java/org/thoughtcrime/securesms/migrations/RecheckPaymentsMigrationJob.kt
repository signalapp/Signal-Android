package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.PaymentLedgerUpdateJob
import org.thoughtcrime.securesms.jobs.PaymentTransactionCheckJob

/**
 * Migration to recheck incoming payments that may have been missed due to db race.
 */
internal class RecheckPaymentsMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    const val KEY = "RecheckPaymentsMigrationJob"

    val TAG = Log.tag(RecheckPaymentsMigrationJob::class.java)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  @Suppress("UsePropertyAccessSyntax")
  override fun performMigration() {
    val jobs: MutableList<Job> = SignalDatabase
      .payments
      .getSubmittedIncomingPayments()
      .filterNotNull()
      .map { PaymentTransactionCheckJob(it) }
      .toMutableList()

    Log.i(TAG, "Rechecking ${jobs.size} payments")
    if (jobs.isNotEmpty()) {
      jobs += PaymentLedgerUpdateJob.updateLedger()
    }
    AppDependencies.jobManager.addAll(jobs)
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<RecheckPaymentsMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RecheckPaymentsMigrationJob {
      return RecheckPaymentsMigrationJob(parameters)
    }
  }
}
