/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.account.AccountSettingsAction
import org.signal.appsettings.account.AccountSettingsEvent
import org.signal.appsettings.account.AccountSettingsState
import org.signal.appsettings.account.AccountSettingsState.Dialog
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.lock.v2.SvrConstants

/**
 * Drives the account settings screen shown on a primary device, which is where PIN, registration lock, and account
 * deletion all live.
 */
class AccountSettingsViewModel(
  private val repository: AccountSettingsRepository = AccountSettingsRepository()
) : EventDrivenViewModel<AccountSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(AccountSettingsViewModel::class)

    /** Stand-in for the real key count, which we have nowhere to read from yet. */
    private const val MOCK_SIGNAL_LOGIN_KEY_COUNT = 2
  }

  private val _state = MutableStateFlow(AccountSettingsState())
  private val _actions = Channel<AccountSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<AccountSettingsState> = _state.asStateFlow()
  val actions: Flow<AccountSettingsAction> = _actions.receiveAsFlow()

  init {
    refresh()
  }

  override suspend fun processEvent(event: AccountSettingsEvent) {
    when (event) {
      AccountSettingsEvent.ScreenResumed -> {
        refresh()
      }
      AccountSettingsEvent.NavigateBackClicked -> {
        _actions.send(AccountSettingsAction.NavigateBack)
      }
      AccountSettingsEvent.ModifyPinClicked -> {
        _actions.send(if (_state.value.hasPin) AccountSettingsAction.LaunchChangePinFlow else AccountSettingsAction.LaunchCreatePinFlow)
      }
      AccountSettingsEvent.PinCreated -> {
        refresh()
        _actions.send(AccountSettingsAction.ShowPinCreatedConfirmation)
      }
      is AccountSettingsEvent.PinRemindersToggled -> {
        if (event.enabled) {
          repository.setPinRemindersEnabled(true)
          refresh()
        } else {
          val keyboardType = repository.getPinKeyboardType()
          _state.update { it.copy(dialog = Dialog.ConfirmPinToDisableReminders(isAlphanumericKeyboard = keyboardType == PinKeyboardType.ALPHA_NUMERIC)) }
        }
      }
      is AccountSettingsEvent.PinEntryChanged -> {
        updatePinDialog { it.copy(pin = event.pin, incorrectPin = false, canSubmit = canSubmit(event.pin)) }
      }
      AccountSettingsEvent.PinKeyboardToggled -> {
        updatePinDialog { it.copy(pin = "", isAlphanumericKeyboard = !it.isAlphanumericKeyboard, incorrectPin = false, canSubmit = false) }
      }
      AccountSettingsEvent.DisablePinRemindersConfirmed -> {
        val dialog = _state.value.dialog as? Dialog.ConfirmPinToDisableReminders ?: return

        if (repository.verifyLocalPin(dialog.pin)) {
          repository.setPinRemindersEnabled(false)
          _state.update { it.copy(dialog = Dialog.None) }
          refresh()
        } else {
          updatePinDialog { it.copy(incorrectPin = true) }
        }
      }
      is AccountSettingsEvent.RegistrationLockToggled -> {
        _state.update { it.copy(dialog = Dialog.ConfirmRegistrationLock(enable = event.enabled)) }
      }
      AccountSettingsEvent.RegistrationLockConfirmed -> {
        val dialog = _state.value.dialog as? Dialog.ConfirmRegistrationLock ?: return

        _state.update { it.copy(dialog = dialog.copy(inProgress = true)) }
        val success = repository.setRegistrationLockEnabled(dialog.enable)
        _state.update { it.copy(dialog = Dialog.None) }
        refresh()

        if (!success) {
          _actions.send(
            if (dialog.enable) AccountSettingsAction.ShowRegistrationLockEnableFailed else AccountSettingsAction.ShowRegistrationLockDisableFailed
          )
        }
      }
      AccountSettingsEvent.AuthenticatorAppClicked -> {
        _actions.send(AccountSettingsAction.NavigateToAuthenticatorAppSetup)
      }
      AccountSettingsEvent.AdvancedPinSettingsClicked -> {
        _actions.send(AccountSettingsAction.NavigateToAdvancedPinSettings)
      }
      AccountSettingsEvent.ChangePhoneNumberClicked -> {
        _actions.send(AccountSettingsAction.NavigateToChangePhoneNumber)
      }
      AccountSettingsEvent.TransferAccountClicked -> {
        _actions.send(AccountSettingsAction.NavigateToDeviceTransfer)
      }
      AccountSettingsEvent.RequestAccountDataClicked -> {
        _actions.send(AccountSettingsAction.NavigateToExportAccountData)
      }
      AccountSettingsEvent.UpdateSignalClicked -> {
        _actions.send(AccountSettingsAction.OpenPlayStore)
      }
      AccountSettingsEvent.ReRegisterClicked -> {
        _actions.send(AccountSettingsAction.LaunchReRegistration)
      }
      AccountSettingsEvent.DeleteAllDataClicked -> {
        _state.update { it.copy(dialog = Dialog.ConfirmDeleteAllData) }
      }
      AccountSettingsEvent.DeleteAllDataConfirmed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(AccountSettingsAction.WipeAllData)
      }
      AccountSettingsEvent.DataWipeFailed -> {
        _actions.send(AccountSettingsAction.ShowDataWipeFailed)
      }
      AccountSettingsEvent.DeleteAccountClicked -> {
        _actions.send(AccountSettingsAction.NavigateToDeleteAccount)
      }
      AccountSettingsEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
    }
  }

  private fun refresh() {
    _state.update {
      it.copy(
        hasPin = repository.hasPin(),
        hasRestoredAep = repository.hasRestoredAep(),
        pinRemindersEnabled = repository.arePinRemindersEnabled(),
        registrationLockEnabled = repository.isRegistrationLockEnabled(),
        userUnregistered = repository.isUserUnregistered(),
        clientDeprecated = repository.isClientDeprecated(),
        signalLogin = if (repository.isPhoneNumberlessRegistrationEnabled()) {
          AccountSettingsState.SignalLogin(
            keyCount = MOCK_SIGNAL_LOGIN_KEY_COUNT,
            hasAuthenticatorApp = repository.hasAuthenticatorApp()
          )
        } else {
          null
        }
      )
    }
  }

  private fun canSubmit(pin: String): Boolean = pin.length >= SvrConstants.MINIMUM_PIN_LENGTH

  private fun updatePinDialog(transform: (Dialog.ConfirmPinToDisableReminders) -> Dialog.ConfirmPinToDisableReminders) {
    _state.update { state ->
      val dialog = state.dialog as? Dialog.ConfirmPinToDisableReminders ?: return@update state
      state.copy(dialog = transform(dialog))
    }
  }
}
