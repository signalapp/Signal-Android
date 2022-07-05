package org.thoughtcrime.securesms.stories.my

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender

class MyStoriesRepository(context: Context) {

  private val context = context.applicationContext

  fun resend(story: MessageRecord): Completable {
    return Completable.fromAction {
      MessageSender.resend(context, story)
    }.subscribeOn(Schedulers.io())
  }

  fun getMyStories(): Observable<List<MyStoriesState.DistributionSet>> {
    return Observable.create { emitter ->
      fun refresh() {
        val storiesMap = mutableMapOf<Recipient, List<MessageRecord>>()
        SignalDatabase.mms.getAllOutgoingStories(true, -1).use {
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
