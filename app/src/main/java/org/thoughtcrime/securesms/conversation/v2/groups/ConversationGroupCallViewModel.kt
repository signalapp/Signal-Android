package org.thoughtcrime.securesms.conversation.v2.groups

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.v2.ConversationRecipientRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.GroupCallPeekEvent
import org.thoughtcrime.securesms.util.rx.RxStore

/**
 * ViewModel which manages state associated with group calls.
 */
class ConversationGroupCallViewModel(
  recipientRepository: ConversationRecipientRepository
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(ConversationGroupCallViewModel::class.java)
  }

  private val disposables = CompositeDisposable()
  private val store = RxStore(ConversationGroupCallState()).addTo(disposables)
  private val forcePeek = PublishProcessor.create<Unit>()

  val state: Flowable<ConversationGroupCallState> = store.stateFlowable.onBackpressureLatest().observeOn(AndroidSchedulers.mainThread())

  val hasOngoingGroupCallSnapshot: Boolean
    get() = store.state.ongoingCall

  init {
    recipientRepository
      .conversationRecipient
      .subscribeBy { recipient ->
        store.update { s: ConversationGroupCallState ->
          val activeV2Group = recipient.isPushV2Group && recipient.isActiveGroup
          s.copy(
            recipientId = recipient.id,
            activeV2Group = activeV2Group,
            ongoingCall = if (activeV2Group && s.recipientId == recipient.id) s.ongoingCall else false,
            hasCapacity = if (activeV2Group && s.recipientId == recipient.id) s.hasCapacity else false
          )
        }
      }
      .addTo(disposables)

    val filteredState = store.stateFlowable
      .filter { it.recipientId != null }
      .distinctUntilChanged { s -> s.activeV2Group }

    Flowable.combineLatest(forcePeek, filteredState) { _, s -> s }
      .subscribeOn(Schedulers.io())
      .onBackpressureLatest()
      .subscribeBy { s: ConversationGroupCallState ->
        if (s.recipientId != null && s.activeV2Group) {
          Log.i(TAG, "Peek call for ${s.recipientId}")
          AppDependencies.signalCallManager.peekGroupCall(s.recipientId)
        }
      }
      .addTo(disposables)
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun onGroupCallPeekEvent(event: GroupCallPeekEvent) {
    store.update { s: ConversationGroupCallState ->
      if (s.recipientId != null && event.groupRecipientId == s.recipientId) {
        s.copy(ongoingCall = event.isOngoing, hasCapacity = event.callHasCapacity())
      } else {
        s
      }
    }
  }

  fun peekGroupCall() {
    forcePeek.onNext(Unit)
  }
}
