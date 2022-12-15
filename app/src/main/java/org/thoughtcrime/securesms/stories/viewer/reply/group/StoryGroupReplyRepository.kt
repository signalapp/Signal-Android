package org.thoughtcrime.securesms.stories.viewer.reply.group

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.ThreadUtil
import org.signal.paging.ObservablePagedData
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.signal.paging.PagingController
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.conversation.colors.NameColors
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class StoryGroupReplyRepository {

  fun getThreadId(storyId: Long): Single<Long> {
    return Single.fromCallable {
      SignalDatabase.messages.getThreadIdForMessage(storyId)
    }.subscribeOn(Schedulers.io())
  }

  fun getPagedReplies(parentStoryId: Long): Observable<ObservablePagedData<MessageId, ReplyBody>> {
    return getThreadId(parentStoryId)
      .toObservable()
      .flatMap { threadId ->
        Observable.create<ObservablePagedData<MessageId, ReplyBody>> { emitter ->
          val pagedData: ObservablePagedData<MessageId, ReplyBody> = PagedData.createForObservable(StoryGroupReplyDataSource(parentStoryId), PagingConfig.Builder().build())
          val controller: PagingController<MessageId> = pagedData.controller

          val updateObserver = DatabaseObserver.MessageObserver { controller.onDataItemChanged(it) }
          val insertObserver = DatabaseObserver.MessageObserver { controller.onDataItemInserted(it, PagingController.POSITION_END) }
          val conversationObserver = DatabaseObserver.Observer { controller.onDataInvalidated() }

          ApplicationDependencies.getDatabaseObserver().registerMessageUpdateObserver(updateObserver)
          ApplicationDependencies.getDatabaseObserver().registerMessageInsertObserver(threadId, insertObserver)
          ApplicationDependencies.getDatabaseObserver().registerConversationObserver(threadId, conversationObserver)

          emitter.setCancellable {
            ApplicationDependencies.getDatabaseObserver().unregisterObserver(updateObserver)
            ApplicationDependencies.getDatabaseObserver().unregisterObserver(insertObserver)
            ApplicationDependencies.getDatabaseObserver().unregisterObserver(conversationObserver)
          }

          emitter.onNext(pagedData)
        }.subscribeOn(Schedulers.io())
      }
  }

  fun getNameColorsMap(storyId: Long, sessionMemberCache: MutableMap<GroupId, Set<Recipient>>): Observable<Map<RecipientId, NameColor>> {
    return Single.fromCallable { SignalDatabase.messages.getMessageRecord(storyId).individualRecipient.id }
      .subscribeOn(Schedulers.io())
      .flatMapObservable { recipientId ->
        Observable.create<Map<RecipientId, NameColor>?> { emitter ->
          val nameColorsMapLiveData = NameColors.getNameColorsMapLiveData(MutableLiveData(recipientId), sessionMemberCache)
          val observer = Observer<Map<RecipientId, NameColor>> { emitter.onNext(it) }

          ThreadUtil.postToMain { nameColorsMapLiveData.observeForever(observer) }

          emitter.setCancellable { ThreadUtil.postToMain { nameColorsMapLiveData.removeObserver(observer) } }
        }.subscribeOn(Schedulers.io())
      }
  }
}
