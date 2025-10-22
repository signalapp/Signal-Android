/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupUiState.UserMessage.Info
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.util.RemoteConfig

class CreateGroupViewModel : ViewModel() {
  private val internalUiState = MutableStateFlow(CreateGroupUiState())
  val uiState: StateFlow<CreateGroupUiState> = internalUiState.asStateFlow()

  fun onSearchQueryChanged(query: String) {
    internalUiState.update { it.copy(searchQuery = query) }
  }

  suspend fun shouldAllowSelection(id: RecipientId?, phone: PhoneNumber?): Boolean {
    return if (id != null) true else recipientExists(phone!!)
  }

  private suspend fun recipientExists(phone: PhoneNumber): Boolean {
    internalUiState.update { it.copy(isLookingUpRecipient = true) }

    val lookupResult = withContext(Dispatchers.IO) {
      RecipientRepository.lookupNewE164(inputE164 = phone.value)
    }

    return when (lookupResult) {
      is RecipientRepository.LookupResult.Success -> {
        internalUiState.update { it.copy(isLookingUpRecipient = false) }
        true
      }

      is RecipientRepository.LookupResult.NotFound, is RecipientRepository.LookupResult.InvalidEntry -> {
        internalUiState.update {
          it.copy(
            isLookingUpRecipient = false,
            userMessage = Info.RecipientNotSignalUser(phone)
          )
        }
        false
      }

      is RecipientRepository.LookupResult.NetworkError -> {
        internalUiState.update {
          it.copy(
            isLookingUpRecipient = false,
            userMessage = Info.NetworkError
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
    // TODO [jeff] pass selected recipients to AddGroupDetailsActivity
  }

  fun clearUserMessage() {
    internalUiState.update { it.copy(userMessage = null) }
  }
}

data class CreateGroupUiState(
  val forceSplitPaneOnCompactLandscape: Boolean = SignalStore.internal.forceSplitPaneOnCompactLandscape,
  val searchQuery: String = "",
  val selectionLimits: SelectionLimits = RemoteConfig.groupLimits.excludingSelf(),
  val newSelections: List<SelectedContact> = emptyList(),
  val totalMembersCount: Int = 0,
  val isLookingUpRecipient: Boolean = false,
  val pendingRecipientSelections: Set<RecipientId> = emptySet(),
  val userMessage: UserMessage? = null
) {
  sealed interface UserMessage {
    sealed interface Info : UserMessage {
      data class RecipientNotSignalUser(val phone: PhoneNumber) : Info
      data object NetworkError : Info
    }
  }
}
