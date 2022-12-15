package org.thoughtcrime.securesms.database.model

import kotlin.math.abs

/**
 * Abstract representation of a message id for a story.
 *
 * At the database layer, the sign of the id is dependant on whether a reply is meant for
 * a group thread or a direct reply. This class facilitates that, while still allowing for
 * normal behaviour elsewhere.
 */
sealed class ParentStoryId(protected val id: Long) {
  abstract fun serialize(): Long

  fun asMessageId(): MessageId = MessageId(abs(id))
  fun isGroupReply() = serialize() > 0
  fun isDirectReply() = !isGroupReply()

  /**
   * A parent story who's child should be displayed in a group reply thread.
   */
  class GroupReply(id: Long) : ParentStoryId(id) {
    override fun serialize(): Long = abs(id)
  }

  /**
   * A parent story who's child should be displayed in a 1:1 conversation.
   */
  class DirectReply(id: Long) : ParentStoryId(id) {
    override fun serialize(): Long = -abs(id)
  }

  companion object {
    /**
     * Takes a long stored in the database and converts it to an appropriate subclass of ParentStoryId.
     * If the passed value is 0L, null is returned.
     */
    @JvmStatic
    fun deserialize(id: Long): ParentStoryId? {
      return when {
        id > 0L -> GroupReply(id)
        id < 0L -> DirectReply(id)
        else -> null
      }
    }
  }
}
