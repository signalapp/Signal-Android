package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.signal.core.util.update
import org.thoughtcrime.securesms.database.CollapsedState
import org.thoughtcrime.securesms.database.CollapsibleEvents
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.BackfillCollapsedMessageJob.Companion.BATCH_SIZE
import org.thoughtcrime.securesms.jobs.protos.BackfillCollapsedMessageJobData
import org.thoughtcrime.securesms.util.DateUtils

/**
 * Backfills the collapsed state of chat events. Runs for [BATCH_SIZE] messages, then re-enqueues itself
 * to allow other jobs to run, until the backfill is complete.
 */
class BackfillCollapsedMessageJob private constructor(
  private val lastDateReceived: Long,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY = "BackfillCollapsedMessageJob"
    val TAG = Log.tag(BackfillCollapsedMessageJob::class)
    private const val BATCH_SIZE = 5_000
  }

  constructor() : this(lastDateReceived = 1)

  constructor(lastDateReceived: Long) : this(
    lastDateReceived = lastDateReceived,
    parameters = Parameters.Builder()
      .setQueue(KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setGlobalPriority(Parameters.PRIORITY_LOWER)
      .build()
  )

  override fun serialize(): ByteArray {
    return BackfillCollapsedMessageJobData(lastDateReceived = lastDateReceived).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val db = SignalDatabase.rawDatabase

    val messages = db
      .select(MessageTable.ID, MessageTable.THREAD_ID, MessageTable.DATE_RECEIVED, MessageTable.TYPE, MessageTable.READ, MessageTable.COLLAPSED_STATE, MessageTable.MESSAGE_EXTRAS)
      .from(MessageTable.TABLE_NAME)
      .where("${MessageTable.DATE_RECEIVED} > ?", lastDateReceived)
      .orderBy("${MessageTable.DATE_RECEIVED}, ${MessageTable.ID}")
      .limit(BATCH_SIZE)
      .run()
      .readToList { cursor ->
        PotentialCollapsibleMessage(
          id = cursor.requireLong(MessageTable.ID),
          threadId = cursor.requireLong(MessageTable.THREAD_ID),
          type = cursor.requireLong(MessageTable.TYPE),
          dateReceived = cursor.requireLong(MessageTable.DATE_RECEIVED),
          collapsedState = cursor.requireLong(MessageTable.COLLAPSED_STATE),
          read = cursor.requireBoolean(MessageTable.READ),
          messageExtras = cursor.requireBlob(MessageTable.MESSAGE_EXTRAS)?.let { MessageExtras.ADAPTER.decode(it) }
        )
      }

    // Tracks the last/previous message to compare against the current message when determining collapsed state
    val lastMessageByThread = mutableMapOf<Long, LastMessage?>()
    for (message in messages) {
      val collapsibleType = CollapsibleEvents.getCollapsibleType(message.type, message.messageExtras)

      if (collapsibleType == null) {
        lastMessageByThread[message.threadId] = null
      } else {
        val previous = lastMessageByThread[message.threadId]

        val (collapsedState, headId, size) = if ((previous?.collapsibleType == collapsibleType) && DateUtils.isSameDay(previous.dateReceived, message.dateReceived) && previous.collapsedSetSize < CollapsibleEvents.MAX_SIZE) {
          val state = if (message.read) CollapsedState.COLLAPSED.id else CollapsedState.PENDING_COLLAPSED.id
          Triple(state, previous.headId, previous.collapsedSetSize)
        } else {
          Triple(CollapsedState.HEAD_COLLAPSED.id, message.id, 0)
        }

        db.update(MessageTable.TABLE_NAME)
          .values(
            MessageTable.COLLAPSED_STATE to collapsedState,
            MessageTable.COLLAPSED_HEAD_ID to headId
          )
          .where("${MessageTable.ID} = ?", message.id)
          .run()
        lastMessageByThread[message.threadId] = LastMessage(collapsibleType, headId, message.dateReceived, size + 1)
      }
    }

    if (messages.isEmpty() || messages.size != BATCH_SIZE) {
      Log.i(TAG, "Finished processing all messages, backfill is completed")
    } else {
      val dateReceived = messages.last().dateReceived
      Log.i(TAG, "Processed ${messages.size} messages, up to time $dateReceived. Re-enqueuing job")
      AppDependencies.jobManager.add(BackfillCollapsedMessageJob(lastDateReceived = dateReceived))
    }

    return Result.success()
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to backfill collapsed messages. Time of last processed message: $lastDateReceived")
  }

  /**
   * Data required from a message to know if it collapsible
   */
  private data class PotentialCollapsibleMessage(
    val id: Long,
    val threadId: Long,
    val type: Long,
    val dateReceived: Long,
    val collapsedState: Long,
    val read: Boolean,
    val messageExtras: MessageExtras?
  )

  /**
   * Information about the previous message, used when deciding the collapsible state of the next
   */
  private data class LastMessage(
    val collapsibleType: CollapsibleEvents.CollapsibleType?,
    val headId: Long,
    val dateReceived: Long,
    val collapsedSetSize: Int
  )

  class Factory : Job.Factory<BackfillCollapsedMessageJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackfillCollapsedMessageJob {
      val data = BackfillCollapsedMessageJobData.ADAPTER.decode(serializedData!!)
      return BackfillCollapsedMessageJob(lastDateReceived = data.lastDateReceived, parameters = parameters)
    }
  }
}
