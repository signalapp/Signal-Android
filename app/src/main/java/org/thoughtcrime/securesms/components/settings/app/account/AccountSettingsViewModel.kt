/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.account.AccountSettingsAction
import org.signal.appsettings.account.AccountSettingsEvent
import org.signal.appsettings.account.AccountSettingsState
import org.signal.appsettings.account.AccountSettingsState.Dialog
import org.signal.appsettings.account.AccountSettingsState.LoadState
import org.signal.appsettings.account.AccountSettingsState.SignalLogin
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.MfaKeyKind
import org.signal.libsignal.net.RequestResult
import org.signal.network.service.TwoFactorMethodService
import org.signal.passwordmanager.PasskeyCreationResult
import org.thoughtcrime.securesms.components.settings.app.account.AccountSettingsRepository.FinishPasskeyRegistrationResult
import org.thoughtcrime.securesms.components.settings.app.account.AccountSettingsRepository.StartPasskeyRegistrationResult
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.lock.v2.SvrConstants
import org.thoughtcrime.securesms.net.SignalNetwork
import org.signal.network.service.TwoFactorMethodService.TwoFactorMethod as ServiceTwoFactorMethod

/**
 * Drives the account settings screen shown on a primary device, which is where PIN, registration lock, and account
 * deletion all live.
 */
class AccountSettingsViewModel(
  private val accountRepository: AccountSettingsRepository,
  arePasskeysSupported: Boolean,
  private val twoFactorMethodService: TwoFactorMethodService = SignalNetwork.twoFactorMethodService
) : EventDrivenViewModel<AccountSettingsEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(AccountSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(AccountSettingsState(arePasskeysSupported = arePasskeysSupported))
  private val _actions = Channel<AccountSettingsAction>(Channel.BUFFERED)

  val state: StateFlow<AccountSettingsState> = _state.asStateFlow()
  val actions: Flow<AccountSettingsAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch { refresh() }
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
        applyPinRemindersToggled(event.enabled)
      }
      is AccountSettingsEvent.PinEntryChanged -> {
        updatePinDialog { it.copy(pin = event.pin, incorrectPin = false, canSubmit = canSubmit(event.pin)) }
      }
      AccountSettingsEvent.PinKeyboardToggled -> {
        updatePinDialog { it.copy(pin = "", isAlphanumericKeyboard = !it.isAlphanumericKeyboard, incorrectPin = false, canSubmit = false) }
      }
      AccountSettingsEvent.DisablePinRemindersConfirmed -> {
        applyDisablePinRemindersConfirmed()
      }
      is AccountSettingsEvent.RegistrationLockToggled -> {
        _state.update { it.copy(dialog = Dialog.ConfirmRegistrationLock(enable = event.enabled)) }
      }
      AccountSettingsEvent.RegistrationLockConfirmed -> {
        applyRegistrationLockConfirmed()
      }
      AccountSettingsEvent.AccountAndRecoveryClicked -> {
        _actions.send(AccountSettingsAction.AuthenticateToViewSignalLoginDetails)
      }
      AccountSettingsEvent.SignalLoginDetailsAuthenticated -> {
        _actions.send(AccountSettingsAction.NavigateToSignalLoginDetails)
      }
      AccountSettingsEvent.AddTotpAppClicked -> {
        applyAddTotpAppClicked()
      }
      AccountSettingsEvent.AddPasskeyClicked -> {
        applyAddPasskeyClicked()
      }
      is AccountSettingsEvent.PasskeyCeremonyCompleted -> {
        applyPasskeyCeremonyCompleted(event.result)
      }
      is AccountSettingsEvent.LearnMoreClicked -> {
        _actions.send(AccountSettingsAction.OpenSupportArticle(event.url))
      }
      is AccountSettingsEvent.RenameMethodClicked -> {
        _actions.send(AccountSettingsAction.NavigateToRenameMethod(event.method))
      }
      is AccountSettingsEvent.RemoveMethodClicked -> {
        _actions.send(AccountSettingsAction.AuthenticateToRemoveMethod(event.method))
      }
      is AccountSettingsEvent.MethodRemovalAuthenticated -> {
        applyMethodRemovalAuthenticated(event.method)
      }
      AccountSettingsEvent.AuthenticationFailed -> {
        _actions.send(AccountSettingsAction.ShowAuthenticationFailed)
      }
      is AccountSettingsEvent.RemoveMethodConfirmed -> {
        applyRemoveMethodConfirmed(event.method)
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
        _actions.send(AccountSettingsAction.AuthenticateToDeleteAccount)
      }
      AccountSettingsEvent.DeleteAccountAuthenticated -> {
        _actions.send(AccountSettingsAction.NavigateToDeleteAccount)
      }
      AccountSettingsEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
    }
  }

  private suspend fun applyPinRemindersToggled(enabled: Boolean) {
    if (enabled) {
      accountRepository.setPinRemindersEnabled(true)
      refresh()
    } else {
      val keyboardType = accountRepository.getPinKeyboardType()
      _state.update { it.copy(dialog = Dialog.ConfirmPinToDisableReminders(isAlphanumericKeyboard = keyboardType == PinKeyboardType.ALPHA_NUMERIC)) }
    }
  }

  private suspend fun applyDisablePinRemindersConfirmed() {
    val dialog = _state.value.dialog as? Dialog.ConfirmPinToDisableReminders ?: return

    if (accountRepository.verifyLocalPin(dialog.pin)) {
      accountRepository.setPinRemindersEnabled(false)
      _state.update { it.copy(dialog = Dialog.None) }
      refresh()
    } else {
      updatePinDialog { it.copy(incorrectPin = true) }
    }
  }

  private suspend fun applyRegistrationLockConfirmed() {
    val dialog = _state.value.dialog as? Dialog.ConfirmRegistrationLock ?: return

    _state.update { it.copy(dialog = dialog.copy(inProgress = true)) }
    val success = accountRepository.setRegistrationLockEnabled(dialog.enable)
    _state.update { it.copy(dialog = Dialog.None) }
    refresh()

    if (!success) {
      _actions.send(
        if (dialog.enable) {
          AccountSettingsAction.ShowRegistrationLockEnableFailed
        } else {
          AccountSettingsAction.ShowRegistrationLockDisableFailed
        }
      )
    }
  }

  private suspend fun applyAddTotpAppClicked() {
    val signalLogin = _state.value.signalLogin
    when {
      signalLogin?.atMaxTotpApps == true -> _state.update { it.copy(dialog = Dialog.MaxTotpAppsReached) }
      signalLogin?.atMaxTwoFactorMethods == true -> _state.update { it.copy(dialog = Dialog.MaxTwoFactorMethodsReached) }
      else -> _actions.send(AccountSettingsAction.NavigateToTotpSetup)
    }
  }

  /**
   * Starts the ceremony, but we need to show an activity, so we send out an action for the UI to do the next step and send back the results as an event.
   */
  private suspend fun applyAddPasskeyClicked() {
    val signalLogin = _state.value.signalLogin
    if (signalLogin?.atMaxTwoFactorMethods == true) {
      _state.update { it.copy(dialog = Dialog.MaxTwoFactorMethodsReached) }
      return
    }

    _state.update { it.copy(dialog = Dialog.PasskeyInProgress) }

    when (val result = accountRepository.startPasskeyRegistration()) {
      is StartPasskeyRegistrationResult.Success -> {
        _actions.send(AccountSettingsAction.CreatePasskey(result.parameters))
      }
      StartPasskeyRegistrationResult.TooManyMethods -> {
        _state.update { it.copy(dialog = Dialog.MaxTwoFactorMethodsReached) }
      }
      StartPasskeyRegistrationResult.NetworkFailure -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(AccountSettingsAction.ShowPasskeyCreationFailed)
      }
    }
  }

  private suspend fun applyPasskeyCeremonyCompleted(result: PasskeyCreationResult) {
    when (result) {
      is PasskeyCreationResult.Success -> {
        registerNewPasskey(result)
      }
      PasskeyCreationResult.UserCanceled -> {
        _state.update { it.copy(dialog = Dialog.None) }
        Log.i(TAG, "The user backed out of the passkey provider's sheet.")
      }
      PasskeyCreationResult.NoProviderAvailable -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(AccountSettingsAction.ShowNoPasskeyProvider)
      }
      PasskeyCreationResult.CeremonyFailed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(AccountSettingsAction.ShowPasskeyCreationFailed)
      }
    }
  }

  /** Hands the completed ceremony to the service, which is what actually puts the passkey on the account. */
  private suspend fun registerNewPasskey(ceremony: PasskeyCreationResult.Success) {
    val result = accountRepository.finishPasskeyRegistration(
      attestationObject = ceremony.attestationObject,
      collectedClientDataJson = ceremony.collectedClientDataJson
    )

    when (result) {
      is FinishPasskeyRegistrationResult.Success -> {
        _state.update { it.copy(dialog = Dialog.None) }
        refreshTwoFactorMethods()
        _actions.send(
          AccountSettingsAction.NavigateToNameNewPasskey(
            TwoFactorMethod(id = result.passkeyId, kind = TwoFactorMethod.Kind.PASSKEY, name = null, createdAt = result.createdAt)
          )
        )
      }
      FinishPasskeyRegistrationResult.TooManyMethods -> {
        _state.update { it.copy(dialog = Dialog.MaxTwoFactorMethodsReached) }
      }
      FinishPasskeyRegistrationResult.CeremonyRejected,
      FinishPasskeyRegistrationResult.NetworkFailure -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(AccountSettingsAction.ShowPasskeyCreationFailed)
      }
    }
  }

  private fun applyMethodRemovalAuthenticated(method: TwoFactorMethod) {
    _state.update { it.copy(dialog = Dialog.ConfirmRemoveMethod(method)) }
  }

  /** A method the service has already forgotten counts as removed, since that's the outcome the user asked for. */
  private suspend fun applyRemoveMethodConfirmed(method: TwoFactorMethod) {
    _state.update { it.copy(dialog = Dialog.None) }

    when (val result = twoFactorMethodService.removeMethod(method.id)) {
      is RequestResult.Success -> {
        _actions.send(AccountSettingsAction.ShowMethodRemoved(method.kind))
        refreshTwoFactorMethods()
      }
      is RequestResult.RetryableNetworkError, is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't remove the second factor. Leaving it in the list, where it still is.")
        _actions.send(AccountSettingsAction.ShowMethodRemovalFailed(method.kind))
      }
      is RequestResult.NonSuccess -> error("Code branch is unreachable")
    }
  }

  private suspend fun refresh() {
    val isPhoneNumberless = accountRepository.isPhoneNumberless()

    _state.update {
      it.copy(
        hasPin = accountRepository.hasPin(),
        hasRestoredAep = accountRepository.hasRestoredAep(),
        pinRemindersEnabled = accountRepository.arePinRemindersEnabled(),
        registrationLockEnabled = accountRepository.isRegistrationLockEnabled(),
        userUnregistered = accountRepository.isUserUnregistered(),
        clientDeprecated = accountRepository.isClientDeprecated(),
        isPhoneNumberless = isPhoneNumberless,
        // Held onto across refreshes so a resume doesn't drop the list back to its loading state.
        signalLogin = if (isPhoneNumberless) it.signalLogin ?: SignalLogin(maxTotpApps = accountRepository.getMaxTotpApps(), maxTwoFactorMethods = accountRepository.getMaxTwoFactorMethods()) else null
      )
    }

    if (isPhoneNumberless) {
      refreshTwoFactorMethods()
    }
  }

  private suspend fun refreshTwoFactorMethods() {
    val (methods, loadState) = when (val result = twoFactorMethodService.getMethods(accountRepository.masterKey())) {
      is RequestResult.Success -> result.result.map { it.toTwoFactorMethod() }.sortedBy { it.kind.sortRank() } to LoadState.LOADED
      is RequestResult.RetryableNetworkError, is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't reach the service to list the account's second factors.")
        emptyList<TwoFactorMethod>() to LoadState.NETWORK_FAILURE
      }
      is RequestResult.NonSuccess -> error("Code branch is unreachable")
    }

    _state.update { state ->
      val signalLogin = state.signalLogin ?: return@update state
      state.copy(signalLogin = signalLogin.copy(twoFactorMethods = methods, loadState = loadState))
    }
  }

  /**
   * A kind we don't recognize still gets a row, since the user needs to be able to see and remove a second factor
   * whether or not we can make sense of it.
   */
  private fun ServiceTwoFactorMethod.toTwoFactorMethod(): TwoFactorMethod {
    return TwoFactorMethod(
      id = id,
      kind = when (kind) {
        MfaKeyKind.TOTP -> TwoFactorMethod.Kind.AUTHENTICATOR_APP
        MfaKeyKind.WEB_AUTHN -> TwoFactorMethod.Kind.PASSKEY
        MfaKeyKind.UNKNOWN -> TwoFactorMethod.Kind.OTHER
      },
      name = name,
      createdAt = createdAt?.toEpochMilli()
    )
  }

  /** The settings list leads with authenticator apps, whatever order the service reports them in. */
  private fun TwoFactorMethod.Kind.sortRank(): Int {
    return when (this) {
      TwoFactorMethod.Kind.AUTHENTICATOR_APP -> 0
      TwoFactorMethod.Kind.PASSKEY -> 1
      TwoFactorMethod.Kind.OTHER -> 2
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
