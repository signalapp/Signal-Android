package org.thoughtcrime.securesms.stories.my

import android.content.Context
import io.reactivex.rxjava3.core.Observable
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient

class MyStoriesRepository(context: Context) {

  private val context = context.applicationContext

  fun getMyStories(): Observable<List<MyStoriesState.DistributionSet>> {
    return Observable.create { emitter ->
      fun refresh() {
        val storiesMap = mutableMapOf<Recipient, List<MessageRecord>>()
        SignalDatabase.mms.allOutgoingStories.use {
          while (it.next != null) {
            val messageRecord = it.current
            val currentList = storiesMap[messageRecord.recipient] ?: emptyList()
            storiesMap[messageRecord.recipient] = (currentList + messageRecord)
          }
        }

        emitter.onNext(storiesMap.map { (r, m) -> createDistributionSet(r, m) })
      }

      val observer = DatabaseObserver.Observer {
        refresh()
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(observer)
      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer)
      }

      refresh()
    }
  }

  private fun createDistributionSet(recipient: Recipient, messageRecords: List<MessageRecord>): MyStoriesState.DistributionSet {
    return MyStoriesState.DistributionSet(
      label = recipient.getDisplayName(context),
      stories = messageRecords.map {
        ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, it)
      }
    )
  }
}
