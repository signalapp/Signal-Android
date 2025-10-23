/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupUiState.NavTarget
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupUiState.UserMessage.Info
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class CreateGroupViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(CreateGroupViewModel::class)
  }

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
    viewModelScope.launch {
      val stopwatch = Stopwatch(title = "Recipient Refresh")
      internalUiState.update { it.copy(isLookingUpRecipient = true) }

      val selectedRecipients = uiState.value.newSelections.asRecipients(stopwatch)
      stopwatch.split(label = "registered")
      stopwatch.stop(tag = TAG)

      val notSignalUsers = selectedRecipients.filter { !it.isRegistered || !it.hasServiceId }
      if (notSignalUsers.isNotEmpty()) {
        internalUiState.update {
          it.copy(
            isLookingUpRecipient = false,
            userMessage = Info.RecipientsNotSignalUsers(recipients = notSignalUsers)
          )
        }
      } else {
        internalUiState.update {
          it.copy(
            isLookingUpRecipient = false,
            pendingDestination = NavTarget.AddGroupDetails(recipientIds = selectedRecipients.map(Recipient::id))
          )
        }
      }
    }
  }

  private fun List<SelectedContact>.asRecipients(stopwatch: Stopwatch): List<Recipient> {
    val selectedRecipientIds: List<RecipientId> = this.map { it.orCreateRecipientId }

    val recipientsNeedingRegistrationCheck = Recipient
      .resolvedList(selectedRecipientIds)
      .also { stopwatch.split(label = "resolve") }
      .filter { !it.isRegistered || !it.hasServiceId }
      .toSet()

    Log.d(TAG, "Need to do ${recipientsNeedingRegistrationCheck.size} registration checks.")
    recipientsNeedingRegistrationCheck.forEach { recipient ->
      try {
        ContactDiscovery.refresh(
          context = AppDependencies.application,
          recipient = recipient,
          notifyOfNewUsers = false,
          timeoutMs = 10.seconds.inWholeMilliseconds
        )
      } catch (e: IOException) {
        Log.w(TAG, "Failed to refresh registered status for ${recipient.id}", e)
      }
    }

    return Recipient.resolvedList(selectedRecipientIds)
  }

  fun clearUserMessage() {
    internalUiState.update { it.copy(userMessage = null) }
  }

  fun clearPendingDestination() {
    internalUiState.update { it.copy(pendingDestination = null) }
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
  val pendingDestination: NavTarget? = null,
  val userMessage: UserMessage? = null
) {
  sealed interface UserMessage {
    sealed interface Info : UserMessage {
      data class RecipientNotSignalUser(val phone: PhoneNumber) : Info
      data class RecipientsNotSignalUsers(val recipients: List<Recipient>) : Info
      data object NetworkError : Info
    }
  }

  sealed interface NavTarget {
    data class AddGroupDetails(val recipientIds: List<RecipientId>) : NavTarget
  }
}
