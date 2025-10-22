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

class NewConversationViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(NewConversationViewModel::class)
  }

  private val internalUiState = MutableStateFlow(NewConversationUiState())
  val uiState: StateFlow<NewConversationUiState> = internalUiState.asStateFlow()

  private val contactsManagementRepo = ContactsManagementRepository(AppDependencies.application)

  fun onMessage(id: RecipientId): Unit = openConversation(recipientId = id)

  fun onRecipientSelected(id: RecipientId?, phone: PhoneNumber?) {
    when {
      id != null -> openConversation(recipientId = id)

      SignalStore.account.isRegistered -> {
        Log.d(TAG, "[onRecipientSelected] Missing recipientId: attempting to look up.")
        resolveAndOpenConversation(phone)
      }

      else -> Log.w(TAG, "[onRecipientSelected] Cannot look up recipient: account not registered.")
    }
  }

  private fun openConversation(recipientId: RecipientId) {
    internalUiState.update { it.copy(pendingDestination = recipientId) }
  }

  private fun resolveAndOpenConversation(phone: PhoneNumber?) {
    viewModelScope.launch {
      internalUiState.update { it.copy(isRefreshingRecipient = true) }

      val lookupResult = withContext(Dispatchers.IO) {
        if (phone != null) {
          RecipientRepository.lookupNewE164(inputE164 = phone.value)
        } else {
          RecipientRepository.LookupResult.InvalidEntry
        }
      }

      when (lookupResult) {
        is RecipientRepository.LookupResult.Success -> {
          val recipient = Recipient.resolved(lookupResult.recipientId)
          internalUiState.update { it.copy(isRefreshingRecipient = false) }

          if (recipient.isRegistered && recipient.hasServiceId) {
            openConversation(recipient.id)
          } else {
            Log.d(TAG, "[resolveAndOpenConversation] Lookup successful, but recipient is not registered or has no service ID.")
          }
        }

        is RecipientRepository.LookupResult.NotFound, is RecipientRepository.LookupResult.InvalidEntry -> {
          internalUiState.update {
            it.copy(
              isRefreshingRecipient = false,
              userMessage = Info.RecipientNotSignalUser(phone)
            )
          }
        }

        is RecipientRepository.LookupResult.NetworkError -> {
          internalUiState.update {
            it.copy(
              isRefreshingRecipient = false,
              userMessage = Info.NetworkError
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

  fun onUserAlreadyInACall() {
    internalUiState.update { it.copy(userMessage = Info.UserAlreadyInAnotherCall) }
  }

  fun onContactsListReset() {
    internalUiState.update { it.copy(shouldResetContactsList = false) }
  }

  fun refresh() {
    viewModelScope.launch {
      internalUiState.update { it.copy(isRefreshingContacts = true) }

      withContext(Dispatchers.IO) {
        ContactDiscovery.refreshAll(AppDependencies.application, true)
      }

      internalUiState.update { it.copy(isRefreshingContacts = false) }
    }
  }

  fun onUserMessageDismissed() {
    internalUiState.update { it.copy(userMessage = null) }
  }
}

data class NewConversationUiState(
  val forceSplitPaneOnCompactLandscape: Boolean = SignalStore.internal.forceSplitPaneOnCompactLandscape,
  val isRefreshingRecipient: Boolean = false,
  val isRefreshingContacts: Boolean = false,
  val shouldResetContactsList: Boolean = false,
  val pendingDestination: RecipientId? = null,
  val userMessage: UserMessage? = null
) {
  sealed interface UserMessage {
    sealed interface Info : UserMessage {
      data class RecipientRemoved(val recipient: Recipient) : Info
      data class RecipientBlocked(val recipient: Recipient) : Info
      data class RecipientNotSignalUser(val phone: PhoneNumber?) : Info
      data object UserAlreadyInAnotherCall : Info
      data object NetworkError : Info
    }

    sealed interface Prompt : UserMessage {
      data class ConfirmRemoveRecipient(val recipient: Recipient) : Prompt
      data class ConfirmBlockRecipient(val recipient: Recipient) : Prompt
    }
  }
}
