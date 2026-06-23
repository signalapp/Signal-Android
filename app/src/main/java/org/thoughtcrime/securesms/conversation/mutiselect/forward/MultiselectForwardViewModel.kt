package org.thoughtcrime.securesms.conversation.mutiselect.forward

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.UntrustedRecords
import org.thoughtcrime.securesms.util.livedata.Store

class MultiselectForwardViewModel(
  private val args: MultiselectForwardFragmentArgs,
  private val identityChangesSince: Long = System.currentTimeMillis()
) : ViewModel() {

  private val store = Store(
    MultiselectForwardState(
      storySendRequirements = args.storySendRequirements
    )
  )

  val state: LiveData<MultiselectForwardState> = store.stateLiveData
  val snapshot: MultiselectForwardState get() = store.state

  private val internalBottomBarState = MutableStateFlow(
    MultiselectForwardBottomBarState(
      sendButtonColors = args.sendButtonColors,
      isSendButtonVisible = !args.selectSingleRecipient
    )
  )

  val bottomBarState: StateFlow<MultiselectForwardBottomBarState> = internalBottomBarState

  private val disposables = CompositeDisposable()

  init {
    if (args.multiShareArgs.isNotEmpty()) {
      disposables += MultiselectForwardRepository.checkAllSelectedMediaCanBeSentToStories(args.multiShareArgs).subscribe { sendRequirements ->
        store.update { it.copy(storySendRequirements = sendRequirements) }
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun setSendEnabled(isSendEnabled: Boolean) {
    internalBottomBarState.update { it.copy(isSendButtonEnabled = isSendEnabled) }
  }

  fun setShareSelection(selection: List<MultiselectForwardBottomBarState.SelectedContact>) {
    internalBottomBarState.update { it.copy(selection = selection) }
  }

  fun setAddMessageVisible(isAddMessageVisible: Boolean) {
    internalBottomBarState.update { it.copy(isAddMessageVisible = isAddMessageVisible) }
  }

  fun setMessage(message: String) {
    internalBottomBarState.update { it.copy(message = message) }
  }

  fun send(selectedContacts: Set<ContactSearchKey>) {
    if (SignalStore.tooltips.showMultiForwardDialog()) {
      SignalStore.tooltips.markMultiForwardDialogSeen()
      store.update { it.copy(stage = MultiselectForwardState.Stage.FirstConfirmation) }
    } else {
      store.update { it.copy(stage = MultiselectForwardState.Stage.LoadingIdentities) }
      UntrustedRecords.checkForBadIdentityRecords(selectedContacts.filterIsInstance(ContactSearchKey.RecipientSearchKey::class.java).toSet(), identityChangesSince) { identityRecords ->
        if (identityRecords.isEmpty()) {
          performSend(selectedContacts)
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

  fun confirmFirstSend(selectedContacts: Set<ContactSearchKey>) {
    send(selectedContacts)
  }

  fun confirmSafetySend(selectedContacts: Set<ContactSearchKey>) {
    send(selectedContacts)
  }

  fun cancelSend() {
    store.update { it.copy(stage = MultiselectForwardState.Stage.Selection) }
  }

  private fun performSend(selectedContacts: Set<ContactSearchKey>) {
    store.update { it.copy(stage = MultiselectForwardState.Stage.SendPending) }
    if (args.multiShareArgs.isEmpty() || args.forceSelectionOnly) {
      store.update { it.copy(stage = MultiselectForwardState.Stage.SelectionConfirmed(selectedContacts)) }
    } else {
      MultiselectForwardRepository.send(
        additionalMessage = bottomBarState.value.message,
        multiShareArgs = args.multiShareArgs,
        shareContacts = selectedContacts,
        MultiselectForwardRepository.MultiselectForwardResultHandlers(
          onAllMessageSentSuccessfully = { store.update { it.copy(stage = MultiselectForwardState.Stage.Success) } },
          onAllMessagesFailed = { store.update { it.copy(stage = MultiselectForwardState.Stage.AllFailed) } },
          onSomeMessagesFailed = { store.update { it.copy(stage = MultiselectForwardState.Stage.SomeFailed) } }
        )
      )
    }
  }
}
