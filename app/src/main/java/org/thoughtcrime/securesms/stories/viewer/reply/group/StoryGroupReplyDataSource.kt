package org.thoughtcrime.securesms.stories.viewer.reply.group

import android.database.Cursor
import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import java.lang.UnsupportedOperationException

class StoryGroupReplyDataSource(private val parentStoryId: Long) : PagedDataSource<StoryGroupReplyItemData.Key, StoryGroupReplyItemData> {
  override fun size(): Int {
    return SignalDatabase.mms.getNumberOfStoryReplies(parentStoryId)
  }

  override fun load(start: Int, length: Int, cancellationSignal: PagedDataSource.CancellationSignal): MutableList<StoryGroupReplyItemData> {
    val results: MutableList<StoryGroupReplyItemData> = ArrayList(length)
    SignalDatabase.mms.getStoryReplies(parentStoryId).use { cursor ->
      cursor.moveToPosition(start - 1)
      val reader = MmsDatabase.Reader(cursor)
      while (cursor.moveToNext() && cursor.position < start + length) {
        results.add(readRowFromRecord(reader.current))
      }
    }

    return results
  }

  override fun load(key: StoryGroupReplyItemData.Key?): StoryGroupReplyItemData? {
    throw UnsupportedOperationException()
  }

  override fun getKey(data: StoryGroupReplyItemData): StoryGroupReplyItemData.Key {
    return data.key
  }

  private fun readRowFromRecord(record: MessageRecord): StoryGroupReplyItemData {
    return readMessageRecordFromCursor(record)
  }

  private fun readReactionFromCursor(cursor: Cursor): StoryGroupReplyItemData {
    throw NotImplementedError("TODO -- Need to know what the special story reaction record looks like.")
  }

  private fun readMessageRecordFromCursor(messageRecord: MessageRecord): StoryGroupReplyItemData {
    return StoryGroupReplyItemData(
      key = StoryGroupReplyItemData.Key.Text(messageRecord.id),
      sender = if (messageRecord.isOutgoing) Recipient.self() else messageRecord.individualRecipient,
      sentAtMillis = messageRecord.dateSent,
      replyBody = StoryGroupReplyItemData.ReplyBody.Text(
        ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(ApplicationDependencies.getApplication(), messageRecord)
      )
    )
  }
}
