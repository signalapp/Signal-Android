package org.thoughtcrime.securesms.stories.archive

import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.keyvalue.SignalStore

class StoryArchivePagedDataSource(
  private val sortNewest: Boolean
) : PagedDataSource<Long, ArchivedStoryItem> {

  private val includeActive = SignalStore.story.isArchiveEnabled

  override fun size(): Int {
    return SignalDatabase.messages.getArchiveScreenStoriesCount(includeActive)
  }

  override fun load(start: Int, length: Int, totalSize: Int, cancellationSignal: PagedDataSource.CancellationSignal): List<ArchivedStoryItem> {
    return SignalDatabase.messages.getArchiveScreenStoriesPage(includeActive, sortNewest, start, length).use { reader ->
      reader.mapNotNull { record ->
        if (cancellationSignal.isCanceled) return@use emptyList()
        val mmsRecord = record as? MmsMessageRecord
        ArchivedStoryItem(
          messageId = record.id,
          dateSent = record.dateSent,
          thumbnailUri = mmsRecord?.slideDeck?.thumbnailSlide?.uri,
          blurHash = mmsRecord?.slideDeck?.thumbnailSlide?.placeholderBlur,
          storyType = mmsRecord?.storyType ?: StoryType.NONE,
          body = record.body
        )
      }
    }
  }

  override fun load(key: Long): ArchivedStoryItem? {
    val record = SignalDatabase.messages.getMessageRecordOrNull(key) ?: return null
    val mmsRecord = record as? MmsMessageRecord
    return ArchivedStoryItem(
      messageId = record.id,
      dateSent = record.dateSent,
      thumbnailUri = mmsRecord?.slideDeck?.thumbnailSlide?.uri,
      blurHash = mmsRecord?.slideDeck?.thumbnailSlide?.placeholderBlur,
      storyType = mmsRecord?.storyType ?: StoryType.NONE,
      body = record.body
    )
  }

  override fun getKey(data: ArchivedStoryItem): Long {
    return data.messageId
  }
}
