package org.thoughtcrime.securesms.conversation.mutiselect.forward

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.sharing.ShareContact
import org.thoughtcrime.securesms.sharing.ShareSelectionMappingModel
import org.thoughtcrime.securesms.util.livedata.Store
import org.whispersystems.libsignal.util.guava.Optional

class MultiselectForwardViewModel(
  private val records: List<MultiShareArgs>,
  private val repository: MultiselectForwardRepository
) : ViewModel() {

  private val store = Store(MultiselectForwardState())

  val state: LiveData<MultiselectForwardState> = store.stateLiveData

  val shareContactMappingModels: LiveData<List<ShareSelectionMappingModel>> = Transformations.map(state) { s -> s.selectedContacts.mapIndexed { i, c -> ShareSelectionMappingModel(c, i == 0) } }

  fun addSelectedContact(recipientId: Optional<RecipientId>, number: String?) {
    store.update { it.copy(selectedContacts = it.selectedContacts + ShareContact(recipientId, number)) }
  }

  fun removeSelectedContact(recipientId: Optional<RecipientId>, number: String?) {
    store.update { it.copy(selectedContacts = it.selectedContacts - ShareContact(recipientId, number)) }
  }

  fun send(additionalMessage: String) {
    repository.send(
      additionalMessage = additionalMessage,
      multiShareArgs = records,
      shareContacts = store.state.selectedContacts,
      MultiselectForwardRepository.MultiselectForwardResultHandlers(
        onAllMessageSentSuccessfully = { store.update { it.copy(stage = MultiselectForwardState.Stage.SUCCESS) } },
        onAllMessagesFailed = { store.update { it.copy(stage = MultiselectForwardState.Stage.ALL_FAILED) } },
        onSomeMessagesFailed = { store.update { it.copy(stage = MultiselectForwardState.Stage.SOME_FAILED) } }
      )
    )
  }

  class Factory(
    private val records: List<MultiShareArgs>,
    private val repository: MultiselectForwardRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(MultiselectForwardViewModel(records, repository)))
    }
  }
}
