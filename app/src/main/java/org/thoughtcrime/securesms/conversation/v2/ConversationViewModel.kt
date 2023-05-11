/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.paging.ProxyPagingController
import org.thoughtcrime.securesms.conversation.ConversationIntents.Args
import org.thoughtcrime.securesms.conversation.colors.GroupAuthorNameColorHelper
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.conversation.v2.data.ConversationElementKey
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.rx.RxStore
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper

/**
 * ConversationViewModel, which operates solely off of a thread id that never changes.
 */
class ConversationViewModel(
  private val threadId: Long,
  requestedStartingPosition: Int,
  private val repository: ConversationRepository,
  recipientRepository: ConversationRecipientRepository
) : ViewModel() {

  private val disposables = CompositeDisposable()
  private val groupAuthorNameColorHelper = GroupAuthorNameColorHelper()

  private val scrollButtonStateStore = RxStore(ConversationScrollButtonState()).addTo(disposables)
  val scrollButtonState: Flowable<ConversationScrollButtonState> = scrollButtonStateStore.stateFlowable
    .distinctUntilChanged()
    .observeOn(AndroidSchedulers.mainThread())

  private val _recipient: BehaviorSubject<Recipient> = BehaviorSubject.create()
  val recipient: Observable<Recipient> = _recipient

  private val _conversationThreadState: Subject<ConversationThreadState> = BehaviorSubject.create()
  val conversationThreadState: Single<ConversationThreadState> = _conversationThreadState.firstOrError()

  private val _markReadProcessor: PublishProcessor<Long> = PublishProcessor.create()
  val markReadRequests: Flowable<Long> = _markReadProcessor
    .onBackpressureBuffer()
    .distinct()

  val pagingController = ProxyPagingController<ConversationElementKey>()

  val nameColorsMap: Observable<Map<RecipientId, NameColor>> = _recipient.flatMap { repository.getNameColorsMap(it, groupAuthorNameColorHelper) }

  val recipientSnapshot: Recipient?
    get() = _recipient.value

  val wallpaperSnapshot: ChatWallpaper?
    get() = _recipient.value?.wallpaper

  init {
    disposables += recipientRepository
      .conversationRecipient
      .subscribeBy(onNext = {
        _recipient.onNext(it)
      })

    disposables += repository.getConversationThreadState(threadId, requestedStartingPosition)
      .subscribeBy(onSuccess = {
        pagingController.set(it.items.controller)
        _conversationThreadState.onNext(it)
      })

    disposables += conversationThreadState.flatMapObservable { threadState ->
      Observable.create<Unit> { emitter ->
        val controller = threadState.items.controller
        val messageUpdateObserver = DatabaseObserver.MessageObserver {
          controller.onDataItemChanged(ConversationElementKey.forMessage(it.id))
        }
        val messageInsertObserver = DatabaseObserver.MessageObserver {
          controller.onDataItemInserted(ConversationElementKey.forMessage(it.id), 0)
        }
        val conversationObserver = DatabaseObserver.Observer {
          controller.onDataInvalidated()
        }

        ApplicationDependencies.getDatabaseObserver().registerMessageUpdateObserver(messageUpdateObserver)
        ApplicationDependencies.getDatabaseObserver().registerMessageInsertObserver(threadId, messageInsertObserver)
        ApplicationDependencies.getDatabaseObserver().registerConversationObserver(threadId, conversationObserver)

        emitter.setCancellable {
          ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageUpdateObserver)
          ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageInsertObserver)
          ApplicationDependencies.getDatabaseObserver().unregisterObserver(conversationObserver)
        }
      }
    }.subscribeOn(Schedulers.io()).subscribe()

    disposables += scrollButtonStateStore.update(
      repository.getMessageCounts(threadId)
    ) { counts, state ->
      state.copy(
        unreadCount = counts.unread,
        hasMentions = counts.mentions != 0
      )
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun setShowScrollButtons(showScrollButtons: Boolean) {
    scrollButtonStateStore.update {
      it.copy(showScrollButtons = showScrollButtons)
    }
  }

  fun getQuotedMessagePosition(quote: Quote): Single<Int> {
    return repository.getQuotedMessagePosition(threadId, quote)
  }

  fun getNextMentionPosition(): Single<Int> {
    return repository.getNextMentionPosition(threadId)
  }

  fun setLastScrolled(lastScrolledTimestamp: Long) {
    repository.setLastVisibleMessageTimestamp(
      threadId,
      lastScrolledTimestamp
    )
  }

  fun markGiftBadgeRevealed(messageRecord: MessageRecord) {
    if (messageRecord.isOutgoing && messageRecord.hasGiftBadge()) {
      repository.markGiftBadgeRevealed(messageRecord.id)
    }
  }

  fun requestMarkRead(timestamp: Long) {
  }

  class Factory(
    private val args: Args,
    private val repository: ConversationRepository,
    private val recipientRepository: ConversationRecipientRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(
        ConversationViewModel(
          args.threadId,
          args.startingPosition,
          repository,
          recipientRepository
        )
      ) as T
    }
  }
}
