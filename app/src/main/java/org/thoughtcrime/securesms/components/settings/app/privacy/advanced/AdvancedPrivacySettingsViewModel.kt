/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsAction
import org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsEvent
import org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsScreen].
 */
class AdvancedPrivacySettingsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  connectionChanges: Flow<Unit> = AdvancedPrivacySettingsRepository.connectionChanges()
) : EventDrivenViewModel<AdvancedPrivacySettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(AdvancedPrivacySettingsViewModel::class)
  }

  private val _state = MutableStateFlow(AdvancedPrivacySettingsRepository.getState())
  private val _actions = Channel<AdvancedPrivacySettingsAction>(Channel.BUFFERED)

  val state: StateFlow<AdvancedPrivacySettingsState> = _state.asStateFlow()
  val actions: Flow<AdvancedPrivacySettingsAction> = _actions.receiveAsFlow()

  init {
    connectionChanges
      .onEach { refresh() }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: AdvancedPrivacySettingsEvent) {
    when (event) {
      AdvancedPrivacySettingsEvent.ScreenResumed -> refresh()
      AdvancedPrivacySettingsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      is AdvancedPrivacySettingsEvent.AlwaysRelayCallsToggled -> {
        AdvancedPrivacySettingsRepository.setAlwaysRelayCalls(event.enabled)
        refresh()
      }
      is AdvancedPrivacySettingsEvent.CensorshipCircumventionToggled -> {
        AdvancedPrivacySettingsRepository.setCensorshipCircumventionEnabled(event.enabled)
        refresh()
      }
      is AdvancedPrivacySettingsEvent.SealedSenderStatusIconToggled -> {
        AdvancedPrivacySettingsRepository.setShowSealedSenderStatusIcon(event.enabled)
        refresh()
      }
      is AdvancedPrivacySettingsEvent.SealedSenderFromAnyoneToggled -> {
        AdvancedPrivacySettingsRepository.setAllowSealedSenderFromAnyone(event.enabled)
        refresh()
      }
      AdvancedPrivacySettingsEvent.SealedSenderLearnMoreClicked -> _actions.send(AdvancedPrivacySettingsAction.OpenSealedSenderLearnMore)
      is AdvancedPrivacySettingsEvent.AutomaticKeyVerificationToggled -> {
        AdvancedPrivacySettingsRepository.setAutomaticKeyVerificationEnabled(event.enabled)
        refresh()
        viewModelScope.launch { AdvancedPrivacySettingsRepository.syncAutomaticKeyVerification(event.enabled) }
      }
      AdvancedPrivacySettingsEvent.AutomaticKeyVerificationLearnMoreClicked -> _actions.send(AdvancedPrivacySettingsAction.OpenAutomaticKeyVerificationLearnMore)
    }
  }

  private fun refresh() {
    _state.value = AdvancedPrivacySettingsRepository.getState()
  }
}
