/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.help.LicenseEvent
import org.signal.appsettings.help.LicenseScreen
import org.signal.appsettings.help.LicenseState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [LicenseScreen].
 */
class LicenseViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<LicenseEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(LicenseViewModel::class)
  }

  private val _state = MutableStateFlow(LicenseState())

  val state: StateFlow<LicenseState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      val lines = LicenseRepository.getLicenseLines()
      _state.update { it.copy(licenseLines = lines) }
    }
  }

  override suspend fun processEvent(event: LicenseEvent) {
    when (event) {
      LicenseEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
    }
  }
}
