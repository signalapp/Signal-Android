/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.totpentry

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.uicomponents.codeentryfield.CodeEntryFieldAction
import org.signal.uicomponents.codeentryfield.CodeEntryFieldPresenter

/**
 * Drives [TotpEntryScreen]. The six-digit code is collected by a [CodeEntryFieldPresenter], and once it's complete it's
 * emitted via [ResultEventBus], then the two-factor screens are popped so the login screen that bounced here can retry.
 */
class TotpEntryViewModel(
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val resultBus: ResultEventBus,
  private val resultKey: String
) : EventDrivenViewModel<TotpEntryScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(TotpEntryViewModel::class)
  }

  private val _state = MutableStateFlow(TotpEntryState())

  val state: StateFlow<TotpEntryState> = _state.asStateFlow()

  private val codeEntryPresenter = CodeEntryFieldPresenter(viewModelScope)

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    codeEntryPresenter
      .state
      .onEach { onEvent(TotpEntryScreenEvents.CodeEntryStateChanged(it)) }
      .launchIn(viewModelScope)

    codeEntryPresenter
      .actions
      .onEach { onEvent(TotpEntryScreenEvents.CodeEntryAction(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: TotpEntryScreenEvents) {
    when (event) {
      is TotpEntryScreenEvents.CodeEntryEvent -> {
        codeEntryPresenter.onEvent(event.event)
      }
      is TotpEntryScreenEvents.CodeEntryStateChanged -> {
        _state.update { it.copy(codeEntry = event.codeEntryState) }
      }
      is TotpEntryScreenEvents.CodeEntryAction -> {
        when (val action = event.action) {
          is CodeEntryFieldAction.CodeEntered -> emitCode(action.code)
        }
      }
      TotpEntryScreenEvents.CancelClicked -> {
        parentEventEmitter.navigateBack()
      }
    }
  }

  private fun emitCode(code: String) {
    resultBus.sendResult(resultKey, code)
    parentEventEmitter(RegistrationFlowEvent.NavigateBackToScreen(RegistrationRoute.SignalLoginCredentialEntry()))
  }
}
