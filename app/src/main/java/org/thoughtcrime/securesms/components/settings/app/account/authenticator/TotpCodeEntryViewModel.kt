/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

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
import org.signal.appsettings.totpcodeentry.TotpCodeEntryAction
import org.signal.appsettings.totpcodeentry.TotpCodeEntryEvent
import org.signal.appsettings.totpcodeentry.TotpCodeEntryState
import org.signal.appsettings.totpcodeentry.TotpCodeEntryState.Error
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.uicomponents.codeentryfield.CodeEntryFieldPresenter

/**
 * Drives the screen that collects a code from the user's authenticator app, which is how the service learns the user
 * kept a copy of the key it just handed out.
 */
class TotpCodeEntryViewModel(
  private val repository: TotpRepository = TotpRepository()
) : EventDrivenViewModel<TotpCodeEntryEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(TotpCodeEntryViewModel::class)
  }

  private val _state = MutableStateFlow(TotpCodeEntryState())
  private val _actions = Channel<TotpCodeEntryAction>(Channel.BUFFERED)

  val state: StateFlow<TotpCodeEntryState> = _state.asStateFlow()
  val actions: Flow<TotpCodeEntryAction> = _actions.receiveAsFlow()

  private val codeEntryPresenter = CodeEntryFieldPresenter(viewModelScope)

  init {
    codeEntryPresenter
      .state
      .onEach { onEvent(TotpCodeEntryEvent.CodeEntryStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: TotpCodeEntryEvent) {
    when (event) {
      TotpCodeEntryEvent.NavigateBackClicked -> {
        _actions.send(TotpCodeEntryAction.NavigateBack)
      }
      is TotpCodeEntryEvent.CodeEntryEvent -> {
        _state.update { it.copy(error = Error.None) }
        codeEntryPresenter.onEvent(event.event)
      }
      is TotpCodeEntryEvent.CodeEntryStateChanged -> {
        _state.update { it.copy(codeEntry = event.codeEntryState) }
      }
      TotpCodeEntryEvent.NextClicked -> {
        if (!_state.value.canSubmit) {
          return
        }

        _state.update { it.copy(submitting = true, error = Error.None) }

        confirmNewApp()
      }
    }
  }

  private suspend fun confirmNewApp() {
    when (val result = repository.confirmPendingApp(_state.value.code)) {
      is TotpRepository.ConfirmResult.Success -> {
        _actions.send(TotpCodeEntryAction.NavigateToNaming(result.appId))
      }
      TotpRepository.ConfirmResult.IncorrectCode -> {
        fail(Error.IncorrectCode)
      }
      TotpRepository.ConfirmResult.TooManyApps -> {
        Log.w(TAG, "The account filled up while this key was pending. Going back to setup, which will explain the limit.")
        _state.update { it.copy(submitting = false) }
        _actions.send(TotpCodeEntryAction.NavigateToSetup)
      }
      TotpRepository.ConfirmResult.NetworkFailure -> {
        fail(Error.NetworkFailure)
      }
    }
  }

  private fun fail(error: Error) {
    _state.update { it.copy(submitting = false, error = error) }
  }
}
