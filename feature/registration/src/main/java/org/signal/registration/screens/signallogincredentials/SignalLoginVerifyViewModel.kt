/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [SignalLoginCredentialEntryScreen] in [SignalLoginCredentialEntryState.Mode.Verify], where a user who is
 * already logged in types their Signal Login in to prove they still know it.
 *
 * Like confirming a freshly-saved login, nothing here talks to the service: the account's ID and recovery key are
 * already on the device, so verifying is a local comparison. That keeps it quick enough to finish with a single tap
 * through the password manager.
 */
class SignalLoginVerifyViewModel(
  private val expectedAci: ACI,
  private val expectedAep: AccountEntropyPool
) : EventDrivenViewModel<SignalLoginCredentialEntryScreenEvents>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(SignalLoginVerifyViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginCredentialEntryState(mode = SignalLoginCredentialEntryState.Mode.Verify))
  val state: StateFlow<SignalLoginCredentialEntryState> = _state.asStateFlow()

  private val _actions = Channel<SignalLoginVerifyActions>(Channel.BUFFERED)
  val actions: Flow<SignalLoginVerifyActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginCredentialEntryScreenEvents) {
    applyEvent(_state.value, event, { _actions.send(it) }) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: SignalLoginCredentialEntryState,
    event: SignalLoginCredentialEntryScreenEvents,
    actionEmitter: suspend (SignalLoginVerifyActions) -> Unit,
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
        applyPasswordManagerCredentialSelected(state, event, actionEmitter, stateEmitter)
      }

      is SignalLoginCredentialEntryScreenEvents.BackClicked -> {
        actionEmitter(SignalLoginVerifyActions.Close)
      }

      is SignalLoginCredentialEntryScreenEvents.NeedHelpClicked -> {
        actionEmitter(SignalLoginVerifyActions.OpenNeedHelpArticle)
      }

      is SignalLoginCredentialEntryScreenEvents.NextClicked -> {
        applyNextClicked(state, actionEmitter, stateEmitter)
      }

      is SignalLoginCredentialEntryScreenEvents.ShowLoginInfoAgainClicked -> {
        error("There is no 'show login info again' button in ${SignalLoginCredentialEntryState.Mode.Verify} mode, so this event can't happen.")
      }

      is SignalLoginCredentialEntryScreenEvents.TwoFactorCodeEntered,
      is SignalLoginCredentialEntryScreenEvents.PasskeyAssertionReceived -> {
        error("Verifying a login never talks to the service, so it can never ask for a second factor.")
      }
    }
  }

  /**
   * Fills both fields from the password manager, then checks the pair straight away if what came back is complete.
   */
  private suspend fun applyPasswordManagerCredentialSelected(
    state: SignalLoginCredentialEntryState,
    event: SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected,
    actionEmitter: suspend (SignalLoginVerifyActions) -> Unit,
    stateEmitter: (SignalLoginCredentialEntryState) -> Unit
  ) {
    val filledState = SignalLoginCredentialEntryScreenEventHandler.applyPasswordManagerCredential(state, event)

    stateEmitter(filledState)

    if (filledState.isNextEnabled) {
      Log.i(TAG, "[CredentialSelected] The password manager supplied a complete login. Checking it.")
      applyNextClicked(filledState, actionEmitter, stateEmitter)
    } else {
      Log.w(TAG, "[CredentialSelected] The password manager supplied a login we can't check as-is. Leaving it in the fields for the user to fix.")
    }
  }

  /**
   * Compares what the user typed against the account's login. A mismatch is flagged on both fields rather than one,
   * since either half could be the one they got wrong.
   */
  private suspend fun applyNextClicked(
    state: SignalLoginCredentialEntryState,
    actionEmitter: suspend (SignalLoginVerifyActions) -> Unit,
    stateEmitter: (SignalLoginCredentialEntryState) -> Unit
  ) {
    val matches = SignalLoginCredentialEntryScreenEventHandler.matchesLogin(state, expectedAci, expectedAep)

    if (matches) {
      Log.i(TAG, "[Next] The user typed in the login that matches their account.")
      actionEmitter(SignalLoginVerifyActions.Verified)
    } else {
      Log.w(TAG, "[Next] What the user typed doesn't match their account's login.")
      stateEmitter(state.copy(areCredentialsIncorrect = true))
    }
  }
}
