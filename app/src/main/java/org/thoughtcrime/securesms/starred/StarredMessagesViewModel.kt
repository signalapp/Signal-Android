package org.thoughtcrime.securesms.starred

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.RxDatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient

class StarredMessagesViewModel(
  private val threadId: Long?
) : ViewModel() {

  fun getMessages(): Flow<List<ConversationMessage>> {
    val trigger = if (threadId != null) {
      RxDatabaseObserver.conversation(threadId)
    } else {
      RxDatabaseObserver.starredMessages
    }

    return trigger.toObservable().asFlow()
      .map {
        val messages = SignalDatabase.messages.getStarredMessages(threadId)
        messages.map { record ->
          val incomingRecord = if (record is MmsMessageRecord && record.isOutgoing) {
            record.withIncomingType()
          } else {
            record
          }
          val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(record.threadId) ?: Recipient.UNKNOWN
          ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(
            AppDependencies.application,
            incomingRecord,
            threadRecipient
          )
        }
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.IO)
  }

  suspend fun unstarMessage(messageId: Long) {
    withContext(Dispatchers.IO) {
      SignalDatabase.messages.setStarred(messageId, false)
    }
  }

  class Factory(
    private val threadId: Long?
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return StarredMessagesViewModel(threadId) as T
    }
  }
}
