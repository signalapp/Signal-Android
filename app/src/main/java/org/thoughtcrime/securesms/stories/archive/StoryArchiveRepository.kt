package org.thoughtcrime.securesms.stories.archive

import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobs.MultiDeviceDeleteSyncJob

class StoryArchiveRepository {

  fun deleteStories(messageIds: Set<Long>) {
    val records = messageIds.mapNotNull { SignalDatabase.messages.getMessageRecordOrNull(it) }.toSet()
    messageIds.forEach { SignalDatabase.messages.deleteMessage(it) }
    MultiDeviceDeleteSyncJob.enqueueMessageDeletes(records)
  }
}
