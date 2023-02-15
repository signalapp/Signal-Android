package org.thoughtcrime.securesms.conversation.mutiselect.forward

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.UntrustedRecords
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.livedata.Store

class MultiselectForwardViewModel(
  private val storySendRequirements: Stories.MediaTransform.SendRequirements,
  private val records: List<MultiShareArgs>,
  private val isSelectionOnly: Boolean,
  private val repository: MultiselectForwardRepository,
  private val identityChangesSince: Long = System.currentTimeMillis()
) : ViewModel() {

  private val store = Store(
    MultiselectForwardState(
      storySendRequirements = storySendRequirements
    )
  )

  val state: LiveData<MultiselectForwardState> = store.stateLiveData
  val snapshot: MultiselectForwardState get() = store.state

  private val disposables = CompositeDisposable()

  init {
    if (records.isNotEmpty()) {
      disposables += repository.checkAllSelectedMediaCanBeSentToStories(records).subscribe { sendRequirements ->
        store.update { it.copy(storySendRequirements = sendRequirements) }
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun send(additionalMessage: String, selectedContacts: Set<ContactSearchKey>) {
    if (SignalStore.tooltips().showMultiForwardDialog()) {
      SignalStore.tooltips().markMultiForwardDialogSeen()
      store.update { it.copy(stage = MultiselectForwardState.Stage.FirstConfirmation) }
    } else {
      store.update { it.copy(stage = MultiselectForwardState.Stage.LoadingIdentities) }
      UntrustedRecords.checkForBadIdentityRecords(selectedContacts.filterIsInstance(ContactSearchKey.RecipientSearchKey::class.java).toSet(), identityChangesSince) { identityRecords ->
        if (identityRecords.isEmpty()) {
          performSend(additionalMessage, selectedContacts)
        } else {
          store.update { state ->
            state.copy(
              stage = MultiselectForwardState.Stage.SafetyConfirmation(
                identityRecords,
                selectedContacts.filterIsInstance<ContactSearchKey.RecipientSearchKey>()
              )
            )
          }
        }
      }
    }
  }

  fun confirmFirstSend(additionalMessage: String, selectedContacts: Set<ContactSearchKey>) {
    send(additionalMessage, selectedContacts)
  }

  fun confirmSafetySend(additionalMessage: String, selectedContacts: Set<ContactSearchKey>) {
    send(additionalMessage, selectedContacts)
  }

  fun cancelSend() {
    store.update { it.copy(stage = MultiselectForwardState.Stage.Selection) }
  }

  private fun performSend(additionalMessage: String, selectedContacts: Set<ContactSearchKey>) {
    store.update { it.copy(stage = MultiselectForwardState.Stage.SendPending) }
    if (records.isEmpty() || isSelectionOnly) {
      store.update { it.copy(stage = MultiselectForwardState.Stage.SelectionConfirmed(selectedContacts)) }
    } else {
      repository.send(
        additionalMessage = additionalMessage,
        multiShareArgs = records,
        shareContacts = selectedContacts,
        MultiselectForwardRepository.MultiselectForwardResultHandlers(
          onAllMessageSentSuccessfully = { store.update { it.copy(stage = MultiselectForwardState.Stage.Success) } },
          onAllMessagesFailed = { store.update { it.copy(stage = MultiselectForwardState.Stage.AllFailed) } },
          onSomeMessagesFailed = { store.update { it.copy(stage = MultiselectForwardState.Stage.SomeFailed) } }
        )
      )
    }
  }

  class Factory(
    private val storySendRequirements: Stories.MediaTransform.SendRequirements,
    private val records: List<MultiShareArgs>,
    private val isSelectionOnly: Boolean,
    private val repository: MultiselectForwardRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(MultiselectForwardViewModel(storySendRequirements, records, isSelectionOnly, repository)))
    }
  }
}
