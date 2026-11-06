/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.advancedpin.AdvancedPinSettingsAction
import org.signal.appsettings.advancedpin.AdvancedPinSettingsEvent
import org.signal.appsettings.advancedpin.AdvancedPinSettingsState
import org.signal.appsettings.advancedpin.AdvancedPinSettingsState.Dialog
import org.signal.appsettings.advancedpin.AdvancedPinSettingsState.Snackbar
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [org.signal.appsettings.advancedpin.AdvancedPinSettingsScreen].
 */
class AdvancedPinSettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<AdvancedPinSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(AdvancedPinSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(AdvancedPinSettingsState(hasOptedOutOfPin = AdvancedPinSettingsRepository.hasOptedOutOfPin()))
  private val _actions = Channel<AdvancedPinSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<AdvancedPinSettingsState> = _state.asStateFlow()
  val actions: Flow<AdvancedPinSettingsAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: AdvancedPinSettingsEvent) {
    when (event) {
      AdvancedPinSettingsEvent.ScreenResumed -> refresh()
      AdvancedPinSettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      AdvancedPinSettingsEvent.PinToggleClicked -> onPinToggleClicked()
      AdvancedPinSettingsEvent.RecordRecoveryPhraseConfirmed -> {
        dismissDialog()
        _actions.send(AdvancedPinSettingsAction.LaunchRecoveryPhraseRecording)
      }
      AdvancedPinSettingsEvent.RotateBackupKeyConfirmed -> {
        dismissDialog()
        parentEventEmitter.navigateTo(AppSettingsRoute.BackupsRoute.DisplayKey(startWithKeyRotation = true))
      }
      AdvancedPinSettingsEvent.DialogDismissed -> dismissDialog()
      AdvancedPinSettingsEvent.PinOptedOut -> {
        refresh()
        _state.update { it.copy(snackbar = Snackbar.PinDisabled) }
      }
      AdvancedPinSettingsEvent.PinCreated -> {
        refresh()
        _state.update { it.copy(snackbar = Snackbar.PinCreated) }
      }
      AdvancedPinSettingsEvent.BackupKeyRotated -> {
        dismissDialog()
        AdvancedPinSettingsRepository.optOutOfPinAfterKeyRotation()
        refresh()
        _state.update { it.copy(snackbar = Snackbar.PinDisabled) }
      }
      AdvancedPinSettingsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = Snackbar.None) }
    }
  }

  /** Turning the PIN back on is always allowed; turning it off has to clear whatever depends on it first. */
  private suspend fun onPinToggleClicked() {
    if (_state.value.hasOptedOutOfPin) {
      dismissDialog()
      _actions.send(AdvancedPinSettingsAction.LaunchPinCreation)
      return
    }

    when {
      AdvancedPinSettingsRepository.isRegistrationLockEnabled() -> _state.update { it.copy(dialog = Dialog.RegistrationLockRequiresPin) }
      AdvancedPinSettingsRepository.needsPaymentsRecoveryPhrase() -> _state.update { it.copy(dialog = Dialog.RecordPaymentsRecoveryPhrase) }
      AdvancedPinSettingsRepository.areBackupsEnabled() -> _state.update { it.copy(dialog = Dialog.RotateBackupKey) }
      else -> {
        dismissDialog()
        _actions.send(AdvancedPinSettingsAction.ConfirmPinOptOut)
      }
    }
  }

  private fun refresh() {
    _state.update { it.copy(hasOptedOutOfPin = AdvancedPinSettingsRepository.hasOptedOutOfPin()) }
  }

  private fun dismissDialog() {
    _state.update { it.copy(dialog = Dialog.None) }
  }
}
