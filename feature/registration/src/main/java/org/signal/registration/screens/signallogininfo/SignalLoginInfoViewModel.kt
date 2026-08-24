/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.screens.util.navigateBack

/**
 * View model for [SignalLoginInfoScreen].
 *
 * The credentials this screen displays come from a purchase endpoint that doesn't exist yet, so nothing is loaded and
 * neither save action does anything. Every event the screen can produce is routed here and handled explicitly so that
 * filling in the business logic is a matter of replacing the TODO branches.
 */
class SignalLoginInfoViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  isPasswordManagerAvailable: Boolean
) : EventDrivenViewModel<SignalLoginInfoScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginInfoViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginInfoState(isPasswordManagerAvailable = isPasswordManagerAvailable))
  val state: StateFlow<SignalLoginInfoState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    onEvent(SignalLoginInfoScreenEvents.Initialize)
  }

  override suspend fun processEvent(event: SignalLoginInfoScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: SignalLoginInfoState,
    event: SignalLoginInfoScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginInfoState) -> Unit
  ) {
    when (event) {
      is SignalLoginInfoScreenEvents.Initialize -> {
        // TODO [phonenumberless] Load the purchased account identifier and recovery key.
      }

      is SignalLoginInfoScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }

      is SignalLoginInfoScreenEvents.ViewDetailsClicked -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(credentialDetails = true)))
      }

      is SignalLoginInfoScreenEvents.CredentialDetailsDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(credentialDetails = false)))
      }

      is SignalLoginInfoScreenEvents.SaveToPasswordManagerClicked -> {
        // TODO [phonenumberless] Store the credentials via the credential manager, then advance the flow.
        Log.i(TAG, "Save to password manager clicked, but the flow isn't implemented yet.")
      }

      is SignalLoginInfoScreenEvents.SaveManuallyClicked -> {
        // TODO [phonenumberless] Advance to the confirm-you-saved-it step.
        Log.i(TAG, "Save manually clicked, but the flow isn't implemented yet.")
      }

      is SignalLoginInfoScreenEvents.SaveFailedDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(saveFailed = false)))
      }

      is SignalLoginInfoScreenEvents.UnknownErrorDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(unknownError = false)))
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val isPasswordManagerAvailable: Boolean
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SignalLoginInfoViewModel(repository, parentEventEmitter, isPasswordManagerAvailable) as T
    }
  }
}
