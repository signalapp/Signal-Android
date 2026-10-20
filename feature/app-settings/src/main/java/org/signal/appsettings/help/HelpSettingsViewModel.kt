/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [HelpSettingsScreen].
 */
class HelpSettingsViewModel(
  versionName: String
) : EventDrivenViewModel<HelpSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(HelpSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(HelpSettingsState(versionName = versionName))
  private val _actions = Channel<HelpSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<HelpSettingsState> = _state.asStateFlow()
  val actions: Flow<HelpSettingsAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: HelpSettingsEvent) {
    when (event) {
      HelpSettingsEvent.NavigateBackClicked -> _actions.send(HelpSettingsAction.NavigateBack)
      HelpSettingsEvent.SupportCenterClicked -> _actions.send(HelpSettingsAction.OpenSupportCenter)
      HelpSettingsEvent.ContactUsClicked -> _actions.send(HelpSettingsAction.NavigateToContactUs)
      HelpSettingsEvent.VersionLongClicked -> _actions.send(HelpSettingsAction.CopyVersionToClipboard(_state.value.versionName))
      HelpSettingsEvent.DebugLogClicked -> _actions.send(HelpSettingsAction.NavigateToDebugLog)
      HelpSettingsEvent.LicensesClicked -> _actions.send(HelpSettingsAction.NavigateToLicenses)
      HelpSettingsEvent.TermsAndPrivacyPolicyClicked -> _actions.send(HelpSettingsAction.OpenTermsAndPrivacyPolicy)
    }
  }
}
