package org.thoughtcrime.securesms.stories.settings.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.RecipientId

class BaseStoryRecipientSelectionViewModel(
  private val distributionListId: DistributionListId?
) : ViewModel() {
  private val internalState = MutableStateFlow(BaseStoryRecipientSelectionState(distributionListId))
  private val internalActions = Channel<Action>(Channel.BUFFERED)
  private var actionJob: Job? = null

  val actions: Flow<Action> = internalActions.receiveAsFlow()
  val state: StateFlow<BaseStoryRecipientSelectionState> = internalState

  init {
    if (distributionListId != null) {
      viewModelScope.launch {
        val record = BaseStoryRecipientSelectionRepository.getRecord(distributionListId)
        val startingSelection = if (record.privacyMode == DistributionListPrivacyMode.ALL_EXCEPT) record.rawMembers else record.members
        internalState.update { it.copy(privateStory = record, selection = it.selection + startingSelection, isStartingSelection = true) }
      }
    }
  }

  suspend fun toggleSelectAll(): Set<RecipientId> {
    val allSignalRecipients = BaseStoryRecipientSelectionRepository.getAllSignalContacts()
    internalState.update { it.copy(selection = allSignalRecipients) }
    return allSignalRecipients
  }

  fun addRecipient(recipientId: RecipientId) {
    internalState.update { it.copy(selection = it.selection + recipientId) }
  }

  fun removeRecipient(recipientId: RecipientId) {
    internalState.update { it.copy(selection = it.selection - recipientId) }
  }

  fun onAction() {
    if (actionJob?.isActive == true) {
      return
    }

    actionJob = viewModelScope.launch {
      if (distributionListId != null) {
        BaseStoryRecipientSelectionRepository.updateDistributionListMembership(internalState.value.privateStory!!, internalState.value.selection)
        internalActions.send(Action.ExitFlow)
      } else {
        internalActions.send(Action.GoToNextScreen)
      }
    }
  }

  fun onStartingSelectionAdded() {
    internalState.update { it.copy(isStartingSelection = false) }
  }

  sealed class Action {
    data object GoToNextScreen : Action()
    data object ExitFlow : Action()
  }
}
