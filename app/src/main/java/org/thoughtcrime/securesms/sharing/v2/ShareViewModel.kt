package org.thoughtcrime.securesms.sharing.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.sharing.InterstitialContentType
import org.thoughtcrime.securesms.util.rx.RxStore

class ShareViewModel(
  unresolvedShareData: UnresolvedShareData,
  shareRepository: ShareRepository
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(ShareViewModel::class.java)
  }

  private val store = RxStore(ShareState())
  private val disposables = CompositeDisposable()
  private val eventSubject = PublishSubject.create<ShareEvent>()

  val state: Flowable<ShareState> = store.stateFlowable
  val events: Observable<ShareEvent> = eventSubject

  init {
    disposables += shareRepository.resolve(unresolvedShareData).subscribeBy(
      onSuccess = { data ->
        when (data) {
          ResolvedShareData.Failure -> {
            moveToFailedState()
          }
          else -> {
            store.update { it.copy(loadState = ShareState.ShareDataLoadState.Loaded(data)) }
          }
        }
      },
      onError = this::moveToFailedState
    )
  }

  fun onContactSelectionConfirmed(contactSearchKeys: List<ContactSearchKey>) {
    val loadState = store.state.loadState
    if (loadState !is ShareState.ShareDataLoadState.Loaded) {
      return
    }

    val recipientKeys = contactSearchKeys.filterIsInstance(ContactSearchKey.RecipientSearchKey::class.java)
    val hasStory = recipientKeys.any { it.isStory }
    val openConversation = !hasStory && recipientKeys.size == 1
    val resolvedShareData = loadState.resolvedShareData

    if (openConversation) {
      eventSubject.onNext(ShareEvent.OpenConversation(resolvedShareData, recipientKeys.first()))
      return
    }

    val event = when (resolvedShareData.toMultiShareArgs().interstitialContentType) {
      InterstitialContentType.MEDIA -> ShareEvent.OpenMediaInterstitial(resolvedShareData, recipientKeys)
      InterstitialContentType.TEXT -> ShareEvent.OpenTextInterstitial(resolvedShareData, recipientKeys)
      InterstitialContentType.NONE -> ShareEvent.SendWithoutInterstitial(resolvedShareData, recipientKeys)
    }

    eventSubject.onNext(event)
  }

  override fun onCleared() {
    disposables.clear()
    store.dispose()
  }

  private fun moveToFailedState(throwable: Throwable? = null) {
    Log.w(TAG, "Could not load share data.", throwable)
    store.update { it.copy(loadState = ShareState.ShareDataLoadState.Failed) }
  }

  class Factory(
    private val unresolvedShareData: UnresolvedShareData,
    private val shareRepository: ShareRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(ShareViewModel(unresolvedShareData, shareRepository)) as T
    }
  }
}
