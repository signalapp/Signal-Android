package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.transport.RetryLaterException
import java.lang.Exception
import kotlin.time.Duration.Companion.seconds

/**
 * Optimizes the message search index incrementally.
 */
class OptimizeMessageSearchIndexJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY = "OptimizeMessageSearchIndexJob"

    private val TAG = Log.tag(OptimizeMessageSearchIndexJob::class.java)

    @JvmStatic
    fun enqueue() {
      ApplicationDependencies.getJobManager().add(OptimizeMessageSearchIndexJob())
    }
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue("OptimizeMessageSearchIndexJob")
      .setMaxAttempts(5)
      .setMaxInstancesForQueue(2)
      .build()
  )

  override fun serialize(): ByteArray? = null
  override fun getFactoryKey() = KEY
  override fun onFailure() = Unit
  override fun onShouldRetry(e: Exception) = e is RetryLaterException
  override fun getNextRunAttemptBackoff(pastAttemptCount: Int, exception: Exception): Long = 30.seconds.inWholeMilliseconds

  override fun onRun() {
    if (!SignalStore.registrationValues().isRegistrationComplete || SignalStore.account().aci == null) {
      Log.w(TAG, "Registration not finished yet! Skipping.")
      return
    }

    val success = SignalDatabase.messageSearch.optimizeIndex(5.seconds.inWholeMilliseconds)

    if (!success) {
      throw RetryLaterException()
    }
  }

  class Factory : Job.Factory<OptimizeMessageSearchIndexJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?) = OptimizeMessageSearchIndexJob(parameters)
  }
}
