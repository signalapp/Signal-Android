package org.thoughtcrime.securesms.jobs

import org.signal.core.util.fullWalCheckpoint
import org.signal.core.util.logging.Log
import org.signal.core.util.update
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import kotlin.time.Duration.Companion.seconds

/**
 * Incrementally cleans up "dead" notification state in the message table by marking read, non-notified messages as
 * notified. These rows would otherwise sit in the message_notification_state_index forever.
 */
class BackfillNotifiedStateJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    const val KEY = "BackfillNotifiedStateJob"

    private val TAG = Log.tag(BackfillNotifiedStateJob::class.java)

    private const val BATCH_SIZE = 1000
    private val TIME_BUDGET = 3.seconds
    private val RETRY_BACKOFF = 30.seconds

    @JvmStatic
    fun enqueue() {
      AppDependencies.jobManager.add(BackfillNotifiedStateJob())
    }
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setInitialDelay(30.seconds.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? = null
  override fun getFactoryKey(): String = KEY
  override fun onFailure() = Unit

  override fun run(): Result {
    val endTime = System.currentTimeMillis() + TIME_BUDGET.inWholeMilliseconds
    var totalUpdated = 0
    var lastBatchUpdateCount: Int

    do {
      lastBatchUpdateCount = updateBatch()
      totalUpdated += lastBatchUpdateCount
    } while (lastBatchUpdateCount > 0 && System.currentTimeMillis() < endTime)

    Log.i(TAG, "Updated $totalUpdated rows this run.")

    if (lastBatchUpdateCount > 0) {
      return Result.retry(RETRY_BACKOFF.inWholeMilliseconds)
    }

    Log.i(TAG, "Backfill complete. Attempting to shrink WAL")
    if (!SignalDatabase.writableDatabase.fullWalCheckpoint()) {
      Log.w(TAG, "Failed to do a full WAL checkpoint after finished backfill.")
    }

    return Result.success()
  }

  /**
   * Marks up to [BATCH_SIZE] read, non-notified messages as notified in a single transaction. Returns the number of
   * rows updated, which is 0 once there is nothing left to clean up.
   */
  private fun updateBatch(): Int {
    return SignalDatabase.writableDatabase
      .update(MessageTable.TABLE_NAME)
      .values(MessageTable.NOTIFIED to 1)
      .where(
        """
        ${MessageTable.ID} IN (
          SELECT ${MessageTable.ID}
          FROM ${MessageTable.TABLE_NAME}
          WHERE ${MessageTable.NOTIFIED} = 0 AND ${MessageTable.READ} = 1 AND ${MessageTable.REACTIONS_UNREAD} = 0 AND ${MessageTable.VOTES_UNREAD} = 0
          LIMIT $BATCH_SIZE
        )
        """
      )
      .run()
  }

  class Factory : Job.Factory<BackfillNotifiedStateJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackfillNotifiedStateJob {
      return BackfillNotifiedStateJob(parameters)
    }
  }
}
