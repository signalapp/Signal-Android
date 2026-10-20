/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.help.LicenseAction
import org.signal.appsettings.help.LicenseEvent
import org.signal.appsettings.help.LicenseScreen
import org.signal.appsettings.help.LicenseState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [LicenseScreen].
 */
class LicenseViewModel : EventDrivenViewModel<LicenseEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(LicenseViewModel::class)
  }

  private val _state = MutableStateFlow(LicenseState())
  private val _actions = Channel<LicenseAction>(Channel.BUFFERED)

  val state: StateFlow<LicenseState> = _state.asStateFlow()
  val actions: Flow<LicenseAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      val lines = LicenseRepository.getLicenseLines()
      _state.update { it.copy(licenseLines = lines) }
    }
  }

  override suspend fun processEvent(event: LicenseEvent) {
    when (event) {
      LicenseEvent.NavigateBackClicked -> _actions.send(LicenseAction.NavigateBack)
    }
  }
}
