package org.thoughtcrime.securesms.notifications.v2

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId

/**
 * Represents a "thread" that a notification can belong to.
 */
@Parcelize
data class NotificationThread(
  val threadId: Long,
  val groupStoryId: Long?
) : Parcelable {
  companion object {
    @JvmStatic
    fun forConversation(threadId: Long): NotificationThread {
      return NotificationThread(
        threadId = threadId,
        groupStoryId = null
      )
    }

    @JvmStatic
    fun fromMessageRecord(record: MessageRecord): NotificationThread {
      return NotificationThread(record.threadId, ((record as? MmsMessageRecord)?.parentStoryId as? ParentStoryId.GroupReply)?.serialize())
    }

    @JvmStatic
    fun fromThreadAndReply(threadId: Long, groupReply: ParentStoryId.GroupReply?): NotificationThread {
      return NotificationThread(threadId, groupReply?.serialize())
    }
  }
}
