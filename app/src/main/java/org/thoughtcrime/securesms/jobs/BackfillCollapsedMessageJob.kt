package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
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
import org.thoughtcrime.securesms.keyvalue.SignalStore
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
    if (SignalStore.misc.completedCollapsedEventsMigration) {
      Log.i(TAG, "Already completed migration")
      return Result.success()
    }

    val db = SignalDatabase.rawDatabase

    var messageCount = 0
    var lastProcessedDateReceived = lastDateReceived

    // Tracks the last/previous message to compare against the current message when determining collapsed state
    val lastMessageByThread = mutableMapOf<Long, LastMessage?>()

    db
      .select(MessageTable.ID, MessageTable.THREAD_ID, MessageTable.DATE_RECEIVED, MessageTable.TYPE, MessageTable.READ, MessageTable.MESSAGE_EXTRAS)
      .from(MessageTable.TABLE_NAME)
      .where("${MessageTable.DATE_RECEIVED} > ?", lastDateReceived)
      .orderBy("${MessageTable.DATE_RECEIVED}, ${MessageTable.ID}")
      .limit(BATCH_SIZE)
      .run()
      .use { cursor ->
        while (cursor.moveToNext()) {
          val id = cursor.requireLong(MessageTable.ID)
          val threadId = cursor.requireLong(MessageTable.THREAD_ID)
          val type = cursor.requireLong(MessageTable.TYPE)
          val dateReceived = cursor.requireLong(MessageTable.DATE_RECEIVED)
          val read = cursor.requireBoolean(MessageTable.READ)
          val messageExtras = cursor.requireBlob(MessageTable.MESSAGE_EXTRAS)?.let { MessageExtras.ADAPTER.decode(it) }

          val collapsibleType = CollapsibleEvents.getCollapsibleType(type, messageExtras)

          if (collapsibleType == null) {
            lastMessageByThread[threadId] = null
          } else {
            val previous = lastMessageByThread[threadId]

            val (collapsedState, headId, size) = if ((previous?.collapsibleType == collapsibleType) && DateUtils.isSameDay(previous.dateReceived, dateReceived) && previous.collapsedSetSize < CollapsibleEvents.MAX_SIZE) {
              val state = if (read) CollapsedState.COLLAPSED.id else CollapsedState.PENDING_COLLAPSED.id
              Triple(state, previous.headId, previous.collapsedSetSize)
            } else {
              Triple(CollapsedState.HEAD_COLLAPSED.id, id, 0)
            }

            db.update(MessageTable.TABLE_NAME)
              .values(
                MessageTable.COLLAPSED_STATE to collapsedState,
                MessageTable.COLLAPSED_HEAD_ID to headId
              )
              .where("${MessageTable.ID} = ?", id)
              .run()
            lastMessageByThread[threadId] = LastMessage(collapsibleType, headId, dateReceived, size + 1)
          }

          messageCount++
          lastProcessedDateReceived = dateReceived
        }
      }

    if (messageCount == 0 || messageCount != BATCH_SIZE) {
      Log.i(TAG, "Finished processing all messages, backfill is completed")
      SignalStore.misc.completedCollapsedEventsMigration = true
    } else {
      Log.i(TAG, "Processed $messageCount messages, up to time $lastProcessedDateReceived. Re-enqueuing job")
      AppDependencies.jobManager.add(BackfillCollapsedMessageJob(lastDateReceived = lastProcessedDateReceived))
    }

    return Result.success()
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to backfill collapsed messages. Time of last processed message: $lastDateReceived")
  }

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
