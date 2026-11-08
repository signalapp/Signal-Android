/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsAction
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsEvent
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import kotlin.time.Duration.Companion.seconds

/**
 * Drives [org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsScreen].
 */
class PhoneNumberPrivacySettingsViewModel(
  refreshTicks: Flow<Unit> = defaultRefreshTicks()
) : EventDrivenViewModel<PhoneNumberPrivacySettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(PhoneNumberPrivacySettingsViewModel::class)

    /** Storage sync can change these settings from a linked device while the screen is open. */
    private fun defaultRefreshTicks(): Flow<Unit> = flow {
      while (true) {
        delay(5.seconds)
        emit(Unit)
      }
    }
  }

  private val _state = MutableStateFlow(PhoneNumberPrivacySettingsState())
  private val _actions = Channel<PhoneNumberPrivacySettingsAction>(Channel.BUFFERED)

  val state: StateFlow<PhoneNumberPrivacySettingsState> = _state.asStateFlow()
  val actions: Flow<PhoneNumberPrivacySettingsAction> = _actions.receiveAsFlow()

  init {
    refresh()

    refreshTicks
      .onEach { refresh() }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: PhoneNumberPrivacySettingsEvent) {
    when (event) {
      PhoneNumberPrivacySettingsEvent.NavigateBackClicked -> _actions.send(PhoneNumberPrivacySettingsAction.NavigateBack)

      PhoneNumberPrivacySettingsEvent.EveryoneCanSeeMyNumberClicked -> {
        PhoneNumberPrivacySettingsRepository.setPhoneNumberSharingEnabled(true)
        PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(true)
        refresh()
      }

      PhoneNumberPrivacySettingsEvent.NobodyCanSeeMyNumberClicked -> {
        PhoneNumberPrivacySettingsRepository.setPhoneNumberSharingEnabled(false)
        refresh()
      }

      PhoneNumberPrivacySettingsEvent.EveryoneCanFindMeByNumberClicked -> {
        PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(true)
        refresh()
      }

      PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberClicked -> {
        if (_state.value.phoneNumberSharing) {
          _state.update { it.copy(snackbar = PhoneNumberPrivacySettingsState.Snackbar.HideNumberFirst) }
        } else {
          _state.update { it.copy(dialog = PhoneNumberPrivacySettingsState.Dialog.ConfirmNotDiscoverable) }
        }
      }

      PhoneNumberPrivacySettingsEvent.NobodyCanFindMeByNumberConfirmed -> {
        if (_state.value.dialog != PhoneNumberPrivacySettingsState.Dialog.ConfirmNotDiscoverable) return
        _state.update { it.copy(dialog = PhoneNumberPrivacySettingsState.Dialog.None) }
        PhoneNumberPrivacySettingsRepository.setDiscoverableByPhoneNumber(false)
        refresh()
      }

      PhoneNumberPrivacySettingsEvent.DialogDismissed -> _state.update { it.copy(dialog = PhoneNumberPrivacySettingsState.Dialog.None) }

      PhoneNumberPrivacySettingsEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = PhoneNumberPrivacySettingsState.Snackbar.None) }
    }
  }

  private fun refresh() {
    _state.update {
      it.copy(
        phoneNumberSharing = PhoneNumberPrivacySettingsRepository.isPhoneNumberSharingEnabled(),
        discoverableByPhoneNumber = PhoneNumberPrivacySettingsRepository.isDiscoverableByPhoneNumber()
      )
    }
  }
}
