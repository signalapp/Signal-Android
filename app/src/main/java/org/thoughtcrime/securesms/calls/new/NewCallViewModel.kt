/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.new

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.calls.new.NewCallUiState.CallType
import org.thoughtcrime.securesms.calls.new.NewCallUiState.UserMessage
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.recipients.ui.RecipientSelection

class NewCallViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(NewCallViewModel::class)
  }

  private val internalUiState = MutableStateFlow(NewCallUiState())
  val uiState: StateFlow<NewCallUiState> = internalUiState.asStateFlow()

  fun onSearchQueryChanged(query: String) {
    internalUiState.update { it.copy(searchQuery = query) }
  }

  fun startCall(selection: RecipientSelection) {
    viewModelScope.launch {
      when (selection) {
        is RecipientSelection.WithId -> resolveAndStartCall(selection.id)
        is RecipientSelection.WithIdAndPhone -> resolveAndStartCall(selection.id)
        is RecipientSelection.WithPhone -> {
          Log.d(TAG, "[startCall] Missing recipientId: attempting to look up.")
          resolveAndStartCall(selection.phone)
        }
      }
    }
  }

  private suspend fun resolveAndStartCall(id: RecipientId) {
    val recipient = withContext(Dispatchers.IO) {
      Recipient.resolved(id)
    }
    openCall(recipient)
  }

  private suspend fun resolveAndStartCall(phone: PhoneNumber) {
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "[resolveAndStartCall] Cannot look up recipient: account not registered.")
      return
    }
    internalUiState.update { it.copy(isLookingUpRecipient = true) }

    when (val lookupResult = RecipientRepository.lookup(phone)) {
      is RecipientRepository.PhoneLookupResult.Found -> {
        internalUiState.update { it.copy(isLookingUpRecipient = false) }
        openCall(recipient = lookupResult.recipient)
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

  private fun openCall(recipient: Recipient) {
    if (!recipient.isRegistered && recipient.hasServiceId) {
      Log.w(TAG, "[openCall] Unable to open call: recipient has a service ID but is not registered.")
      return
    }

    internalUiState.update {
      it.copy(
        pendingCall = if (recipient.isGroup) {
          CallType.Video(recipient)
        } else {
          CallType.Voice(recipient)
        }
      )
    }
  }

  fun clearPendingCall() {
    internalUiState.update { it.copy(pendingCall = null) }
  }

  fun showUserAlreadyInACall() {
    internalUiState.update { it.copy(userMessage = UserMessage.UserAlreadyInAnotherCall) }
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

data class NewCallUiState(
  val forceSplitPane: Boolean = SignalStore.internal.forceSplitPane,
  val searchQuery: String = "",
  val isLookingUpRecipient: Boolean = false,
  val isRefreshingContacts: Boolean = false,
  val pendingCall: CallType? = null,
  val userMessage: UserMessage? = null
) {
  sealed interface UserMessage {
    data object UserAlreadyInAnotherCall : UserMessage
    data class RecipientLookupFailed(val failure: RecipientRepository.LookupResult.Failure) : UserMessage
  }

  sealed interface CallType {
    data class Voice(val recipient: Recipient) : CallType
    data class Video(val recipient: Recipient) : CallType
  }
}
