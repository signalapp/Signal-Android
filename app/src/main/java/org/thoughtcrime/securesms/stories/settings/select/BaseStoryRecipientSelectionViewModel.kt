package org.thoughtcrime.securesms.stories.settings.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.RecipientId

class BaseStoryRecipientSelectionViewModel(
  private val distributionListId: DistributionListId?,
  private val repository: BaseStoryRecipientSelectionRepository
) : ViewModel() {
  private val internalState = MutableStateFlow(BaseStoryRecipientSelectionState(distributionListId))
  private val subject = PublishSubject.create<Action>()
  private val disposable = CompositeDisposable()

  var actionObservable: Observable<Action> = subject
  val state: StateFlow<BaseStoryRecipientSelectionState> = internalState

  init {
    if (distributionListId != null) {
      disposable += repository.getRecord(distributionListId)
        .subscribe { record ->
          val startingSelection = if (record.privacyMode == DistributionListPrivacyMode.ALL_EXCEPT) record.rawMembers else record.members
          internalState.update { it.copy(privateStory = record, selection = it.selection + startingSelection, isStartingSelection = true) }
        }
    }
  }

  override fun onCleared() {
    disposable.clear()
  }

  fun toggleSelectAll(): Single<Set<RecipientId>> {
    return Single.create { emitter ->
      disposable += repository.getAllSignalContacts().subscribeBy { allSignalRecipients ->
        internalState.update { it.copy(selection = allSignalRecipients) }
        emitter.onSuccess(allSignalRecipients)
      }
    }
  }

  fun addRecipient(recipientId: RecipientId) {
    internalState.update { it.copy(selection = it.selection + recipientId) }
  }

  fun removeRecipient(recipientId: RecipientId) {
    internalState.update { it.copy(selection = it.selection - recipientId) }
  }

  fun onAction() {
    if (distributionListId != null) {
      repository.updateDistributionListMembership(internalState.value.privateStory!!, internalState.value.selection)
      subject.onNext(Action.ExitFlow)
    } else {
      subject.onNext(Action.GoToNextScreen(internalState.value.selection))
    }
  }

  fun onStartingSelectionAdded() {
    internalState.update { it.copy(isStartingSelection = false) }
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
