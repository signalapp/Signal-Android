/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.twofactorselection

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
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

/**
 * Drives [TwoFactorSelectionScreen].
 */
class TwoFactorSelectionViewModel(
  methods: List<TwoFactorMethod>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<TwoFactorSelectionScreenEvents>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(TwoFactorSelectionViewModel::class)
  }

  private val _state = MutableStateFlow(TwoFactorSelectionState(methods = methods))
  private val _actions = Channel<TwoFactorSelectionAction>(Channel.BUFFERED)

  val state: StateFlow<TwoFactorSelectionState> = _state.asStateFlow()
  val actions: Flow<TwoFactorSelectionAction> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: TwoFactorSelectionScreenEvents) {
    when (event) {
      is TwoFactorSelectionScreenEvents.MethodSelected -> {
        when (val method = event.method) {
          is TwoFactorMethod.Passkey -> _actions.send(TwoFactorSelectionAction.AuthenticateWithPasskey(method.parameters))
          TwoFactorMethod.AuthenticatorApp -> parentEventEmitter.navigateTo(RegistrationRoute.TotpEntry)
        }
      }
      TwoFactorSelectionScreenEvents.CancelClicked -> {
        parentEventEmitter.navigateBack()
      }
      TwoFactorSelectionScreenEvents.PasskeyCeremonyFailed -> {
        _state.update { it.copy(showPasskeyError = true) }
      }
      TwoFactorSelectionScreenEvents.PasskeyErrorDismissed -> {
        _state.update { it.copy(showPasskeyError = false) }
      }
    }
  }
}
