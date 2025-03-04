/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.RecipientId


class BlockedUsersViewModel(private val repository: BlockedUsersRepository) : ViewModel() {
  private val _events = MutableSharedFlow<BlockUserEvent>()
  val events = _events.asSharedFlow()

  private val _blockedUsers = MutableStateFlow<List<BlockedUserRecipientState>>(emptyList())
  val blockedUsers = _blockedUsers.asStateFlow()

  init {
    viewModelScope.launch {
      loadRecipients()
    }
  }

  fun block(recipientId: RecipientId) {
    viewModelScope.launch {
      repository.block(recipientId)
        .onSuccess {
          loadRecipients()
          _events.emit(BlockUserEvent.BlockSucceeded(resolved(recipientId)))
        }.onFailure {
          _events.emit(BlockUserEvent.BlockFailed(resolved(recipientId)))
        }
    }
  }

  fun createAndBlock(number: String) {
    viewModelScope.launch {
      repository.createAndBlock(number)
        .onSuccess {
          loadRecipients()
          _events.emit(BlockUserEvent.CreateAndBlockSucceeded(number))
        }
    }
  }

  fun unblock(recipientId: RecipientId) {
    viewModelScope.launch {
      repository.unblock(recipientId)
        .onSuccess {
          loadRecipients()
          _events.emit(BlockUserEvent.UnblockSucceeded(resolved(recipientId)))
        }
    }
  }

   private suspend fun loadRecipients() {
      repository.getBlocked()
        .onSuccess { recipientList ->
          _blockedUsers.update { recipientList }
      }
  }

}

sealed interface BlockUserEvent {
  data class BlockSucceeded(val recipient: Recipient) : BlockUserEvent
  data class BlockFailed(val recipient: Recipient): BlockUserEvent
  data class CreateAndBlockSucceeded(val number : String) : BlockUserEvent
  data class UnblockSucceeded(val recipient: Recipient): BlockUserEvent
}