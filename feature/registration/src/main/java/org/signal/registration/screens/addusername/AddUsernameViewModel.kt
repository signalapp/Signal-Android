/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.addusername

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository

/**
 * View model for [AddUsernameScreen].
 *
 * Username reservation and confirmation endpoints aren't wired into this flow yet, so nothing is validated or
 * submitted. Every event the screen can produce is routed here and handled explicitly so that filling in the business
 * logic is a matter of replacing the TODO branches.
 */
class AddUsernameViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<AddUsernameScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(AddUsernameViewModel::class)
  }

  private val _state = MutableStateFlow(AddUsernameState())
  val state: StateFlow<AddUsernameState> = _state.asStateFlow()

  private val _actions = Channel<AddUsernameScreenActions>(Channel.BUFFERED)
  val actions: Flow<AddUsernameScreenActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: AddUsernameScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: AddUsernameState,
    event: AddUsernameScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (AddUsernameState) -> Unit
  ) {
    when (event) {
      is AddUsernameScreenEvents.UsernameChanged -> {
        // TODO [phonenumberless] Validate the nickname and populate AddUsernameState.validationError.
        stateEmitter(state.copy(username = event.value))
      }

      is AddUsernameScreenEvents.LearnMoreClicked -> {
        _actions.trySend(AddUsernameScreenActions.OpenLearnMoreArticle)
      }

      is AddUsernameScreenEvents.SkipClicked -> {
        // TODO [phonenumberless] Advance the flow without reserving a username.
        Log.i(TAG, "Skip clicked, but the flow isn't implemented yet.")
      }

      is AddUsernameScreenEvents.NextClicked -> {
        // TODO [phonenumberless] Reserve and confirm the username, then advance the flow.
        Log.i(TAG, "Next clicked, but the flow isn't implemented yet.")
      }

      is AddUsernameScreenEvents.NetworkErrorDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(networkError = false)))
      }

      is AddUsernameScreenEvents.UnknownErrorDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(unknownError = false)))
      }

      is AddUsernameScreenEvents.UsernameUnavailableDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(usernameUnavailable = false)))
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return AddUsernameViewModel(repository, parentEventEmitter) as T
    }
  }
}
