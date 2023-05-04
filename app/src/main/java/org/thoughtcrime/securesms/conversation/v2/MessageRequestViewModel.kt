package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.concurrent.subscribeWithSubject
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.messagerequests.GroupInfo
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.messagerequests.MessageRequestState
import org.thoughtcrime.securesms.messagerequests.MessageRequestViewModel.MessageData
import org.thoughtcrime.securesms.messagerequests.MessageRequestViewModel.RecipientInfo
import org.thoughtcrime.securesms.messagerequests.MessageRequestViewModel.RequestReviewDisplayState
import org.thoughtcrime.securesms.messagerequests.MessageRequestViewModel.Status
import org.thoughtcrime.securesms.profiles.spoofing.ReviewUtil

/**
 * MessageRequestViewModel for ConversationFragment V2
 */
class MessageRequestViewModel(
  private val threadId: Long,
  private val recipientRepository: ConversationRecipientRepository,
  private val messageRequestRepository: MessageRequestRepository
) : ViewModel() {

  private val disposables = CompositeDisposable()

  private val statusSubject = PublishSubject.create<Status>()
  val status: Observable<Status> = statusSubject

  private val failureSubject = PublishSubject.create<GroupChangeFailureReason>()
  val failure: Observable<GroupChangeFailureReason> = failureSubject

  private val groupInfo: Observable<GroupInfo> = recipientRepository
    .conversationRecipient
    .flatMap { recipient ->
      Single.create { emitter ->
        messageRequestRepository.getGroupInfo(recipient.id, emitter::onSuccess)
      }.toObservable()
    }

  private val groups: Observable<List<String>> = recipientRepository
    .conversationRecipient
    .flatMap { recipient ->
      Single.create<List<String>> { emitter ->
        messageRequestRepository.getGroups(recipient.id, emitter::onSuccess)
      }.toObservable()
    }

  private val messageDataSubject: BehaviorSubject<MessageData> = recipientRepository.conversationRecipient.map {
    val state = messageRequestRepository.getMessageRequestState(it, threadId)
    MessageData(it, state)
  }.subscribeWithSubject(BehaviorSubject.create(), disposables)

  private val requestReviewDisplayStateSubject: BehaviorSubject<RequestReviewDisplayState> = messageDataSubject.map { holder ->
    if (holder.messageState == MessageRequestState.INDIVIDUAL) {
      if (ReviewUtil.isRecipientReviewSuggested(holder.recipient.id)) {
        RequestReviewDisplayState.SHOWN
      } else {
        RequestReviewDisplayState.HIDDEN
      }
    } else {
      RequestReviewDisplayState.NONE
    }
  }.subscribeWithSubject(BehaviorSubject.create(), disposables)

  val recipientInfo: Observable<RecipientInfo> = Observable.combineLatest(
    recipientRepository.conversationRecipient,
    groupInfo,
    groups,
    messageDataSubject.map { it.messageState },
    ::RecipientInfo
  )

  override fun onCleared() {
    disposables.clear()
  }

  fun shouldShowMessageRequest(): Boolean {
    val messageData = messageDataSubject.value
    return messageData != null && messageData.messageState != MessageRequestState.NONE
  }

  fun onAccept() {
    statusSubject.onNext(Status.ACCEPTING)
    disposables += recipientRepository
      .conversationRecipient
      .firstOrError()
      .map { it.id }
      .subscribeBy { recipientId ->
        messageRequestRepository.acceptMessageRequest(
          recipientId,
          threadId,
          { statusSubject.onNext(Status.ACCEPTED) },
          this::onGroupChangeError
        )
      }
  }
  fun onDelete() {
    statusSubject.onNext(Status.DELETING)
    disposables += recipientRepository
      .conversationRecipient
      .firstOrError()
      .map { it.id }
      .subscribeBy { recipientId ->
        messageRequestRepository.deleteMessageRequest(
          recipientId,
          threadId,
          { statusSubject.onNext(Status.DELETED) },
          this::onGroupChangeError
        )
      }
  }
  fun onBlock() {
    statusSubject.onNext(Status.BLOCKING)
    disposables += recipientRepository
      .conversationRecipient
      .firstOrError()
      .map { it.id }
      .subscribeBy { recipientId ->
        messageRequestRepository.blockMessageRequest(
          recipientId,
          { statusSubject.onNext(Status.BLOCKED) },
          this::onGroupChangeError
        )
      }
  }
  fun onUnblock() {
    disposables += recipientRepository
      .conversationRecipient
      .firstOrError()
      .map { it.id }
      .subscribeBy { recipientId ->
        messageRequestRepository.unblockAndAccept(
          recipientId
        ) { statusSubject.onNext(Status.ACCEPTED) }
      }
  }
  fun onBlockAndReportSpam() {
    disposables += recipientRepository
      .conversationRecipient
      .firstOrError()
      .map { it.id }
      .subscribeBy { recipientId ->
        messageRequestRepository.blockAndReportSpamMessageRequest(
          recipientId,
          threadId,
          { statusSubject.onNext(Status.BLOCKED_AND_REPORTED) },
          this::onGroupChangeError
        )
      }
  }

  private fun onGroupChangeError(error: GroupChangeFailureReason) {
    statusSubject.onNext(Status.IDLE)
    failureSubject.onNext(error)
  }

  class Factory(
    private val threadId: Long,
    private val recipientRepository: ConversationRecipientRepository,
    private val messageRequestRepository: MessageRequestRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(
        MessageRequestViewModel(
          threadId,
          recipientRepository,
          messageRequestRepository
        )
      ) as T
    }
  }
}
