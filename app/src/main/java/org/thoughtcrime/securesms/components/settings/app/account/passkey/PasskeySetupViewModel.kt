/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.passkey

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.passkeysetup.PasskeySetupAction
import org.signal.appsettings.passkeysetup.PasskeySetupEvent
import org.signal.appsettings.passkeysetup.PasskeySetupState
import org.signal.appsettings.passkeysetup.PasskeySetupState.Dialog
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.passwordmanager.PasskeyCreationResult
import org.thoughtcrime.securesms.components.settings.app.account.passkey.PasskeySetupRepository.FinishPasskeyRegistrationResult
import org.thoughtcrime.securesms.components.settings.app.account.passkey.PasskeySetupRepository.StartPasskeyRegistrationResult

/**
 * Drives the screen that explains passkeys and runs the ceremony that adds one to the account.
 */
class PasskeySetupViewModel(
  private val repository: PasskeySetupRepository
) : EventDrivenViewModel<PasskeySetupEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(PasskeySetupViewModel::class)

    private const val SUPPORT_URL = "https://support.signal.org/hc/articles/11228705649690"
  }

  private val _state = MutableStateFlow(PasskeySetupState())
  private val _actions = Channel<PasskeySetupAction>(Channel.BUFFERED)

  val state: StateFlow<PasskeySetupState> = _state.asStateFlow()
  val actions: Flow<PasskeySetupAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: PasskeySetupEvent) {
    when (event) {
      PasskeySetupEvent.NavigateBackClicked -> {
        _actions.send(PasskeySetupAction.NavigateBack)
      }
      PasskeySetupEvent.LearnMoreClicked -> {
        _actions.send(PasskeySetupAction.OpenSupportArticle(SUPPORT_URL))
      }
      PasskeySetupEvent.SetUpPasskeyClicked -> {
        applySetUpPasskeyClicked()
      }
      is PasskeySetupEvent.PasskeyCeremonyCompleted -> {
        applyPasskeyCeremonyCompleted(event.result)
      }
      PasskeySetupEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
    }
  }

  /**
   * Starts the ceremony, but we need to show an activity, so we send out an action for the UI to do the next step and
   * send back the results as an event.
   */
  private suspend fun applySetUpPasskeyClicked() {
    _state.update { it.copy(dialog = Dialog.InProgress) }

    when (val result = repository.startPasskeyRegistration()) {
      is StartPasskeyRegistrationResult.Success -> {
        _actions.send(PasskeySetupAction.CreatePasskey(result.parameters))
      }
      StartPasskeyRegistrationResult.TooManyMethods -> {
        showMaxTwoFactorMethodsReached()
      }
      StartPasskeyRegistrationResult.NetworkFailure -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(PasskeySetupAction.ShowPasskeyCreationFailed)
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
        _actions.send(PasskeySetupAction.ShowNoPasskeyProvider)
      }
      PasskeyCreationResult.CeremonyFailed -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(PasskeySetupAction.ShowPasskeyCreationFailed)
      }
    }
  }

  /** Hands the completed ceremony to the service, which is what actually puts the passkey on the account. */
  private suspend fun registerNewPasskey(ceremony: PasskeyCreationResult.Success) {
    val result = repository.finishPasskeyRegistration(
      attestationObject = ceremony.attestationObject,
      collectedClientDataJson = ceremony.collectedClientDataJson
    )

    when (result) {
      is FinishPasskeyRegistrationResult.Success -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(
          PasskeySetupAction.NavigateToNameNewPasskey(
            TwoFactorMethod(id = result.passkeyId, kind = TwoFactorMethod.Kind.PASSKEY, name = null, createdAt = result.createdAt)
          )
        )
      }
      FinishPasskeyRegistrationResult.TooManyMethods -> {
        showMaxTwoFactorMethodsReached()
      }
      FinishPasskeyRegistrationResult.CeremonyRejected,
      FinishPasskeyRegistrationResult.NetworkFailure -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(PasskeySetupAction.ShowPasskeyCreationFailed)
      }
    }
  }

  private fun showMaxTwoFactorMethodsReached() {
    _state.update { it.copy(dialog = Dialog.MaxTwoFactorMethodsReached(repository.getMaxTwoFactorMethods())) }
  }
}
