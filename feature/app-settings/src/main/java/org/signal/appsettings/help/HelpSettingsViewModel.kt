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
import kotlinx.coroutines.flow.update
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [HelpSettingsScreen].
 */
class HelpSettingsViewModel(
  versionName: String,
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
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
      HelpSettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      HelpSettingsEvent.SupportCenterClicked -> _actions.send(HelpSettingsAction.OpenSupportCenter)
      HelpSettingsEvent.ContactUsClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.HelpRoute.Help())
      HelpSettingsEvent.VersionLongClicked -> {
        _actions.send(HelpSettingsAction.CopyVersionToClipboard(_state.value.versionName))
        _state.update { it.copy(snackbar = HelpSettingsState.Snackbar.VersionCopied) }
      }
      HelpSettingsEvent.DebugLogClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.HelpRoute.DebugLog)
      HelpSettingsEvent.LicensesClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.HelpRoute.Licenses)
      HelpSettingsEvent.TermsAndPrivacyPolicyClicked -> _actions.send(HelpSettingsAction.OpenTermsAndPrivacyPolicy)
      HelpSettingsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = HelpSettingsState.Snackbar.None) }
    }
  }
}
