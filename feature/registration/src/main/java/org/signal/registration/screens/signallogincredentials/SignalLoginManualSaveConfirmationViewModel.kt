/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.aepentry.AepInput
import org.signal.registration.screens.shared.AccountIdFormat
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

/**
 * Drives [SignalLoginCredentialEntryScreen] in [SignalLoginCredentialEntryState.Mode.ConfirmSaved], where the user
 * types back the Signal Login they were just shown to prove they recorded it.
 *
 * Nothing here talks to the service: the account already exists and the credentials are sitting in the parent flow
 * state, so confirming is a local comparison against what the user was handed.
 */
class SignalLoginManualSaveConfirmationViewModel(
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<SignalLoginCredentialEntryScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginManualSaveConfirmationViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginCredentialEntryState(mode = SignalLoginCredentialEntryState.Mode.ConfirmSaved))
  val state: StateFlow<SignalLoginCredentialEntryState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginCredentialEntryScreenEvents) {
    applyEvent(_state.value, event, parentState.value, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  fun applyEvent(
    state: SignalLoginCredentialEntryState,
    event: SignalLoginCredentialEntryScreenEvents,
    parentState: RegistrationFlowState,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginCredentialEntryState) -> Unit
  ) {
    when (event) {
      is SignalLoginCredentialEntryScreenEvents.AccountIdChanged,
      is SignalLoginCredentialEntryScreenEvents.RecoveryKeyChanged,
      is SignalLoginCredentialEntryScreenEvents.RecoveryKeyVisibilityToggled,
      is SignalLoginCredentialEntryScreenEvents.DismissError -> {
        stateEmitter(SignalLoginCredentialEntryScreenEventHandler.applyEvent(state, event))
      }

      is SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected -> {
        applyPasswordManagerCredentialSelected(state, event, parentState, parentEventEmitter, stateEmitter)
      }

      is SignalLoginCredentialEntryScreenEvents.BackClicked,
      is SignalLoginCredentialEntryScreenEvents.ShowLoginInfoAgainClicked -> {
        parentEventEmitter.navigateBack()
      }

      is SignalLoginCredentialEntryScreenEvents.NextClicked -> {
        applyNextClicked(state, parentState, parentEventEmitter, stateEmitter)
      }

      is SignalLoginCredentialEntryScreenEvents.NeedHelpClicked -> {
        error("There is no 'need help' button in ${SignalLoginCredentialEntryState.Mode.ConfirmSaved} mode, so this event can't happen.")
      }

      is SignalLoginCredentialEntryScreenEvents.TwoFactorCodeEntered -> {
        error("Confirming a saved login never talks to the service, so it can never ask for a two-factor code.")
      }
    }
  }

  /**
   * Fills both fields from the password manager, then checks the pair straight away if what came back is complete.
   */
  private fun applyPasswordManagerCredentialSelected(
    state: SignalLoginCredentialEntryState,
    event: SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected,
    parentState: RegistrationFlowState,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginCredentialEntryState) -> Unit
  ) {
    val accountId = AccountIdFormat.normalize(event.accountId).ifEmpty { state.accountId }
    val filledState = state.copy(
      accountId = accountId,
      accountIdError = AccountIdFormat.validate(accountId),
      isAccountIdPrefilled = false,
      recoveryKey = AepInput.from(event.recoveryKey),
      areCredentialsIncorrect = false
    )

    stateEmitter(filledState)

    if (filledState.isNextEnabled) {
      Log.i(TAG, "[CredentialSelected] The password manager supplied a complete login. Checking it.")
      applyNextClicked(filledState, parentState, parentEventEmitter, stateEmitter)
    } else {
      Log.w(TAG, "[CredentialSelected] The password manager supplied a login we can't check as-is. Leaving it in the fields for the user to fix.")
    }
  }

  /**
   * Compares what the user typed against the login they were shown. A mismatch is flagged on both fields rather than
   * one, since either half could be the one they mis-copied.
   */
  private fun applyNextClicked(
    state: SignalLoginCredentialEntryState,
    parentState: RegistrationFlowState,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginCredentialEntryState) -> Unit
  ) {
    val expectedAci = parentState.aci
    val expectedAep = parentState.accountEntropyPool

    if (expectedAci == null || expectedAep == null) {
      Log.w(TAG, "[Next] There is no login in the flow state to confirm against. Sending the user on rather than trapping them here.")
      parentEventEmitter.navigateTo(RegistrationRoute.AddUsername)
      return
    }

    val matches = AccountIdFormat.toAciOrNull(state.accountId) == expectedAci && state.recoveryKey.normalized == expectedAep.value

    if (matches) {
      Log.i(TAG, "[Next] The user typed back the login they were shown.")
      parentEventEmitter.navigateTo(RegistrationRoute.AddUsername)
    } else {
      Log.w(TAG, "[Next] What the user typed doesn't match the login they were shown.")
      stateEmitter(state.copy(areCredentialsIncorrect = true))
    }
  }
}
