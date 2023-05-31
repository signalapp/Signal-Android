/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.core.util.concurrent.subscribeWithSubject
import org.signal.core.util.orNull
import org.signal.paging.ProxyPagingController
import org.thoughtcrime.securesms.components.reminder.Reminder
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.colors.GroupAuthorNameColorHelper
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.v2.data.ConversationElementKey
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.messagerequests.MessageRequestState
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.rx.RxStore
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import java.util.Optional

/**
 * ConversationViewModel, which operates solely off of a thread id that never changes.
 */
class ConversationViewModel(
  private val threadId: Long,
  requestedStartingPosition: Int,
  private val repository: ConversationRepository,
  recipientRepository: ConversationRecipientRepository,
  messageRequestRepository: MessageRequestRepository
) : ViewModel() {

  private val disposables = CompositeDisposable()
  private val groupAuthorNameColorHelper = GroupAuthorNameColorHelper()

  private val scrollButtonStateStore = RxStore(ConversationScrollButtonState()).addTo(disposables)
  val scrollButtonState: Flowable<ConversationScrollButtonState> = scrollButtonStateStore.stateFlowable
    .distinctUntilChanged()
    .observeOn(AndroidSchedulers.mainThread())
  val showScrollButtonsSnapshot: Boolean
    get() = scrollButtonStateStore.state.showScrollButtons

  val recipient: Observable<Recipient> = recipientRepository.conversationRecipient

  private val _conversationThreadState: Subject<ConversationThreadState> = BehaviorSubject.create()
  val conversationThreadState: Single<ConversationThreadState> = _conversationThreadState.firstOrError()

  private val _markReadProcessor: PublishProcessor<Long> = PublishProcessor.create()
  val markReadRequests: Flowable<Long> = _markReadProcessor
    .onBackpressureBuffer()
    .distinct()

  val pagingController = ProxyPagingController<ConversationElementKey>()

  val nameColorsMap: Observable<Map<RecipientId, NameColor>> = recipient.flatMap { repository.getNameColorsMap(it, groupAuthorNameColorHelper) }

  @Volatile
  var recipientSnapshot: Recipient? = null
    private set

  val wallpaperSnapshot: ChatWallpaper?
    get() = recipientSnapshot?.wallpaper

  private val _inputReadyState: Observable<InputReadyState>
  val inputReadyState: Observable<InputReadyState>

  private val hasMessageRequestStateSubject: BehaviorSubject<Boolean> = BehaviorSubject.createDefault(false)
  val hasMessageRequestState: Boolean
    get() = hasMessageRequestStateSubject.value ?: false

  private val refreshReminder: Subject<Unit> = PublishSubject.create()
  val reminder: Observable<Optional<Reminder>>

  private val refreshIdentityRecords: Subject<Unit> = PublishSubject.create()
  val identityRecords: Observable<IdentityRecordsState>

  init {
    disposables += recipient
      .subscribeBy {
        recipientSnapshot = it
      }

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

    recipientRepository
      .conversationRecipient
      .filter { it.isRegistered }
      .take(1)
      .subscribeBy { RetrieveProfileJob.enqueue(it.id) }
      .addTo(disposables)

    disposables += recipientRepository
      .conversationRecipient
      .skip(1) // We can safely skip the first emission since this is used for updating the header on future changes
      .subscribeBy { pagingController.onDataItemChanged(ConversationElementKey.threadHeader) }

    disposables += scrollButtonStateStore.update(
      repository.getMessageCounts(threadId)
    ) { counts, state ->
      state.copy(
        unreadCount = counts.unread,
        hasMentions = counts.mentions != 0
      )
    }

    _inputReadyState = Observable.combineLatest(
      recipientRepository.conversationRecipient,
      recipientRepository.groupRecord
    ) { recipient, groupRecord ->
      InputReadyState(
        conversationRecipient = recipient,
        messageRequestState = messageRequestRepository.getMessageRequestState(recipient, threadId),
        groupRecord = groupRecord.orNull(),
        isClientExpired = SignalStore.misc().isClientDeprecated,
        isUnauthorized = TextSecurePreferences.isUnauthorizedReceived(ApplicationDependencies.getApplication())
      )
    }.doOnNext {
      hasMessageRequestStateSubject.onNext(it.messageRequestState != MessageRequestState.NONE)
    }
    inputReadyState = _inputReadyState.observeOn(AndroidSchedulers.mainThread())

    recipientRepository.conversationRecipient.map { Unit }.subscribeWithSubject(refreshReminder, disposables)

    reminder = Observable.combineLatest(refreshReminder.startWithItem(Unit), recipientRepository.groupRecord) { _, groupRecord -> groupRecord }
      .subscribeOn(Schedulers.io())
      .flatMapMaybe { groupRecord -> repository.getReminder(groupRecord.orNull()) }
      .observeOn(AndroidSchedulers.mainThread())

    identityRecords = Observable.combineLatest(
      refreshIdentityRecords.startWithItem(Unit).observeOn(Schedulers.io()),
      recipient,
      recipientRepository.groupRecord
    ) { _, r, g -> Pair(r, g) }
      .flatMapSingle { (r, g) -> repository.getIdentityRecords(r, g.orNull()) }
      .distinctUntilChanged()
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

  fun muteConversation(until: Long) {
    recipient.firstOrError()
      .subscribeBy {
        repository.setConversationMuted(it.id, until)
      }
      .addTo(disposables)
  }

  fun getContactPhotoIcon(context: Context, glideRequests: GlideRequests): Single<ShortcutInfoCompat> {
    return recipient.firstOrError().flatMap {
      repository.getRecipientContactPhotoBitmap(context, glideRequests, it)
    }
  }

  fun requestMarkRead(timestamp: Long) {
  }

  fun sendMessage(
    metricId: String?,
    body: String,
    slideDeck: SlideDeck?,
    scheduledDate: Long,
    messageToEdit: MessageId?,
    quote: QuoteModel?,
    mentions: List<Mention>,
    bodyRanges: BodyRangeList?,
    contacts: List<Contact>
  ): Completable {
    return repository.sendMessage(
      threadId = threadId,
      threadRecipient = recipientSnapshot,
      metricId = metricId,
      body = body,
      slideDeck = slideDeck,
      scheduledDate = scheduledDate,
      messageToEdit = messageToEdit,
      quote = quote,
      mentions = mentions,
      bodyRanges = bodyRanges,
      contacts = contacts
    ).observeOn(AndroidSchedulers.mainThread())
  }

  fun resetVerifiedStatusToDefault(unverifiedIdentities: List<IdentityRecord>) {
    disposables += repository.resetVerifiedStatusToDefault(unverifiedIdentities)
      .subscribe {
        refreshIdentityRecords.onNext(Unit)
      }
  }

  fun updateIdentityRecords() {
    refreshIdentityRecords.onNext(Unit)
  }

  fun getTemporaryViewOnceUri(mmsMessageRecord: MmsMessageRecord): Maybe<Uri> {
    return repository.getTemporaryViewOnceUri(mmsMessageRecord).observeOn(AndroidSchedulers.mainThread())
  }

  fun copyToClipboard(context: Context, messageParts: Set<MultiselectPart>): Maybe<CharSequence> {
    return repository.copyToClipboard(context, messageParts)
  }

  fun getRequestReviewState(): Observable<RequestReviewState> {
    return _inputReadyState
      .flatMapSingle { (recipient, messageRequestState, group) -> repository.getRequestReviewState(recipient, group, messageRequestState) }
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun getSlideDeckAndBodyForReply(context: Context, conversationMessage: ConversationMessage): Pair<SlideDeck, CharSequence> {
    return repository.getSlideDeckAndBodyForReply(context, conversationMessage)
  }

  fun resolveMessageToEdit(conversationMessage: ConversationMessage): Single<ConversationMessage> {
    return repository.resolveMessageToEdit(conversationMessage)
  }
}
