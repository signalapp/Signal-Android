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
        SignalDatabase.messages.getAllOutgoingStories(true, -1).use {
          for (messageRecord in it) {
            val currentList = storiesMap[messageRecord.recipient] ?: emptyList()
            storiesMap[messageRecord.recipient] = (currentList + messageRecord)
          }
        }

        emitter.onNext(storiesMap.toSortedMap(MyStoryBiasComparator()).map { (r, m) -> createDistributionSet(r, m) })
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

  /**
   * Biases "My Story" to the top of the list.
   */
  class MyStoryBiasComparator : Comparator<Recipient> {
    override fun compare(o1: Recipient, o2: Recipient): Int {
      return when {
        o1 == o2 -> 0
        o1.isMyStory -> -1
        o2.isMyStory -> 1
        else -> -1
      }
    }
  }
}
