/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.screens.EventDrivenViewModel

class MessageSyncViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<MessageSyncScreenEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(MessageSyncViewModel::class)
  }

  private val _state = MutableStateFlow(MessageSyncScreenState())
  val state: StateFlow<MessageSyncScreenState> = _state.asStateFlow()

  // TODO [regv5] wire in message restoration state

  override suspend fun processEvent(event: MessageSyncScreenEvent) {
    when (event) {
      MessageSyncScreenEvent.LearnMoreClick -> error("This event is handled in the nav-entry.")
      MessageSyncScreenEvent.CancelClick -> Unit // TODO [regv5] wire in to actually cancel the download.
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MessageSyncViewModel(repository, parentState, parentEventEmitter) as T
    }
  }
}
