/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupUiState.NavTarget
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupUiState.UserMessage
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.recipients.ui.RecipientSelection
import org.thoughtcrime.securesms.util.RemoteConfig

class CreateGroupViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(CreateGroupViewModel::class)
  }

  private val internalUiState = MutableStateFlow(CreateGroupUiState())
  val uiState: StateFlow<CreateGroupUiState> = internalUiState.asStateFlow()

  fun onSearchQueryChanged(query: String) {
    internalUiState.update { it.copy(searchQuery = query) }
  }

  suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = when (selection) {
    is RecipientSelection.HasId -> true
    is RecipientSelection.HasPhone -> recipientExists(selection.phone)
  }

  private suspend fun recipientExists(phone: PhoneNumber): Boolean {
    internalUiState.update { it.copy(isLookingUpRecipient = true) }

    return when (val lookupResult = RecipientRepository.lookup(phone)) {
      is RecipientRepository.PhoneLookupResult.Found -> {
        internalUiState.update { it.copy(isLookingUpRecipient = false) }
        true
      }

      is RecipientRepository.LookupResult.Failure -> {
        internalUiState.update {
          it.copy(
            isLookingUpRecipient = false,
            userMessage = UserMessage.RecipientLookupFailed(failure = lookupResult)
          )
        }
        false
      }
    }
  }

  fun onSelectionChanged(newSelections: List<SelectedContact>, totalMembersCount: Int) {
    internalUiState.update {
      it.copy(
        searchQuery = "",
        newSelections = newSelections,
        totalMembersCount = totalMembersCount
      )
    }
  }

  fun selectRecipient(id: RecipientId) {
    internalUiState.update {
      it.copy(pendingRecipientSelections = it.pendingRecipientSelections + id)
    }
  }

  fun clearPendingRecipientSelections() {
    internalUiState.update {
      it.copy(pendingRecipientSelections = emptySet())
    }
  }

  fun continueToGroupDetails() {
    viewModelScope.launch {
      internalUiState.update { it.copy(isLookingUpRecipient = true) }

      val selectedRecipientIds = uiState.value.newSelections.map { it.orCreateRecipientId }
      when (val lookupResult = RecipientRepository.lookup(recipientIds = selectedRecipientIds)) {
        is RecipientRepository.IdLookupResult.FoundAll -> {
          internalUiState.update {
            it.copy(
              isLookingUpRecipient = false,
              pendingDestination = NavTarget.AddGroupDetails(recipientIds = selectedRecipientIds)
            )
          }
        }

        is RecipientRepository.LookupResult.Failure -> {
          internalUiState.update {
            it.copy(
              isLookingUpRecipient = false,
              userMessage = UserMessage.RecipientLookupFailed(failure = lookupResult)
            )
          }
        }
      }
    }
  }

  fun clearUserMessage() {
    internalUiState.update { it.copy(userMessage = null) }
  }

  fun clearPendingDestination() {
    internalUiState.update { it.copy(pendingDestination = null) }
  }
}

data class CreateGroupUiState(
  val forceSplitPane: Boolean = SignalStore.internal.forceSplitPane,
  val searchQuery: String = "",
  val selectionLimits: SelectionLimits = RemoteConfig.groupLimits.excludingSelf(),
  val newSelections: List<SelectedContact> = emptyList(),
  val totalMembersCount: Int = 0,
  val isLookingUpRecipient: Boolean = false,
  val pendingRecipientSelections: Set<RecipientId> = emptySet(),
  val pendingDestination: NavTarget? = null,
  val userMessage: UserMessage? = null
) {
  sealed interface UserMessage {
    data class RecipientLookupFailed(val failure: RecipientRepository.LookupResult.Failure) : UserMessage
  }

  sealed interface NavTarget {
    data class AddGroupDetails(val recipientIds: List<RecipientId>) : NavTarget
  }
}
