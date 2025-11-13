/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.management.ContactsManagementRepository
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.conversation.NewConversationUiState.UserMessage.Info
import org.thoughtcrime.securesms.conversation.NewConversationUiState.UserMessage.Prompt
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.recipients.ui.RecipientSelection

class NewConversationViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(NewConversationViewModel::class)
  }

  private val internalUiState = MutableStateFlow(NewConversationUiState())
  val uiState: StateFlow<NewConversationUiState> = internalUiState.asStateFlow()

  private val contactsManagementRepo = ContactsManagementRepository(AppDependencies.application)

  fun onSearchQueryChanged(query: String) {
    internalUiState.update { it.copy(searchQuery = query) }
  }

  private fun openConversation(recipientId: RecipientId) {
    internalUiState.update { it.copy(pendingDestination = recipientId) }
  }

  fun openConversation(selection: RecipientSelection) {
    when (selection) {
      is RecipientSelection.WithId -> openConversation(recipientId = selection.id)
      is RecipientSelection.WithIdAndPhone -> openConversation(recipientId = selection.id)
      is RecipientSelection.WithPhone -> {
        if (SignalStore.account.isRegistered) {
          Log.d(TAG, "[openConversation] Missing recipientId: attempting to look up.")
          resolveAndOpenConversation(selection.phone)
        } else {
          Log.w(TAG, "[openConversation] Cannot look up recipient: account not registered.")
        }
      }
    }
  }

  private fun resolveAndOpenConversation(phone: PhoneNumber) {
    viewModelScope.launch {
      internalUiState.update { it.copy(isLookingUpRecipient = true) }

      when (val lookupResult = RecipientRepository.lookup(phone)) {
        is RecipientRepository.PhoneLookupResult.Found -> {
          internalUiState.update { it.copy(isLookingUpRecipient = false) }

          val recipient = lookupResult.recipient
          internalUiState.update { it.copy(isLookingUpRecipient = false) }

          if (recipient.isRegistered && recipient.hasServiceId) {
            openConversation(recipient.id)
          } else {
            Log.d(TAG, "[resolveAndOpenConversation] Lookup successful, but recipient is not registered or has no service ID.")
          }
        }

        is RecipientRepository.LookupResult.Failure -> {
          internalUiState.update {
            it.copy(
              isLookingUpRecipient = false,
              userMessage = Info.RecipientLookupFailed(failure = lookupResult)
            )
          }
        }
      }
    }
  }

  fun showRemoveConfirmation(recipient: Recipient) {
    internalUiState.update {
      it.copy(userMessage = Prompt.ConfirmRemoveRecipient(recipient))
    }
  }

  suspend fun removeRecipient(recipient: Recipient) {
    contactsManagementRepo.hideContact(recipient).await()
    refresh()

    internalUiState.update {
      it.copy(
        shouldResetContactsList = true,
        userMessage = Info.RecipientRemoved(recipient)
      )
    }
  }

  fun showBlockConfirmation(recipient: Recipient) {
    internalUiState.update {
      it.copy(userMessage = Prompt.ConfirmBlockRecipient(recipient))
    }
  }

  suspend fun blockRecipient(recipient: Recipient) {
    contactsManagementRepo.blockContact(recipient).await()
    refresh()

    internalUiState.update {
      it.copy(
        shouldResetContactsList = true,
        userMessage = Info.RecipientBlocked(recipient)
      )
    }
  }

  fun showUserAlreadyInACall() {
    internalUiState.update { it.copy(userMessage = Info.UserAlreadyInAnotherCall) }
  }

  fun clearShouldResetContactsList() {
    internalUiState.update { it.copy(shouldResetContactsList = false) }
  }

  fun refresh() {
    if (internalUiState.value.isRefreshingContacts) {
      return
    }

    viewModelScope.launch {
      internalUiState.update { it.copy(isRefreshingContacts = true) }

      withContext(Dispatchers.IO) {
        ContactDiscovery.refreshAll(AppDependencies.application, true)
      }

      internalUiState.update { it.copy(isRefreshingContacts = false) }
    }
  }

  fun clearUserMessage() {
    internalUiState.update { it.copy(userMessage = null) }
  }
}

data class NewConversationUiState(
  val forceSplitPaneOnCompactLandscape: Boolean = SignalStore.internal.forceSplitPane,
  val searchQuery: String = "",
  val isLookingUpRecipient: Boolean = false,
  val isRefreshingContacts: Boolean = false,
  val shouldResetContactsList: Boolean = false,
  val pendingDestination: RecipientId? = null,
  val userMessage: UserMessage? = null
) {
  sealed interface UserMessage {
    sealed interface Info : UserMessage {
      data class RecipientRemoved(val recipient: Recipient) : Info
      data class RecipientBlocked(val recipient: Recipient) : Info
      data class RecipientLookupFailed(val failure: RecipientRepository.LookupResult.Failure) : Info
      data object UserAlreadyInAnotherCall : Info
    }

    sealed interface Prompt : UserMessage {
      data class ConfirmRemoveRecipient(val recipient: Recipient) : Prompt
      data class ConfirmBlockRecipient(val recipient: Recipient) : Prompt
    }
  }
}
