/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.screenlock.ScreenLockSettingsAction
import org.signal.appsettings.screenlock.ScreenLockSettingsEvent
import org.signal.appsettings.screenlock.ScreenLockSettingsState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [org.signal.appsettings.screenlock.ScreenLockSettingsScreen].
 */
class ScreenLockSettingsViewModel : EventDrivenViewModel<ScreenLockSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(ScreenLockSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(ScreenLockSettingsState())
  private val _actions = Channel<ScreenLockSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<ScreenLockSettingsState> = _state.asStateFlow()
  val actions: Flow<ScreenLockSettingsAction> = _actions.receiveAsFlow()

  init {
    refresh()
  }

  override suspend fun processEvent(event: ScreenLockSettingsEvent) {
    when (event) {
      ScreenLockSettingsEvent.ScreenResumed -> refresh()

      ScreenLockSettingsEvent.NavigateBackClicked -> _actions.send(ScreenLockSettingsAction.NavigateBack)

      is ScreenLockSettingsEvent.ScreenLockToggled -> {
        ScreenLockSettingsRepository.setScreenLockEnabled(event.enabled)
        refresh()
      }

      is ScreenLockSettingsEvent.TimeoutSelected -> {
        ScreenLockSettingsRepository.setTimeout(event.timeout)
        refresh()
      }

      ScreenLockSettingsEvent.CustomTimeoutClicked -> _state.update { it.copy(dialog = ScreenLockSettingsState.Dialog.CustomTimeout) }

      is ScreenLockSettingsEvent.CustomTimeoutSelected -> {
        if (_state.value.dialog != ScreenLockSettingsState.Dialog.CustomTimeout) return
        _state.update { it.copy(dialog = ScreenLockSettingsState.Dialog.None) }
        ScreenLockSettingsRepository.setTimeout(event.timeout)
        refresh()
      }

      ScreenLockSettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = ScreenLockSettingsState.Dialog.None) }
    }
  }

  private fun refresh() {
    _state.update {
      it.copy(
        isScreenLockEnabled = ScreenLockSettingsRepository.isScreenLockEnabled(),
        timeout = ScreenLockSettingsRepository.getTimeout(),
        canAuthenticate = ScreenLockSettingsRepository.canAuthenticate()
      )
    }
  }
}
