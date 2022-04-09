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
import org.thoughtcrime.securesms.database.model.StoryResult
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.MessageSender

class StoriesLandingRepository(context: Context) {

  private val context = context.applicationContext

  fun resend(story: MessageRecord): Completable {
    return Completable.fromAction {
      MessageSender.resend(context, story)
    }.subscribeOn(Schedulers.io())
  }

  fun getStories(): Observable<List<StoriesLandingItemData>> {
    val storyRecipients: Observable<Map<Recipient, List<StoryResult>>> = Observable.create { emitter ->
      fun refresh() {
        val myStoriesId = SignalDatabase.recipients.getOrInsertFromDistributionListId(DistributionListId.MY_STORY)
        val myStories = Recipient.resolved(myStoriesId)

        emitter.onNext(
          SignalDatabase.mms.orderedStoryRecipientsAndIds.groupBy {
            val recipient = Recipient.resolved(it.recipientId)
            if (recipient.isDistributionList) {
              myStories
            } else {
              recipient
            }
          }
        )
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

    return storyRecipients.switchMap { map ->
      val observables = map.map { (recipient, results) ->
        val messages = results
          .sortedBy { it.messageSentTimestamp }
          .reversed()
          .take(if (recipient.isMyStory) 2 else 1)
          .map {
            SignalDatabase.mms.getMessageRecord(it.messageId)
          }

        createStoriesLandingItemData(recipient, messages)
      }

      if (observables.isEmpty()) {
        Observable.just(emptyList())
      } else {
        Observable.combineLatest(observables) {
          it.toList() as List<StoriesLandingItemData>
        }
      }
    }.subscribeOn(Schedulers.io())
  }

  private fun createStoriesLandingItemData(sender: Recipient, messageRecords: List<MessageRecord>): Observable<StoriesLandingItemData> {
    val itemDataObservable = Observable.create<StoriesLandingItemData> { emitter ->
      fun refresh(sender: Recipient) {
        val primaryIndex = messageRecords.indexOfFirst { !it.isOutgoing && it.viewedReceiptCount == 0 }.takeIf { it > -1 } ?: 0
        val itemData = StoriesLandingItemData(
          storyRecipient = sender,
          storyViewState = StoryViewState.NONE,
          hasReplies = messageRecords.any { SignalDatabase.mms.getNumberOfStoryReplies(it.id) > 0 },
          hasRepliesFromSelf = messageRecords.any { SignalDatabase.mms.hasSelfReplyInStory(it.id) },
          isHidden = sender.shouldHideStory(),
          primaryStory = ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, messageRecords[primaryIndex]),
          secondaryStory = if (sender.isMyStory) messageRecords.drop(1).firstOrNull()?.let {
            ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, it)
          } else null
        )

        emitter.onNext(itemData)
      }

      val newRepliesObserver = DatabaseObserver.Observer {
        Recipient.live(sender.id).refresh()
      }

      val recipientChangedObserver = RecipientForeverObserver {
        refresh(it)
      }

      ApplicationDependencies.getDatabaseObserver().registerConversationObserver(messageRecords.first().threadId, newRepliesObserver)
      val liveRecipient = Recipient.live(sender.id)
      liveRecipient.observeForever(recipientChangedObserver)

      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(newRepliesObserver)
        liveRecipient.removeForeverObserver(recipientChangedObserver)
      }

      refresh(sender)
    }

    val storyViewedStateObservable = StoryViewState.getForRecipientId(if (sender.isMyStory) Recipient.self().id else sender.id)

    return Observable.combineLatest(itemDataObservable, storyViewedStateObservable) { data, state ->
      data.copy(storyViewState = state)
    }
  }

  fun setHideStory(recipientId: RecipientId, hideStory: Boolean): Completable {
    return Completable.fromAction {
      SignalDatabase.recipients.setHideStory(recipientId, hideStory)
    }.subscribeOn(Schedulers.io())
  }
}
