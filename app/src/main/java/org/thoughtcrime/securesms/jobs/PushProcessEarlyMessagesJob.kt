package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.ServiceMessageId
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.messages.MessageContentProcessor
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2
import org.thoughtcrime.securesms.util.EarlyMessageCacheEntry
import org.whispersystems.signalservice.api.messages.SignalServiceContent

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

  override fun serialize(): ByteArray? {
    return null
  }

  override fun onRun() {
    val earlyIds: List<ServiceMessageId> = ApplicationDependencies.getEarlyMessageCache().allReferencedIds
      .filter { SignalDatabase.messages.getMessageFor(it.sentTimestamp, it.sender) != null }
      .sortedBy { it.sentTimestamp }

    if (earlyIds.isNotEmpty()) {
      Log.i(TAG, "There are ${earlyIds.size} items in the early message cache with matches.")

      for (id: ServiceMessageId in earlyIds) {
        val contents: List<SignalServiceContent>? = ApplicationDependencies.getEarlyMessageCache().retrieve(id.sender, id.sentTimestamp).orNull()
        val earlyEntries: List<EarlyMessageCacheEntry>? = ApplicationDependencies.getEarlyMessageCache().retrieveV2(id.sender, id.sentTimestamp).orNull()

        if (contents != null) {
          for (content: SignalServiceContent in contents) {
            Log.i(TAG, "[${id.sentTimestamp}] Processing early content for $id")
            MessageContentProcessor.create(context).processEarlyContent(MessageContentProcessor.MessageState.DECRYPTED_OK, content, null, id.sentTimestamp, -1)
          }
        } else if (earlyEntries != null) {
          for (entry in earlyEntries) {
            Log.i(TAG, "[${id.sentTimestamp}] Processing early V2 content for $id")
            MessageContentProcessorV2.create(context).process(entry.envelope, entry.content, entry.metadata, entry.serverDeliveredTimestamp, processingEarlyContent = true)
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
    override fun create(parameters: Parameters, serializedData: ByteArray?): PushProcessEarlyMessagesJob {
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
