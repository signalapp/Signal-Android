/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor.verify

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.twofactorverify.TwoFactorVerifyAction
import org.signal.appsettings.twofactorverify.TwoFactorVerifyEvent
import org.signal.appsettings.twofactorverify.TwoFactorVerifyMethod
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.CodeError
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.Dialog
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.Step
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.passwordmanager.PasskeyAssertionResult
import org.signal.uicomponents.codeentryfield.CodeEntryFieldAction
import org.signal.uicomponents.codeentryfield.CodeEntryFieldEvents
import org.signal.uicomponents.codeentryfield.CodeEntryFieldPresenter
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.verify.TwoFactorVerifyRepository.StartResult
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.verify.TwoFactorVerifyRepository.VerifyResult

/**
 * Drives the flow that verifies one of the user's two-factor methods in answer to the reminder megaphone.
 *
 * @param arePasskeysSupported Whether this device can run a passkey ceremony at all. When it can't, an account's
 *   passkeys are left out of what's offered, the same as during registration.
 */
class TwoFactorVerifyViewModel(
  private val arePasskeysSupported: Boolean,
  private val repository: TwoFactorVerifyRepository = TwoFactorVerifyRepository()
) : EventDrivenViewModel<TwoFactorVerifyEvent>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(TwoFactorVerifyViewModel::class)
  }

  private val _state = MutableStateFlow(TwoFactorVerifyState())
  private val _actions = Channel<TwoFactorVerifyAction>(Channel.BUFFERED)

  val state: StateFlow<TwoFactorVerifyState> = _state.asStateFlow()
  val actions: Flow<TwoFactorVerifyAction> = _actions.receiveAsFlow()

  private val codeEntryPresenter = CodeEntryFieldPresenter(viewModelScope)

  init {
    codeEntryPresenter
      .state
      .onEach { onEvent(TwoFactorVerifyEvent.CodeEntryStateChanged(it)) }
      .launchIn(viewModelScope)

    codeEntryPresenter
      .actions
      .onEach { onEvent(TwoFactorVerifyEvent.CodeEntryAction(it)) }
      .launchIn(viewModelScope)

    viewModelScope.launch { loadMethods() }
  }

  override suspend fun processEvent(event: TwoFactorVerifyEvent) {
    when (event) {
      TwoFactorVerifyEvent.PasskeySelected -> {
        applyPasskeySelected()
      }
      TwoFactorVerifyEvent.AuthenticatorAppSelected -> {
        _state.update { it.copy(step = Step.EnterCode(canReturnToMethodChoice = true)) }
      }
      is TwoFactorVerifyEvent.CodeEntryEvent -> {
        val codeEntryEvent = event.event
        if (codeEntryEvent is CodeEntryFieldEvents.CodeChanged && codeEntryEvent.code.isNotEmpty()) {
          _state.update { it.copy(codeError = CodeError.None) }
        }
        codeEntryPresenter.onEvent(codeEntryEvent)
      }
      is TwoFactorVerifyEvent.CodeEntryStateChanged -> {
        _state.update { it.copy(codeEntry = event.codeEntryState) }
      }
      is TwoFactorVerifyEvent.CodeEntryAction -> {
        when (val action = event.action) {
          is CodeEntryFieldAction.CodeEntered -> {
            verifyCode(action.code)
          }
        }
      }
      is TwoFactorVerifyEvent.PasskeyCeremonyCompleted -> {
        applyPasskeyCeremonyCompleted(event.result)
      }
      TwoFactorVerifyEvent.CancelClicked -> {
        _actions.send(TwoFactorVerifyAction.Close)
      }
      TwoFactorVerifyEvent.BackClicked -> {
        applyBackClicked()
      }
      TwoFactorVerifyEvent.DialogDismissed -> {
        applyDialogDismissed()
      }
    }
  }

  /**
   * Finds out what the account has and picks the first screen accordingly. An account with nothing but authenticator
   * apps has no choice to make, so it goes straight to code entry.
   */
  private suspend fun loadMethods() {
    when (val result = repository.startVerification()) {
      is StartResult.Success -> {
        val passkeyOffered = arePasskeysSupported && result.passkey != null

        when {
          passkeyOffered -> {
            _state.update { it.copy(step = Step.ChooseMethod(hasAuthenticatorApp = result.hasAuthenticatorApp)) }
          }
          result.hasAuthenticatorApp -> {
            _state.update { it.copy(step = Step.EnterCode(canReturnToMethodChoice = false)) }
          }
          result.passkey != null -> {
            Log.w(TAG, "The account only has methods this device can't verify. passkeysSupported: $arePasskeysSupported")
            _actions.send(TwoFactorVerifyAction.Close)
          }
          else -> {
            Log.w(TAG, "The account has no two-factor methods, so there is nothing to verify.")
            repository.markNoMethods()
            _actions.send(TwoFactorVerifyAction.Close)
          }
        }
      }
      StartResult.NetworkFailure -> {
        _state.update { it.copy(dialog = Dialog.NetworkFailure) }
      }
    }
  }

  /**
   * Fetches a fresh challenge before every ceremony, since the one that came with the method list may have expired
   * while the user sat on the selection screen.
   */
  private suspend fun applyPasskeySelected() {
    _state.update { it.copy(dialog = Dialog.PasskeyInProgress) }

    when (val result = repository.startVerification()) {
      is StartResult.Success -> {
        val passkey = result.passkey
        if (passkey == null) {
          Log.w(TAG, "The account no longer has a passkey to verify.")
          _state.update { it.copy(dialog = Dialog.PasskeyFailed) }
          return
        }

        _actions.send(TwoFactorVerifyAction.AuthenticateWithPasskey(passkey))
      }
      StartResult.NetworkFailure -> {
        _state.update { it.copy(dialog = Dialog.PasskeyFailed) }
      }
    }
  }

  private suspend fun applyPasskeyCeremonyCompleted(result: PasskeyAssertionResult) {
    when (result) {
      is PasskeyAssertionResult.Success -> {
        verifyPasskey(result.responseJson)
      }
      PasskeyAssertionResult.UserCanceled -> {
        Log.i(TAG, "The user backed out of the passkey provider's sheet.")
        _state.update { it.copy(dialog = Dialog.None) }
      }
      PasskeyAssertionResult.NoCredentialAvailable -> {
        Log.w(TAG, "This device holds none of the account's passkeys.")
        _state.update { it.copy(dialog = Dialog.PasskeyFailed) }
      }
      PasskeyAssertionResult.CeremonyFailed -> {
        Log.w(TAG, "The assertion ceremony produced nothing usable.")
        _state.update { it.copy(dialog = Dialog.PasskeyFailed) }
      }
    }
  }

  private suspend fun verifyPasskey(responseJson: String) {
    when (repository.verifyPasskey(responseJson)) {
      VerifyResult.Success -> {
        onVerified(TwoFactorVerifyMethod.PASSKEY)
      }
      VerifyResult.NotVerified -> {
        Log.w(TAG, "The service would not accept the passkey assertion.")
        _state.update { it.copy(dialog = Dialog.PasskeyFailed) }
      }
      VerifyResult.NetworkFailure -> {
        _state.update { it.copy(dialog = Dialog.PasskeyFailed) }
      }
    }
  }

  private suspend fun verifyCode(code: String) {
    _state.update { it.copy(submitting = true, codeError = CodeError.None) }

    when (repository.verifyCode(code)) {
      VerifyResult.Success -> {
        onVerified(TwoFactorVerifyMethod.AUTHENTICATOR_APP)
      }
      VerifyResult.NotVerified -> {
        _state.update { it.copy(submitting = false, codeError = CodeError.IncorrectCode) }
        codeEntryPresenter.onEvent(CodeEntryFieldEvents.Clear)
      }
      VerifyResult.NetworkFailure -> {
        _state.update { it.copy(submitting = false, codeError = CodeError.NetworkFailure) }
      }
    }
  }

  private suspend fun onVerified(method: TwoFactorVerifyMethod) {
    Log.i(TAG, "Verified a two-factor method: $method")
    repository.markVerified()
    _state.update { it.copy(dialog = Dialog.None) }
    _actions.send(TwoFactorVerifyAction.Verified(method))
  }

  private suspend fun applyBackClicked() {
    val step = _state.value.step
    if (step is Step.EnterCode && step.canReturnToMethodChoice) {
      codeEntryPresenter.onEvent(CodeEntryFieldEvents.Clear)
      _state.update { it.copy(step = Step.ChooseMethod(hasAuthenticatorApp = true), codeError = CodeError.None) }
    } else {
      _actions.send(TwoFactorVerifyAction.Close)
    }
  }

  private suspend fun applyDialogDismissed() {
    val dialog = _state.value.dialog
    _state.update { it.copy(dialog = Dialog.None) }

    if (dialog == Dialog.NetworkFailure) {
      _actions.send(TwoFactorVerifyAction.Close)
    }
  }
}
