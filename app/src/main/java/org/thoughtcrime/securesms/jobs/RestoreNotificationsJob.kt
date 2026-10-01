package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import kotlin.time.Duration.Companion.hours

/** Re-posts still-pending message notifications after the system dropped them. */
class RestoreNotificationsJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    const val KEY = "RestoreNotificationsJob"

    private val TAG = Log.tag(RestoreNotificationsJob::class)

    fun enqueue() {
      AppDependencies.jobManager.add(RestoreNotificationsJob())
    }
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .setMaxInstancesForFactory(1)
      .setLifespan(1.hours.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? = null
  override fun getFactoryKey(): String = KEY
  override fun onFailure() = Unit

  override fun run(): Result {
    Log.i(TAG, "Restoring message notifications.")
    AppDependencies.messageNotifier.updateNotification(context)
    return Result.success()
  }

  class Factory : Job.Factory<RestoreNotificationsJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RestoreNotificationsJob {
      return RestoreNotificationsJob(parameters)
    }
  }
}
