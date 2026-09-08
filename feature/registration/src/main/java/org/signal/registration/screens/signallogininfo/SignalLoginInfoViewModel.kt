/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

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
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.passwordmanager.CredentialManagerError
import org.signal.passwordmanager.CredentialManagerResult
import org.signal.passwordmanager.UsernamePasswordCredential
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.shared.AccountIdFormat
import org.signal.registration.screens.util.navigateTo

/**
 * View model for [SignalLoginInfoScreen].
 */
class SignalLoginInfoViewModel(
  private val repository: RegistrationRepository,
  parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  isPasswordManagerAvailable: Boolean
) : EventDrivenViewModel<SignalLoginInfoScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginInfoViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginInfoState(isPasswordManagerAvailable = isPasswordManagerAvailable))
  val state: StateFlow<SignalLoginInfoState> = _state.asStateFlow()

  private val _actions = Channel<SignalLoginInfoScreenActions>(Channel.BUFFERED)
  val actions: Flow<SignalLoginInfoScreenActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    parentState
      .onEach { onEvent(SignalLoginInfoScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginInfoScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: SignalLoginInfoState,
    event: SignalLoginInfoScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginInfoState) -> Unit
  ) {
    when (event) {
      is SignalLoginInfoScreenEvents.ParentStateChanged -> {
        stateEmitter(state.copy(aci = event.parentState.aci, aep = event.parentState.accountEntropyPool))
      }

      is SignalLoginInfoScreenEvents.ViewDetailsClicked -> {
        parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginViewDetails)
      }

      is SignalLoginInfoScreenEvents.SaveToPasswordManagerClicked -> {
        applySaveToPasswordManagerClicked(state, isRetry = false, stateEmitter)
      }

      is SignalLoginInfoScreenEvents.SaveToPasswordManagerCompleted -> {
        applySaveToPasswordManagerCompleted(state, event.result, stateEmitter)
      }

      is SignalLoginInfoScreenEvents.ConfirmSavedContinueClicked -> {
        applyConfirmSavedContinueClicked(state, stateEmitter)
      }

      is SignalLoginInfoScreenEvents.SeeLoginInfoAgainClicked -> {
        stateEmitter(state.copy(showConfirmSavedSheet = false))
        parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginViewDetails)
      }

      is SignalLoginInfoScreenEvents.ConfirmSavedSheetDismissed -> {
        stateEmitter(state.copy(showConfirmSavedSheet = false))
      }

      is SignalLoginInfoScreenEvents.SavedCredentialRetrieved -> {
        applySavedCredentialRetrieved(state, event.credential, parentEventEmitter, stateEmitter)
      }

      is SignalLoginInfoScreenEvents.SaveManuallyClicked -> {
        parentEventEmitter.navigateTo(RegistrationRoute.SignalLoginViewDetailsForManualSave)
      }

      is SignalLoginInfoScreenEvents.SaveFailedDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(saveFailed = false)))
      }

      is SignalLoginInfoScreenEvents.SaveNotConfirmedDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(saveNotConfirmed = false)))
      }

      is SignalLoginInfoScreenEvents.UnknownErrorDialogDismissed -> {
        stateEmitter(state.copy(dialogs = state.dialogs.copy(unknownError = false)))
      }
    }
  }

  /**
   * Hands the login off to the password manager. Every dialog is cleared on the way out, since this is also how the
   * user retries after a save that didn't take.
   */
  private fun applySaveToPasswordManagerClicked(
    state: SignalLoginInfoState,
    isRetry: Boolean,
    stateEmitter: (SignalLoginInfoState) -> Unit
  ) {
    val accountId = state.passwordManagerAccountId
    val recoveryKey = state.passwordManagerRecoveryKey

    if (accountId == null || recoveryKey == null) {
      Log.w(TAG, "[SaveToPasswordManager] There is no login in the flow state to save.")
      stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(unknownError = true)))
      return
    }

    stateEmitter(state.copy(showSpinner = true, didRetrySave = isRetry, dialogs = SignalLoginInfoState.Dialogs()))
    _actions.trySend(SignalLoginInfoScreenActions.SaveToPasswordManager(accountId = accountId, recoveryKey = recoveryKey))
  }

  private fun applySaveToPasswordManagerCompleted(
    state: SignalLoginInfoState,
    result: CredentialManagerResult,
    stateEmitter: (SignalLoginInfoState) -> Unit
  ) {
    when (result) {
      is CredentialManagerResult.Success -> {
        Log.i(TAG, "[SaveCompleted] The password manager took the login. Asking the user to confirm it's really in there.")
        stateEmitter(state.copy(showSpinner = false, showConfirmSavedSheet = true))
      }

      is CredentialManagerResult.UserCanceled -> {
        Log.i(TAG, "[SaveCompleted] The user backed out of the password manager.")
        stateEmitter(state.copy(showSpinner = false))
      }

      is CredentialManagerResult.Interrupted -> {
        if (state.didRetrySave) {
          Log.w(TAG, "[SaveCompleted] Interrupted again after a retry. Telling the user to save it themselves.", result.exception)
          stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(saveFailed = true)))
        } else {
          Log.i(TAG, "[SaveCompleted] Interrupted. Trying once more.", result.exception)
          applySaveToPasswordManagerClicked(state, isRetry = true, stateEmitter)
        }
      }

      is CredentialManagerError.MissingCredentialManager -> {
        Log.w(TAG, "[SaveCompleted] No password manager is configured.", result.exception)
        stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(saveFailed = true)))
      }

      is CredentialManagerError.SavePromptDisabled -> {
        Log.w(TAG, "[SaveCompleted] The user has turned off the password manager's save prompt.", result.exception)
        stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(saveFailed = true)))
      }

      is CredentialManagerError.Unexpected -> {
        Log.w(TAG, "[SaveCompleted] Unexpected error saving the login to the password manager.", result.exception)
        stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(unknownError = true)))
      }
    }
  }

  private fun applyConfirmSavedContinueClicked(
    state: SignalLoginInfoState,
    stateEmitter: (SignalLoginInfoState) -> Unit
  ) {
    val accountId = state.passwordManagerAccountId

    if (accountId == null) {
      Log.w(TAG, "[ConfirmSaved] There is no login in the flow state to read back.")
      stateEmitter(state.copy(showConfirmSavedSheet = false, showSpinner = false, dialogs = state.dialogs.copy(unknownError = true)))
      return
    }

    stateEmitter(state.copy(showConfirmSavedSheet = false, showSpinner = true))
    _actions.trySend(SignalLoginInfoScreenActions.ReadBackFromPasswordManager(accountId = accountId))
  }

  /**
   * Checks what the password manager handed back against the login the user was given. A mismatch means the save
   * didn't take, so the user is offered the save again rather than being sent on with nothing recorded.
   */
  private fun applySavedCredentialRetrieved(
    state: SignalLoginInfoState,
    credential: UsernamePasswordCredential?,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginInfoState) -> Unit
  ) {
    val expectedAci = state.aci
    val expectedAep = state.aep

    if (expectedAci == null || expectedAep == null) {
      Log.w(TAG, "[CredentialRetrieved] There is no login in the flow state to compare against.")
      stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(unknownError = true)))
      return
    }

    if (credential == null) {
      Log.w(TAG, "[CredentialRetrieved] The password manager had nothing to hand back.")
      stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(saveNotConfirmed = true)))
      return
    }

    val storedAci = AccountIdFormat.toAciOrNull(AccountIdFormat.normalize(credential.username))
    val storedAep = AccountEntropyPool.parseOrNull(credential.password)

    if (storedAci == expectedAci && storedAep?.value == expectedAep.value) {
      Log.i(TAG, "[CredentialRetrieved] The password manager has the login the user was given.")
      stateEmitter(state.copy(showSpinner = false))
      parentEventEmitter.navigateTo(RegistrationRoute.AddUsername)
    } else {
      Log.w(TAG, "[CredentialRetrieved] What the password manager has doesn't match the login the user was given.")
      stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(saveNotConfirmed = true)))
    }
  }
}
