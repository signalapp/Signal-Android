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
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.NewConversationUiState.UserMessage
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientRepository

class NewConversationViewModel : ViewModel() {
  companion object {
    private val TAG = Log.tag(NewConversationViewModel::class)
  }

  private val _uiState = MutableStateFlow(NewConversationUiState())
  val uiState: StateFlow<NewConversationUiState> = _uiState.asStateFlow()

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
    _uiState.update { it.copy(pendingDestination = recipientId) }
  }

  private fun resolveAndOpenConversation(phone: PhoneNumber?) {
    viewModelScope.launch {
      _uiState.update { it.copy(isRefreshingRecipient = true) }

      val lookupResult = withContext(Dispatchers.IO) {
        if (phone != null) {
          RecipientRepository.lookupNewE164(inputE164 = phone.value)
        } else {
          RecipientRepository.LookupResult.InvalidEntry
        }
      }

      when (lookupResult) {
        is RecipientRepository.LookupResult.Success -> {
          val recipient = resolved(lookupResult.recipientId)
          _uiState.update { it.copy(isRefreshingRecipient = false) }

          if (recipient.isRegistered && recipient.hasServiceId) {
            openConversation(recipient.id)
          } else {
            Log.d(TAG, "[resolveAndOpenConversation] Lookup successful, but recipient is not registered or has no service ID.")
          }
        }

        is RecipientRepository.LookupResult.NotFound, is RecipientRepository.LookupResult.InvalidEntry -> {
          _uiState.update {
            it.copy(
              isRefreshingRecipient = false,
              userMessage = UserMessage.RecipientNotSignalUser(phone)
            )
          }
        }

        is RecipientRepository.LookupResult.NetworkError -> {
          _uiState.update {
            it.copy(
              isRefreshingRecipient = false,
              userMessage = UserMessage.NetworkError
            )
          }
        }
      }
    }
  }

  fun onUserMessageDismissed() {
    _uiState.update { it.copy(userMessage = null) }
  }
}

data class NewConversationUiState(
  val forceSplitPaneOnCompactLandscape: Boolean = SignalStore.internal.forceSplitPaneOnCompactLandscape,
  val isRefreshingRecipient: Boolean = false,
  val pendingDestination: RecipientId? = null,
  val userMessage: UserMessage? = null
) {
  sealed interface UserMessage {
    data class RecipientNotSignalUser(val phone: PhoneNumber?) : UserMessage
    data object NetworkError : UserMessage
  }
}
