package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.ServiceMessageId
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.messages.MessageContentProcessor
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import java.lang.Exception
import java.util.Optional

/**
 * A job that should be enqueued whenever we process a message that we think has arrived "early" (see [org.thoughtcrime.securesms.util.EarlyMessageCache]).
 * It will go through and process all of those early messages (if we have found a "match"), ordered by sentTimestamp.
 */
class PushProcessEarlyMessagesJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  private constructor() :
    this(
      Parameters.Builder()
        .setMaxInstancesForFactory(2)
        .setMaxAttempts(1)
        .setLifespan(Parameters.IMMORTAL)
        .build()
    )

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun serialize(): Data {
    return Data.EMPTY
  }

  override fun onRun() {
    val earlyIds: List<ServiceMessageId> = ApplicationDependencies.getEarlyMessageCache().allReferencedIds
      .filter { SignalDatabase.messages.getMessageFor(it.sentTimestamp, it.sender) != null }
      .sortedBy { it.sentTimestamp }

    if (earlyIds.isNotEmpty()) {
      Log.i(TAG, "There are ${earlyIds.size} items in the early message cache with matches.")

      for (id: ServiceMessageId in earlyIds) {
        val contents: Optional<List<SignalServiceContent>> = ApplicationDependencies.getEarlyMessageCache().retrieve(id.sender, id.sentTimestamp)

        if (contents.isPresent) {
          for (content: SignalServiceContent in contents.get()) {
            Log.i(TAG, "[${id.sentTimestamp}] Processing early content for $id")
            MessageContentProcessor.forEarlyContent(context).process(MessageContentProcessor.MessageState.DECRYPTED_OK, content, null, id.sentTimestamp, -1)
          }
        } else {
          Log.w(TAG, "[${id.sentTimestamp}] Saw $id in the cache, but when we went to retrieve it, it was already gone.")
        }
      }
    } else {
      Log.i(TAG, "There are no items in the early message cache with matches.")
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return false
  }

  override fun onFailure() {
  }

  class Factory : Job.Factory<PushProcessEarlyMessagesJob> {
    override fun create(parameters: Parameters, data: Data): PushProcessEarlyMessagesJob {
      return PushProcessEarlyMessagesJob(parameters)
    }
  }

  companion object {
    private val TAG = Log.tag(PushProcessEarlyMessagesJob::class.java)

    const val KEY = "PushProcessEarlyMessageJob"

    /**
     * Enqueues a job to run after the most-recently-enqueued [PushProcessMessageJob].
     */
    @JvmStatic
    fun enqueue() {
      val jobManger = ApplicationDependencies.getJobManager()

      val youngestProcessJobId: String? = jobManger.find { it.factoryKey == PushProcessMessageJob.KEY }
        .maxByOrNull { it.createTime }
        ?.id

      if (youngestProcessJobId != null) {
        jobManger.add(PushProcessEarlyMessagesJob(), listOf(youngestProcessJobId))
      } else {
        jobManger.add(PushProcessEarlyMessagesJob())
      }
    }
  }
}
