package org.thoughtcrime.securesms.stories.landing

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver
import org.thoughtcrime.securesms.recipients.RecipientId

class StoriesLandingRepository(context: Context) {

  private val context = context.applicationContext

  fun getStories(): Observable<List<StoriesLandingItemData>> {
    return Observable.create<Observable<List<StoriesLandingItemData>>> { emitter ->
      val myStoriesId = SignalDatabase.recipients.getOrInsertFromDistributionListId(DistributionListId.MY_STORY)
      val myStories = Recipient.resolved(myStoriesId)

      fun refresh() {
        val storyMap = mutableMapOf<Recipient, List<MessageRecord>>()
        SignalDatabase.mms.allStories.use {
          while (it.next != null) {
            val messageRecord = it.current
            val recipient = if (messageRecord.isOutgoing && !messageRecord.recipient.isGroup) {
              myStories
            } else if (messageRecord.isOutgoing && messageRecord.recipient.isGroup) {
              messageRecord.recipient
            } else {
              SignalDatabase.threads.getRecipientForThreadId(messageRecord.threadId)!!
            }

            storyMap[recipient] = (storyMap[recipient] ?: emptyList()) + messageRecord
          }
        }

        val data: List<Observable<StoriesLandingItemData>> = storyMap.map { (sender, records) -> createStoriesLandingItemData(sender, records) }
        if (data.isEmpty()) {
          emitter.onNext(Observable.just(emptyList()))
        } else {
          emitter.onNext(Observable.combineLatest(data) { it.toList() as List<StoriesLandingItemData> })
        }
      }

      val observer = DatabaseObserver.Observer {
        refresh()
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationListObserver(observer)
      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer)
      }

      refresh()
    }.switchMap { it }.subscribeOn(Schedulers.io())
  }

  private fun createStoriesLandingItemData(sender: Recipient, messageRecords: List<MessageRecord>): Observable<StoriesLandingItemData> {
    return Observable.create { emitter ->
      fun refresh() {
        val itemData = StoriesLandingItemData(
          storyRecipient = sender,
          storyViewState = getStoryViewState(messageRecords),
          hasReplies = messageRecords.any { SignalDatabase.mms.getNumberOfStoryReplies(it.id) > 0 },
          hasRepliesFromSelf = messageRecords.any { SignalDatabase.mms.hasSelfReplyInStory(it.id) },
          isHidden = Recipient.resolved(messageRecords.first().recipient.id).shouldHideStory(),
          primaryStory = ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, messageRecords.first()),
          secondaryStory = messageRecords.drop(1).firstOrNull()?.let {
            ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, it)
          }
        )

        emitter.onNext(itemData)
      }

      val newRepliesObserver = DatabaseObserver.Observer {
        refresh()
      }

      val recipientChangedObserver = RecipientForeverObserver {
        refresh()
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationObserver(messageRecords.first().threadId, newRepliesObserver)
      val liveRecipient = Recipient.live(messageRecords.first().recipient.id)
      liveRecipient.observeForever(recipientChangedObserver)

      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(newRepliesObserver)
        liveRecipient.removeForeverObserver(recipientChangedObserver)
      }

      refresh()
    }
  }

  fun setHideStory(recipientId: RecipientId, hideStory: Boolean): Completable {
    return Completable.fromAction {
      SignalDatabase.recipients.setHideStory(recipientId, hideStory)
    }.subscribeOn(Schedulers.io())
  }

  private fun getStoryViewState(messageRecords: List<MessageRecord>): StoryViewState {
    val incoming = messageRecords.filterNot { it.isOutgoing }
    if (incoming.isEmpty()) {
      return StoryViewState.NONE
    }

    if (incoming.any { it.viewedReceiptCount == 0 }) {
      return StoryViewState.UNVIEWED
    }

    return StoryViewState.VIEWED
  }
}
