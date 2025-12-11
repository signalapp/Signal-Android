/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.addtogroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.addtogroup.AddToGroupsUiState.UserMessage
import org.thoughtcrime.securesms.groups.v2.GroupAddMembersResult
import org.thoughtcrime.securesms.groups.v2.GroupManagementRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class AddToGroupsViewModel(
  private val recipientId: RecipientId,
  private val existingGroupMemberships: Set<RecipientId>,
  selectionLimits: SelectionLimits?
) : ViewModel() {

  private val internalUiState = MutableStateFlow(
    AddToGroupsUiState(
      existingGroupMemberships = existingGroupMemberships,
      selectionLimits = selectionLimits
    )
  )
  val uiState: StateFlow<AddToGroupsUiState> = internalUiState.asStateFlow()

  private val repository: GroupManagementRepository = GroupManagementRepository()

  fun onSearchQueryChanged(query: String) {
    internalUiState.update { it.copy(searchQuery = query) }
  }

  fun selectGroups(newSelections: List<SelectedContact>) {
    val selectedGroupIds = newSelections.map { it.getOrCreateRecipientId() }.toSet()

    if (internalUiState.value.isMultiSelectEnabled) {
      updateSelection(selectedGroupIds)
    } else {
      confirmAddToGroup(groupRecipientId = selectedGroupIds.single())
    }
  }

  private fun confirmAddToGroup(groupRecipientId: RecipientId) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        if (!existingGroupMemberships.contains(groupRecipientId)) {
          internalUiState.update {
            it.copy(
              userMessage = UserMessage.ConfirmAddToGroup(
                recipientToAdd = Recipient.resolved(recipientId),
                targetGroup = Recipient.resolved(id = groupRecipientId)
              )
            )
          }
        }
      }
    }
  }

  private fun updateSelection(selectedGroupIds: Set<RecipientId>) {
    internalUiState.update {
      it.copy(
        searchQuery = "",
        newSelections = selectedGroupIds
      )
    }
  }

  fun addToGroups(groupRecipients: List<Recipient>) {
    if (groupRecipients.size > 1) {
      throw UnsupportedOperationException("Multi-select is not yet supported.")
    }

    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        val recipient = Recipient.resolved(recipientId)
        val groupRecipient = groupRecipients.single()

        if (groupRecipient.groupId.get().isV1 && !recipient.hasE164) {
          internalUiState.update {
            it.copy(userMessage = UserMessage.CantAddRecipientToLegacyGroup)
          }
          return@withContext
        }

        repository.addMembers(groupRecipient, listOf(recipient.id)) { result ->
          when (result) {
            is GroupAddMembersResult.Success -> {
              internalUiState.update {
                it.copy(userMessage = UserMessage.AddedRecipientToGroup(recipient, groupRecipient))
              }
            }

            is GroupAddMembersResult.Failure -> {
              internalUiState.update {
                it.copy(userMessage = UserMessage.GroupUpdateError(result.reason))
              }
            }
          }
        }
      }
    }
  }

  fun addToSelectedGroups() {
    val selectedGroups = internalUiState.value.newSelections
    throw UnsupportedOperationException("Not yet built to handle multi-select.")
  }

  fun clearUserMessage() {
    internalUiState.update { it.copy(userMessage = null) }
  }
}

data class AddToGroupsUiState(
  val forceSplitPane: Boolean = SignalStore.internal.forceSplitPane,
  val searchQuery: String = "",
  val existingGroupMemberships: Set<RecipientId> = emptySet(),
  val selectionLimits: SelectionLimits? = null,
  val newSelections: Set<RecipientId> = emptySet(),
  val userMessage: UserMessage? = null
) {
  val isMultiSelectEnabled: Boolean
    get() = selectionLimits != null

  sealed interface UserMessage {
    data class ConfirmAddToGroup(val recipientToAdd: Recipient, val targetGroup: Recipient) : UserMessage
    data class AddedRecipientToGroup(val recipient: Recipient, val targetGroup: Recipient) : UserMessage
    data object CantAddRecipientToLegacyGroup : UserMessage
    data class GroupUpdateError(val failureReason: GroupChangeFailureReason) : UserMessage
  }
}
