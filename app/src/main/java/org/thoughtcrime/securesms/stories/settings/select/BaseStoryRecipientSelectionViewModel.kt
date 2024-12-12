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
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.livedata.Store

class BaseStoryRecipientSelectionViewModel(
  private val distributionListId: DistributionListId?,
  private val repository: BaseStoryRecipientSelectionRepository
) : ViewModel() {
  private val store = Store(BaseStoryRecipientSelectionState(distributionListId))
  private val subject = PublishSubject.create<Action>()
  private val disposable = CompositeDisposable()

  var actionObservable: Observable<Action> = subject
  var state: LiveData<BaseStoryRecipientSelectionState> = store.stateLiveData

  init {
    if (distributionListId != null) {
      disposable += repository.getRecord(distributionListId)
        .subscribe { record ->
          val startingSelection = if (record.privacyMode == DistributionListPrivacyMode.ALL_EXCEPT) record.rawMembers else record.members
          store.update { it.copy(privateStory = record, selection = it.selection + startingSelection, isStartingSelection = true) }
        }
    }
  }

  override fun onCleared() {
    disposable.clear()
  }

  fun toggleSelectAll() {
    disposable += repository.getAllSignalContacts().subscribeBy { allSignalRecipients ->
      store.update { it.copy(selection = allSignalRecipients) }
    }
  }

  fun addRecipient(recipientId: RecipientId) {
    store.update { it.copy(selection = it.selection + recipientId) }
  }

  fun removeRecipient(recipientId: RecipientId) {
    store.update { it.copy(selection = it.selection - recipientId) }
  }

  fun onAction() {
    if (distributionListId != null) {
      repository.updateDistributionListMembership(store.state.privateStory!!, store.state.selection)
      subject.onNext(Action.ExitFlow)
    } else {
      subject.onNext(Action.GoToNextScreen(store.state.selection))
    }
  }

  fun onStartingSelectionAdded() {
    store.update { it.copy(isStartingSelection = false) }
  }

  sealed class Action {
    data class GoToNextScreen(val recipients: Set<RecipientId>) : Action()
    object ExitFlow : Action()
  }

  class Factory(
    private val distributionListId: DistributionListId?,
    private val repository: BaseStoryRecipientSelectionRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(BaseStoryRecipientSelectionViewModel(distributionListId, repository)) as T
    }
  }
}
