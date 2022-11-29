package org.thoughtcrime.securesms.stories.viewer.reply.group

import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.MmsSmsColumns
import org.thoughtcrime.securesms.database.MmsTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies

class StoryGroupReplyDataSource(private val parentStoryId: Long) : PagedDataSource<MessageId, ReplyBody> {
  override fun size(): Int {
    return SignalDatabase.mms.getNumberOfStoryReplies(parentStoryId)
  }

  override fun load(start: Int, length: Int, cancellationSignal: PagedDataSource.CancellationSignal): MutableList<ReplyBody> {
    val results: MutableList<ReplyBody> = ArrayList(length)
    SignalDatabase.mms.getStoryReplies(parentStoryId).use { cursor ->
      cursor.moveToPosition(start - 1)
      val reader = MmsTable.Reader(cursor)
      while (cursor.moveToNext() && cursor.position < start + length) {
        results.add(readRowFromRecord(reader.current as MmsMessageRecord))
      }
    }

    return results
  }

  override fun load(key: MessageId): ReplyBody {
    return readRowFromRecord(SignalDatabase.mms.getMessageRecord(key.id) as MmsMessageRecord)
  }

  override fun getKey(data: ReplyBody): MessageId {
    return data.key
  }

  private fun readRowFromRecord(record: MmsMessageRecord): ReplyBody {
    return when {
      record.isRemoteDelete -> ReplyBody.RemoteDelete(record)
      MmsSmsColumns.Types.isStoryReaction(record.type) -> ReplyBody.Reaction(record)
      else -> ReplyBody.Text(
        ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(ApplicationDependencies.getApplication(), record)
      )
    }
  }
}
