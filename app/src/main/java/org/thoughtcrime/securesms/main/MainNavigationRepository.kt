package org.thoughtcrime.securesms.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import org.signal.core.util.throttleLatest
import org.thoughtcrime.securesms.database.RxDatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import kotlin.time.Duration.Companion.milliseconds

object MainNavigationRepository {

  private val THROTTLE_INTERVAL = 500.milliseconds

  fun getNumberOfUnreadMessages(): Flow<Long> {
    return onConversationListChanged { SignalDatabase.threads.getUnreadMessageCount() }
  }

  fun getNumberOfUnseenStories(): Flow<Long> {
    return onConversationListChanged {
      SignalDatabase
        .messages
        .getUnreadStoryThreadRecipientIds()
        .map { Recipient.resolved(it) }
        .filterNot { it.shouldHideStory }
        .size
        .toLong()
    }
  }

  fun getHasFailedOutgoingStories(): Flow<Boolean> {
    return onConversationListChanged { SignalDatabase.messages.hasFailedOutgoingStory() }
  }

  fun getNumberOfUnseenCalls(): Flow<Long> {
    return onConversationListChanged { SignalDatabase.calls.getUnreadMissedCallCount() }
  }

  private fun <T> onConversationListChanged(query: () -> T): Flow<T> {
    return RxDatabaseObserver.conversationList
      .asFlow()
      .throttleLatest(THROTTLE_INTERVAL)
      .map { query() }
      .flowOn(Dispatchers.Default)
  }
}
