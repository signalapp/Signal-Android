/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.passkeyverification

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.screens.twofactorselection.WebAuthnParameters
import org.signal.registration.screens.util.navigateBack

/**
 * Drives [PasskeyVerificationScreen].
 */
class PasskeyVerificationViewModel(
  private val parameters: WebAuthnParameters,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<PasskeyVerificationScreenEvents>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(PasskeyVerificationViewModel::class)
  }

  private val _state = MutableStateFlow(PasskeyVerificationState())
  private val _actions = Channel<PasskeyVerificationAction>(Channel.BUFFERED)

  val state: StateFlow<PasskeyVerificationState> = _state.asStateFlow()
  val actions: Flow<PasskeyVerificationAction> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: PasskeyVerificationScreenEvents) {
    when (event) {
      PasskeyVerificationScreenEvents.ContinueClicked -> {
        _actions.send(PasskeyVerificationAction.AuthenticateWithPasskey(parameters))
      }
      PasskeyVerificationScreenEvents.CancelClicked -> {
        parentEventEmitter.navigateBack()
      }
      PasskeyVerificationScreenEvents.PasskeyCeremonyFailed -> {
        _state.update { it.copy(showPasskeyError = true) }
      }
      PasskeyVerificationScreenEvents.PasskeyErrorDismissed -> {
        _state.update { it.copy(showPasskeyError = false) }
      }
    }
  }
}
