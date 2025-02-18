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
  private val _recipients = MutableStateFlow<List<Recipient>>(emptyList())
  val recipients = _recipients.asStateFlow()
  private val _events = MutableSharedFlow<Event>()
  val events = _events.asSharedFlow()
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
          _events.emit(Event(EventType.BLOCK_SUCCEEDED, resolved(recipientId)))
        }.onFailure {
          _events.emit(Event(EventType.BLOCK_FAILED, resolved(recipientId)))
        }
    }
  }

  fun createAndBlock(number: String) {
    viewModelScope.launch {
      repository.createAndBlock(number)
        .onSuccess {
          loadRecipients()
          _events.emit(Event(EventType.BLOCK_SUCCEEDED, number))
        }
    }
  }

  fun unblock(recipientId: RecipientId) {
    viewModelScope.launch {
      repository.unblock(recipientId)
        .onSuccess {
          loadRecipients()
          _events.emit(Event(EventType.UNBLOCK_SUCCEEDED, resolved(recipientId)))
        }
    }
  }

   private suspend fun loadRecipients() {
      repository.getBlocked()
        .onSuccess { recipientList ->
          _recipients.update { recipientList }
      }
  }

  enum class EventType{
    BLOCK_SUCCEEDED,
    BLOCK_FAILED,
    UNBLOCK_SUCCEEDED
  }

  data class Event private constructor(val eventType: EventType,
                                       val recipient: Recipient?,
                                       val number: String?){
    constructor(eventType: EventType, recipient: Recipient) : this(eventType, recipient, null)
    constructor(eventType: EventType, number: String) : this(eventType, null, number)
  }
}