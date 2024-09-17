/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.lifecycle.ViewModel
import com.bumptech.glide.RequestManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.util.orNull
import org.signal.paging.ProxyPagingController
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.banners.BubbleOptOutBanner
import org.thoughtcrime.securesms.banner.banners.GroupsV1MigrationSuggestionsBanner
import org.thoughtcrime.securesms.banner.banners.OutdatedBuildBanner
import org.thoughtcrime.securesms.banner.banners.PendingGroupJoinRequestsBanner
import org.thoughtcrime.securesms.banner.banners.ServiceOutageBanner
import org.thoughtcrime.securesms.banner.banners.UnauthorizedBanner
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ScheduledMessagesRepository
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.v2.data.ConversationElementKey
import org.thoughtcrime.securesms.conversation.v2.items.ChatColorsDrawable
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.keyboard.KeyboardUtil
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.messagerequests.MessageRequestState
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.util.BubbleUtil
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.rx.RxStore
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * ConversationViewModel, which operates solely off of a thread id that never changes.
 */
class ConversationViewModel(
  val threadId: Long,
  requestedStartingPosition: Int,
  initialChatColors: ChatColors,
  private val repository: ConversationRepository,
  recipientRepository: ConversationRecipientRepository,
  messageRequestRepository: MessageRequestRepository,
  private val scheduledMessagesRepository: ScheduledMessagesRepository
) : ViewModel() {

  private val disposables = CompositeDisposable()

  private val scrollButtonStateStore = RxStore(ConversationScrollButtonState()).addTo(disposables)
  val scrollButtonState: Flowable<ConversationScrollButtonState> = scrollButtonStateStore.stateFlowable
    .distinctUntilChanged()
    .observeOn(AndroidSchedulers.mainThread())
  val showScrollButtonsSnapshot: Boolean
    get() = scrollButtonStateStore.state.showScrollButtons
  val unreadCount: Int
    get() = scrollButtonStateStore.state.unreadCount

  val recipient: Observable<Recipient> = recipientRepository.conversationRecipient
  val titleViewParticipants: Observable<List<Recipient>> = recipient.filter { it.isGroup }.switchMap { groupRecipient ->
    val firstTenIds = groupRecipient.participantIds
      .take(10)
      .sortedBy { it == Recipient.self().id }

    Observable.combineLatest(
      firstTenIds.map { Recipient.observable(it) }
    ) { objects ->
      objects.toList() as List<Recipient>
    }
  }

  private val _conversationThreadState: Subject<ConversationThreadState> = BehaviorSubject.create()
  val conversationThreadState: Single<ConversationThreadState> = _conversationThreadState.firstOrError()

  val pagingController = ProxyPagingController<ConversationElementKey>()

  val groupMemberServiceIds: Observable<List<ServiceId>> = recipientRepository
    .groupRecord
    .filter { it.isPresent && it.get().isV2Group }
    .map { it.get().requireV2GroupProperties().getMemberServiceIds() }
    .distinctUntilChanged()
    .observeOn(AndroidSchedulers.mainThread())

  private val chatBounds: BehaviorSubject<Rect> = BehaviorSubject.create()
  private val chatColors: RxStore<ChatColorsDrawable.ChatColorsData> = RxStore(ChatColorsDrawable.ChatColorsData(initialChatColors, null))
  val chatColorsSnapshot: ChatColorsDrawable.ChatColorsData get() = chatColors.state

  @Volatile
  var recipientSnapshot: Recipient? = null
    private set

  @Volatile
  var titleViewParticipantsSnapshot: List<Recipient> = emptyList()
    private set

  val isPushAvailable: Boolean
    get() = recipientSnapshot?.isRegistered == true && Recipient.self().isRegistered

  val wallpaperSnapshot: ChatWallpaper?
    get() = recipientSnapshot?.wallpaper

  private val _inputReadyState: Observable<InputReadyState>
  val inputReadyState: Observable<InputReadyState>

  private val hasMessageRequestStateSubject: BehaviorSubject<MessageRequestState> = BehaviorSubject.createDefault(MessageRequestState())
  val hasMessageRequestState: Boolean
    get() = hasMessageRequestStateSubject.value?.state != MessageRequestState.State.NONE
  val messageRequestState: MessageRequestState
    get() = hasMessageRequestStateSubject.value ?: MessageRequestState()

  private val groupRecordFlow: Flow<GroupRecord>

  private val refreshIdentityRecords: Subject<Unit> = PublishSubject.create()
  private val identityRecordsStore: RxStore<IdentityRecordsState> = RxStore(IdentityRecordsState())
  val identityRecordsObservable: Observable<IdentityRecordsState> = identityRecordsStore.stateFlowable.toObservable()
  val identityRecordsState: IdentityRecordsState
    get() = identityRecordsStore.state

  private val _searchQuery = BehaviorSubject.createDefault("")
  val searchQuery: Observable<String> = _searchQuery

  val storyRingState = recipient
    .switchMap { StoryViewState.getForRecipientId(it.id) }
    .subscribeOn(Schedulers.io())
    .distinctUntilChanged()
    .observeOn(AndroidSchedulers.mainThread())

  private val startExpiration = BehaviorSubject.create<MessageTable.ExpirationInfo>()

  private val _jumpToDateValidator: JumpToDateValidator by lazy { JumpToDateValidator(threadId) }
  val jumpToDateValidator: JumpToDateValidator
    get() = _jumpToDateValidator

  init {
    disposables += recipient
      .subscribeBy {
        recipientSnapshot = it
      }

    disposables += titleViewParticipants
      .subscribeBy {
        titleViewParticipantsSnapshot = it
      }

    val chatColorsDataObservable: Observable<ChatColorsDrawable.ChatColorsData> = Observable.combineLatest(
      recipient.map { it.chatColors }.distinctUntilChanged(),
      chatBounds.distinctUntilChanged()
    ) { chatColors, bounds ->
      val chatMask = chatColors.chatBubbleMask

      chatMask.bounds = bounds

      ChatColorsDrawable.ChatColorsData(chatColors, chatMask)
    }

    disposables += chatColors.update(chatColorsDataObservable.toFlowable(BackpressureStrategy.LATEST)) { c, _ -> c }

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

        AppDependencies.databaseObserver.registerMessageUpdateObserver(messageUpdateObserver)
        AppDependencies.databaseObserver.registerMessageInsertObserver(threadId, messageInsertObserver)
        AppDependencies.databaseObserver.registerConversationObserver(threadId, conversationObserver)

        emitter.setCancellable {
          AppDependencies.databaseObserver.unregisterObserver(messageUpdateObserver)
          AppDependencies.databaseObserver.unregisterObserver(messageInsertObserver)
          AppDependencies.databaseObserver.unregisterObserver(conversationObserver)
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
        isClientExpired = SignalStore.misc.isClientDeprecated,
        isUnauthorized = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application),
        threadContainsSms = !recipient.isRegistered && !recipient.isPushGroup && !recipient.isSelf && messageRequestRepository.threadContainsSms(threadId)
      )
    }.doOnNext {
      hasMessageRequestStateSubject.onNext(it.messageRequestState)
    }
    inputReadyState = _inputReadyState.observeOn(AndroidSchedulers.mainThread())

    groupRecordFlow = recipientRepository.groupRecord
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .asFlow()
      .mapNotNull { it.orNull() }

    Observable.combineLatest(
      refreshIdentityRecords.startWithItem(Unit).observeOn(Schedulers.io()),
      recipient,
      recipientRepository.groupRecord
    ) { _, r, g -> Pair(r, g) }
      .subscribeOn(Schedulers.io())
      .flatMapSingle { (r, g) -> repository.getIdentityRecords(r, g.orNull()) }
      .subscribeBy { newState ->
        identityRecordsStore.update { newState }
      }
      .addTo(disposables)

    startExpiration
      .buffer(startExpiration.throttleLast(1, TimeUnit.SECONDS))
      .observeOn(Schedulers.io())
      .subscribe(object : Observer<List<MessageTable.ExpirationInfo>> {
        override fun onNext(t: List<MessageTable.ExpirationInfo>) = repository.startExpirationTimeout(t.distinctBy { it.id })
        override fun onSubscribe(d: Disposable) = Unit
        override fun onError(e: Throwable) = Unit
        override fun onComplete() = Unit
      })
  }

  fun getBannerFlows(
    context: Context,
    groupJoinClickListener: () -> Unit,
    onSuggestionAddMembers: () -> Unit,
    onSuggestionNoThanks: () -> Unit,
    bubbleClickListener: (Boolean) -> Unit
  ): Flow<List<Banner<*>>> {
    val pendingGroupJoinFlow: Flow<PendingGroupJoinRequestsBanner> = groupRecordFlow
      .map {
        PendingGroupJoinRequestsBanner(
          suggestionsSize = it.actionableRequestingMembersCount,
          onViewClicked = groupJoinClickListener
        )
      }

    val groupV1SuggestionsFlow = groupRecordFlow
      .map {
        GroupsV1MigrationSuggestionsBanner(
          suggestionsSize = it.gv1MigrationSuggestions.size,
          onAddMembers = onSuggestionAddMembers,
          onNoThanks = onSuggestionNoThanks
        )
      }

    return combine(
      listOf(
        flowOf(OutdatedBuildBanner()),
        flowOf(UnauthorizedBanner(context)),
        flowOf(ServiceOutageBanner(context)),
        pendingGroupJoinFlow,
        groupV1SuggestionsFlow,
        flowOf(BubbleOptOutBanner(inBubble = repository.isInBubble, actionListener = bubbleClickListener))
      ),
      transform = { it.toList() }
    )
      .flowOn(Dispatchers.IO)
  }

  fun onChatBoundsChanged(bounds: Rect) {
    chatBounds.onNext(bounds)
  }

  fun setSearchQuery(query: String?) {
    _searchQuery.onNext(query ?: "")
  }

  fun onDismissReview() {
    val recipientId = recipientSnapshot?.id ?: return
    repository.dismissRequestReviewState(recipientId)
  }

  override fun onCleared() {
    disposables.clear()
    startExpiration.onComplete()
  }

  fun setShowScrollButtonsForScrollPosition(showScrollButtons: Boolean, willScrollToBottomOnNewMessage: Boolean) {
    scrollButtonStateStore.update {
      it.copy(
        showScrollButtonsForScrollPosition = showScrollButtons,
        willScrollToBottomOnNewMessage = willScrollToBottomOnNewMessage
      )
    }
  }

  fun setHideScrollButtonsForReactionOverlay(hide: Boolean) {
    scrollButtonStateStore.update {
      it.copy(hideScrollButtonsForReactionOverlay = hide)
    }
  }

  fun getQuotedMessagePosition(quote: Quote): Single<Int> {
    return repository.getQuotedMessagePosition(threadId, quote)
  }

  fun moveToDate(receivedTimestamp: Long): Single<Int> {
    return repository.getMessageResultPosition(threadId, receivedTimestamp)
  }

  fun getNextMentionPosition(): Single<Int> {
    return repository.getNextMentionPosition(threadId)
  }

  fun moveToMessage(dateReceived: Long, author: RecipientId): Single<Int> {
    return repository.getMessagePosition(threadId, dateReceived, author)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun moveToMessage(messageRecord: MessageRecord): Single<Int> {
    return repository.getMessagePosition(threadId, messageRecord.dateReceived, messageRecord.fromRecipient.id)
      .observeOn(AndroidSchedulers.mainThread())
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

  fun getContactPhotoIcon(context: Context, requestManager: RequestManager): Single<ShortcutInfoCompat> {
    return recipient.firstOrError().flatMap {
      repository.getRecipientContactPhotoBitmap(context, requestManager, it)
    }
  }

  fun startExpirationTimeout(messageRecord: MessageRecord) {
    startExpiration.onNext(
      MessageTable.ExpirationInfo(
        id = messageRecord.id,
        expiresIn = messageRecord.expiresIn,
        expireStarted = System.currentTimeMillis(),
        isMms = messageRecord.isMms
      )
    )
  }

  fun updateReaction(messageRecord: MessageRecord, emoji: String): Completable {
    val oldRecord = messageRecord.oldReactionRecord()

    return if (oldRecord != null && oldRecord.emoji == emoji) {
      repository.sendReactionRemoval(messageRecord, oldRecord)
    } else {
      repository.sendNewReaction(messageRecord, emoji)
    }
  }

  /**
   * @return Maybe which only emits if the "React with any" sheet should be displayed.
   */
  fun updateCustomReaction(messageRecord: MessageRecord, hasAddedCustomEmoji: Boolean): Maybe<Unit> {
    val oldRecord = messageRecord.oldReactionRecord()

    return if (oldRecord != null && hasAddedCustomEmoji) {
      repository.sendReactionRemoval(messageRecord, oldRecord).toMaybe()
    } else {
      Maybe.just(Unit)
    }
  }

  fun getKeyboardImageDetails(uri: Uri): Maybe<KeyboardUtil.ImageDetails> {
    return repository.getKeyboardImageDetails(uri)
  }

  private fun MessageRecord.oldReactionRecord(): ReactionRecord? {
    return reactions.firstOrNull { it.author == Recipient.self().id }
  }

  fun sendMessage(
    metricId: String?,
    threadRecipient: Recipient,
    body: String,
    slideDeck: SlideDeck?,
    scheduledDate: Long,
    messageToEdit: MessageId?,
    quote: QuoteModel?,
    mentions: List<Mention>,
    bodyRanges: BodyRangeList?,
    contacts: List<Contact>,
    linkPreviews: List<LinkPreview>,
    preUploadResults: List<MessageSender.PreUploadResult>,
    isViewOnce: Boolean
  ): Completable {
    return repository.sendMessage(
      threadId = threadId,
      threadRecipient = threadRecipient,
      metricId = metricId,
      body = body,
      slideDeck = slideDeck,
      scheduledDate = scheduledDate,
      messageToEdit = messageToEdit,
      quote = quote,
      mentions = mentions,
      bodyRanges = bodyRanges,
      contacts = contacts,
      linkPreviews = linkPreviews,
      preUploadResults = preUploadResults,
      isViewOnce = isViewOnce
    ).observeOn(AndroidSchedulers.mainThread())
  }

  fun resetVerifiedStatusToDefault(unverifiedIdentities: List<IdentityRecord>) {
    disposables += repository.resetVerifiedStatusToDefault(unverifiedIdentities)
      .subscribe {
        refreshIdentityRecords.onNext(Unit)
      }
  }

  fun updateIdentityRecordsInBackground() {
    refreshIdentityRecords.onNext(Unit)
  }

  fun updateIdentityRecords(): Completable {
    val state: IdentityRecordsState = identityRecordsStore.state
    if (state.recipient == null) {
      return Completable.error(IllegalStateException("No recipient in records store"))
    }

    return repository.getIdentityRecords(state.recipient, state.group)
      .doOnSuccess { newState ->
        identityRecordsStore.update { newState }
      }
      .flatMapCompletable { Completable.complete() }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun getTemporaryViewOnceUri(mmsMessageRecord: MmsMessageRecord): Maybe<Uri> {
    return repository.getTemporaryViewOnceUri(mmsMessageRecord).observeOn(AndroidSchedulers.mainThread())
  }

  fun canShowAsBubble(context: Context): Observable<Boolean> {
    return recipient
      .map { Build.VERSION.SDK_INT >= ConversationUtil.CONVERSATION_SUPPORT_VERSION && BubbleUtil.canBubble(context, it, threadId) }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun copyToClipboard(context: Context, messageParts: Set<MultiselectPart>): Maybe<CharSequence> {
    return repository.copyToClipboard(context, messageParts)
  }

  fun resendMessage(conversationMessage: ConversationMessage): Completable {
    return repository.resendMessage(conversationMessage.messageRecord)
  }

  fun getRequestReviewState(): Observable<RequestReviewState> {
    return _inputReadyState
      .flatMapSingle { state -> repository.getRequestReviewState(state.conversationRecipient, state.groupRecord, state.messageRequestState) }
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun getSlideDeckAndBodyForReply(context: Context, conversationMessage: ConversationMessage): Pair<SlideDeck, CharSequence> {
    return repository.getSlideDeckAndBodyForReply(context, conversationMessage)
  }

  fun resolveMessageToEdit(conversationMessage: ConversationMessage): Single<ConversationMessage> {
    return repository.resolveMessageToEdit(conversationMessage)
  }

  fun deleteSlideData(slides: List<Slide>) {
    repository.deleteSlideData(slides)
  }

  fun updateStickerLastUsedTime(stickerRecord: StickerRecord, timestamp: Duration) {
    repository.updateStickerLastUsedTime(stickerRecord, timestamp)
  }

  fun getScheduledMessagesCount(): Observable<Int> {
    return scheduledMessagesRepository
      .getScheduledMessageCount(threadId)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun markLastSeen() {
    repository.markLastSeen(threadId)
  }

  fun onChatSearchOpened() {
    // Trigger the lazy load, so we can race initialization of the validator
    _jumpToDateValidator
  }

  fun getEarliestMessageSentDate(): Single<Long> {
    return repository
      .getEarliestMessageSentDate(threadId)
      .observeOn(AndroidSchedulers.mainThread())
  }
}
