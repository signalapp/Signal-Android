package org.thoughtcrime.securesms.conversation

import org.thoughtcrime.securesms.database.model.ThreadRecord

/**
 * Describes and aggregates the thread count for a particular thread, for use in the "Last Seen" header.
 */
sealed class ThreadCountAggregator {

  abstract val count: Int

  abstract fun updateWith(record: ThreadRecord): ThreadCountAggregator

  /**
   * The Init object, used as an initial state and returned whenever the given record is an outgoing message.
   * The conversation fragment already properly cleans up the header when an outgoing message is emitted, so
   * there's no need to worry about seeing a "zero."
   */
  object Init : ThreadCountAggregator() {
    override val count: Int = 0

    override fun updateWith(record: ThreadRecord): ThreadCountAggregator {
      return when {
        record.isOutgoing -> Outgoing
        else -> Count(record.threadId, record.unreadCount, record.date)
      }
    }
  }

  /**
   * The Outgoing object, returned whenever the given record is an outgoing message.
   * The conversation fragment already properly cleans up the header when an outgoing message is emitted, so
   * there's no need to worry about seeing a "zero."
   */
  object Outgoing : ThreadCountAggregator() {
    override val count: Int = 0

    override fun updateWith(record: ThreadRecord): ThreadCountAggregator {
      return when {
        record.isOutgoing -> Outgoing
        else -> Count(record.threadId, record.unreadCount, record.date)
      }
    }
  }

  /**
   * Represents an actual count. We keep record of the id and date to use in comparisons with future
   * ThreadRecord objects.
   */
  class Count(val threadId: Long, val unreadCount: Int, val threadDate: Long) : ThreadCountAggregator() {
    override val count: Int = unreadCount

    /**
     * "Ratchets" the count to the new state.
     * * Outgoing records will always result in Empty
     * * Mismatched threadIds will always create a new Count, initialized with the new thread
     * * Matching dates will be ignored, as this means that there was no actual change.
     * * Otherwise, we'll proceed with the new date and aggregate the count.
     */
    override fun updateWith(record: ThreadRecord): ThreadCountAggregator {
      return when {
        record.isOutgoing -> Outgoing
        threadId != record.threadId -> Init.updateWith(record)
        threadDate >= record.date -> this
        record.unreadCount > 1 -> Init.updateWith(record)
        else -> Count(threadId, unreadCount + 1, record.date)
      }
    }
  }
}
