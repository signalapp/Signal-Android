/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreenEvents
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsState

/**
 * Drives the screen that shows the user the account and recovery keys that make up their Signal Login, reached from
 * account settings.
 *
 * @param showResetRecoveryKeyButton True if the option to reset the recovery key should be offered in the UI.
 * @param isPasswordManagerAvailable False if the device has no password manager, which leaves the save button showing
 *   but styled as disabled.
 */
class SettingsSignalLoginDetailsViewModel(
  private val repository: SignalLoginViewDetailsRepository = SignalLoginViewDetailsRepository(),
  showResetRecoveryKeyButton: Boolean = false,
  isPasswordManagerAvailable: Boolean = true
) : EventDrivenViewModel<SettingsSignalLoginDetailsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(SettingsSignalLoginDetailsViewModel::class)
  }

  private val _state = MutableStateFlow(
    SignalLoginViewDetailsState(
      accountKey = repository.getAci()?.toString()?.uppercase().orEmpty(),
      recoveryKey = repository.getAccountEntropyPool()?.displayValue.orEmpty(),
      isPasswordManagerAvailable = isPasswordManagerAvailable,
      showResetRecoveryKeyButton = showResetRecoveryKeyButton,
      resetRecoveryKeyButtonLoading = showResetRecoveryKeyButton
    )
  )
  private val _resetRecoveryKeyState = MutableStateFlow(ResetRecoveryKeyState(areBackupsEnabled = repository.areBackupsEnabled()))
  private val _actions = Channel<SignalLoginViewDetailsAction>(Channel.BUFFERED)

  val state: StateFlow<SignalLoginViewDetailsState> = _state.asStateFlow()
  val resetRecoveryKeyState: StateFlow<ResetRecoveryKeyState> = _resetRecoveryKeyState.asStateFlow()
  val actions: Flow<SignalLoginViewDetailsAction> = _actions.receiveAsFlow()

  init {
    if (showResetRecoveryKeyButton) {
      refreshResetLimit()
    }
  }

  override suspend fun processEvent(event: SettingsSignalLoginDetailsEvent) {
    when (event) {
      is SettingsSignalLoginDetailsEvent.Screen -> {
        processScreenEvent(event.event)
      }

      SettingsSignalLoginDetailsEvent.ResetRecoveryKeyConfirmed -> {
        if (repository.isOptimizedStorageEnabled()) {
          _resetRecoveryKeyState.update { it.copy(dialog = ResetRecoveryKeyState.Dialog.DOWNLOAD_MEDIA) }
        } else {
          _resetRecoveryKeyState.update { it.copy(dialog = ResetRecoveryKeyState.Dialog.NONE) }
          _actions.send(SignalLoginViewDetailsAction.LaunchRecoveryKeyReset)
        }
      }

      SettingsSignalLoginDetailsEvent.TurnOffOptimizedStorageClicked -> {
        repository.turnOffOptimizedStorageAndDownloadMedia()
        _resetRecoveryKeyState.update { it.copy(dialog = ResetRecoveryKeyState.Dialog.NONE) }
        _actions.send(SignalLoginViewDetailsAction.NavigateBack)
      }

      SettingsSignalLoginDetailsEvent.ResetRecoveryKeyDismissed -> {
        _resetRecoveryKeyState.update { it.copy(dialog = ResetRecoveryKeyState.Dialog.NONE) }
      }

      SettingsSignalLoginDetailsEvent.RecoveryKeyRotated -> {
        _state.update {
          it.copy(
            accountKey = repository.getAci()?.toString()?.uppercase().orEmpty(),
            recoveryKey = repository.getAccountEntropyPool()?.displayValue.orEmpty(),
            resetRecoveryKeyButtonLoading = it.showResetRecoveryKeyButton
          )
        }
        _resetRecoveryKeyState.update { it.copy(hasResetPermitsRemaining = null) }
        refreshResetLimit()
      }
    }
  }

  private suspend fun processScreenEvent(event: SignalLoginViewDetailsScreenEvents) {
    when (event) {
      SignalLoginViewDetailsScreenEvents.BackClicked -> {
        _actions.send(SignalLoginViewDetailsAction.NavigateBack)
      }
      SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked -> {
        if (_state.value.isPasswordManagerAvailable) {
          _actions.send(SignalLoginViewDetailsAction.LaunchSaveToPasswordManager)
        } else {
          _actions.send(SignalLoginViewDetailsAction.ShowNoPasswordManagerAvailable)
        }
      }
      SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked -> {
        _actions.send(SignalLoginViewDetailsAction.LaunchSaveAsPdf)
      }
      SignalLoginViewDetailsScreenEvents.ResetRecoveryKeyClicked -> {
        _resetRecoveryKeyState.update {
          when (it.hasResetPermitsRemaining) {
            true -> it.copy(dialog = ResetRecoveryKeyState.Dialog.CONFIRMATION)
            false -> it.copy(dialog = ResetRecoveryKeyState.Dialog.KEY_LIMIT_REACHED)
            null -> {
              Log.w(TAG, "Reset clicked before the limit was known. Ignoring.")
              it
            }
          }
        }
      }
      is SignalLoginViewDetailsScreenEvents.CopyAccountIdClicked -> {
        _actions.send(SignalLoginViewDetailsAction.CopyTextToClipboard(event.aci))
      }
      is SignalLoginViewDetailsScreenEvents.CopyRecoveryKeyClicked -> {
        _actions.send(SignalLoginViewDetailsAction.CopyTextToClipboard(event.aep))
      }
    }
  }

  private fun refreshResetLimit() {
    viewModelScope.launch {
      val canReset = repository.canResetRecoveryKey()
      _resetRecoveryKeyState.update { it.copy(hasResetPermitsRemaining = canReset) }
      _state.update { it.copy(resetRecoveryKeyButtonLoading = false) }
    }
  }
}
