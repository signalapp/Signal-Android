package org.thoughtcrime.securesms.stories.settings.select

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.livedata.Store

class BaseStoryRecipientSelectionViewModel(
  private val distributionListId: DistributionListId?,
  private val repository: BaseStoryRecipientSelectionRepository
) : ViewModel() {
  private val store = Store(emptySet<RecipientId>())
  private val subject = PublishSubject.create<Action>()
  private val disposable = CompositeDisposable()

  var actionObservable: Observable<Action> = subject
  var state: LiveData<Set<RecipientId>> = store.stateLiveData

  init {
    if (distributionListId != null) {
      disposable += repository.getListMembers(distributionListId)
        .subscribe { members ->
          store.update { it + members }
        }
    }
  }

  override fun onCleared() {
    disposable.clear()
  }

  fun toggleSelectAll() {
    disposable += repository.getAllSignalContacts().subscribeBy { allSignalRecipients ->
      store.update { allSignalRecipients }
    }
  }

  fun addRecipient(recipientId: RecipientId) {
    store.update { it + recipientId }
  }

  fun removeRecipient(recipientId: RecipientId) {
    store.update { it - recipientId }
  }

  fun onAction() {
    if (distributionListId != null) {
      repository.updateDistributionListMembership(distributionListId, store.state)
      subject.onNext(Action.ExitFlow)
    } else {
      subject.onNext(Action.GoToNextScreen(store.state))
    }
  }

  sealed class Action {
    data class GoToNextScreen(val recipients: Set<RecipientId>) : Action()
    object ExitFlow : Action()
  }

  class Factory(
    private val distributionListId: DistributionListId?,
    private val repository: BaseStoryRecipientSelectionRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(BaseStoryRecipientSelectionViewModel(distributionListId, repository)) as T
    }
  }
}
