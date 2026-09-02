/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.RestoreDecision
import org.signal.registration.screens.aepentry.AepInput
import org.signal.registration.screens.shared.AccountIdError
import org.signal.registration.screens.shared.AccountIdFormat
import org.signal.registration.screens.twofactorselection.TwoFactorMethod
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

/**
 * View model for [SignalLoginCredentialEntryScreen], where a user who already owns a Signal Login types in both halves
 * of it and gets logged back in.
 */
class SignalLoginCredentialEntryViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  prefilledAccountId: String? = null
) : EventDrivenViewModel<SignalLoginCredentialEntryScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginCredentialEntryViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginCredentialEntryState(accountId = prefilledAccountId ?: ""))
  val state: StateFlow<SignalLoginCredentialEntryState> = _state.asStateFlow()

  private val _actions = Channel<SignalLoginCredentialEntryScreenActions>(Channel.BUFFERED)
  val actions: Flow<SignalLoginCredentialEntryScreenActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginCredentialEntryScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: SignalLoginCredentialEntryState,
    event: SignalLoginCredentialEntryScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginCredentialEntryState) -> Unit
  ) {
    when (event) {
      is SignalLoginCredentialEntryScreenEvents.BackClicked -> {
        parentEventEmitter.navigateBack()
      }

      is SignalLoginCredentialEntryScreenEvents.AccountIdChanged -> {
        val accountId = AccountIdFormat.normalize(event.value)
        stateEmitter(state.copy(accountId = accountId, accountIdError = AccountIdFormat.validate(accountId), areCredentialsIncorrect = false))
      }

      is SignalLoginCredentialEntryScreenEvents.RecoveryKeyChanged -> {
        stateEmitter(state.copy(recoveryKey = AepInput.from(event.value, state.recoveryKey.error), areCredentialsIncorrect = false))
      }

      is SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected -> {
        applyPasswordManagerCredentialSelected(state, event, parentEventEmitter, stateEmitter)
      }

      is SignalLoginCredentialEntryScreenEvents.RecoveryKeyVisibilityToggled -> {
        stateEmitter(state.copy(isRecoveryKeyRevealed = !state.isRecoveryKeyRevealed))
      }

      is SignalLoginCredentialEntryScreenEvents.NeedHelpClicked -> {
        _actions.trySend(SignalLoginCredentialEntryScreenActions.OpenNeedHelpArticle)
      }

      is SignalLoginCredentialEntryScreenEvents.DismissError -> {
        stateEmitter(state.copy(loginError = null))
      }

      is SignalLoginCredentialEntryScreenEvents.NextClicked -> {
        applyNextClicked(state, totp = null, parentEventEmitter, stateEmitter)
      }

      is SignalLoginCredentialEntryScreenEvents.TwoFactorCodeEntered -> {
        if (state.isNextEnabled) {
          applyNextClicked(state, totp = event.code.toIntOrNull(), parentEventEmitter, stateEmitter)
        } else {
          Log.w(TAG, "[TwoFactorCodeEntered] Got a two-factor code, but the login on screen is no longer submittable. Leaving the user on the credential screen to re-enter it.")
        }
      }
    }
  }

  /**
   * Fills both halves of the login with what the password manager handed back, then submits it right away if the pair
   * is complete so the user doesn't have to tap the next button themselves.
   */
  private suspend fun applyPasswordManagerCredentialSelected(
    state: SignalLoginCredentialEntryState,
    event: SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginCredentialEntryState) -> Unit
  ) {
    val accountId = AccountIdFormat.normalize(event.accountId)
    val filledState = state.copy(
      accountId = accountId,
      accountIdError = AccountIdFormat.validate(accountId),
      recoveryKey = AepInput.from(event.recoveryKey),
      areCredentialsIncorrect = false
    )

    stateEmitter(filledState)

    if (filledState.isNextEnabled) {
      Log.i(TAG, "[CredentialSelected] The password manager supplied a complete login. Submitting it.")
      applyNextClicked(filledState, totp = null, parentEventEmitter, stateEmitter)
    } else {
      Log.w(TAG, "[CredentialSelected] The password manager supplied a login we can't submit as-is. Leaving it in the fields for the user to fix.")
    }
  }

  /**
   * Submits the login currently on screen. Callers are expected to have checked [SignalLoginCredentialEntryState.isNextEnabled]
   * first, but the incomplete cases are handled rather than asserted: a two-factor code can arrive from the TOTP screen
   * long after this view model was recreated with empty fields (e.g. after process death), so an incomplete login here is
   * something to log and drop, not a crash.
   */
  private suspend fun applyNextClicked(
    state: SignalLoginCredentialEntryState,
    totp: Int?,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginCredentialEntryState) -> Unit
  ) {
    val aci = AccountIdFormat.toAciOrNull(state.accountId)
    if (aci == null) {
      Log.w(TAG, "[Next] The entered account ID isn't a valid ACI.")
      stateEmitter(state.copy(accountIdError = AccountIdError.Invalid))
      return
    }

    if (!state.recoveryKey.isValid) {
      Log.w(TAG, "[Next] The recovery key on screen isn't complete, so there is nothing to submit.")
      return
    }

    val aep = AccountEntropyPool(state.recoveryKey.normalized)

    stateEmitter(state.copy(isLoggingIn = true))
    parentEventEmitter(RegistrationFlowEvent.UserSuppliedAepSubmitted(aep))

    Log.i(TAG, "[Next] Attempting to log in to ${aci.logString()} with the RRP derived from the entered recovery key. totp: ${totp != null}")

    attemptToLogIn(state, aci, aep, totp, provideRegistrationLock = false, parentEventEmitter, stateEmitter)
  }

  private suspend fun attemptToLogIn(
    inputState: SignalLoginCredentialEntryState,
    aci: ACI,
    aep: AccountEntropyPool,
    totp: Int?,
    provideRegistrationLock: Boolean,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (SignalLoginCredentialEntryState) -> Unit
  ) {
    val masterKey = aep.deriveMasterKey()
    val recoveryPassword = masterKey.deriveRegistrationRecoveryPassword()
    val registrationLock = masterKey.deriveRegistrationLock().takeIf { provideRegistrationLock }

    val result = repository.reRegisterAccountWithoutPhoneNumber(
      aci = aci,
      recoveryPassword = recoveryPassword,
      aep = aep,
      registrationLock = registrationLock,
      totp = totp
    )

    when (result) {
      is RequestResult.Success -> {
        Log.i(TAG, "[Next] Successfully logged back in without a phone number.")
        val (response, keyMaterial, registeredAci) = result.result

        parentEventEmitter(RegistrationFlowEvent.Registered(registeredAci, keyMaterial.accountEntropyPool, response.storageCapable, phoneNumberless = response.e164 == null))

        if (response.reregistration) {
          val hasRemoteBackup = hasRemoteBackup(aep)

          Log.i(TAG, "[Next] Reclaimed an existing account. Letting the user choose how to restore it. hasRemoteBackup: $hasRemoteBackup")
          stateEmitter(inputState.copy(isLoggingIn = false))
          parentEventEmitter.navigateTo(RegistrationRoute.ArchiveRestoreSelection.forPostRegisterWithKnownAep(aep, hasRemoteBackup))
        } else {
          Log.i(TAG, "[Next] The service reports a brand new account, so there is nothing to restore. Finishing up.")
          repository.setRestoreDecision(RestoreDecision.NEW_ACCOUNT)
          repository.restoreAccountRecord()
          stateEmitter(inputState.copy(isLoggingIn = false))
          parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
        }
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[Next] RRP incorrect. Either the account ID or the recovery key is wrong. Message: ${error.message}")
            stateEmitter(inputState.copy(isLoggingIn = false, areCredentialsIncorrect = true))
          }
          is RegisterAccountError.RegistrationLock -> {
            if (provideRegistrationLock) {
              Log.w(TAG, "[Next] Still registration locked after providing the reglock token derived from the recovery key. Falling back to PIN entry.")
              stateEmitter(inputState.copy(isLoggingIn = false))
              parentEventEmitter.navigateTo(
                RegistrationRoute.PinEntryForRegistrationLock(
                  timeRemaining = error.data.timeRemaining,
                  svrCredentials = error.data.svr2Credentials
                )
              )
            } else {
              Log.w(TAG, "[Next] Registration locked. Retrying with the reglock token derived from the recovery key.")
              attemptToLogIn(inputState, aci, aep, totp, provideRegistrationLock = true, parentEventEmitter, stateEmitter)
            }
          }
          is RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[Next] Rate limited (retryAfter: ${error.retryAfter}).")
            stateEmitter(inputState.copy(isLoggingIn = false, loginError = SignalLoginError.RateLimited))
          }
          is RegisterAccountError.SessionNotFoundOrNotVerified -> {
            error("[Next] Session not found or not verified. This should not happen with RRP-based registration.")
          }
          is RegisterAccountError.DeviceTransferPossible -> {
            error("[Next] Device transfer possible. This should not happen with RRP-based registration.")
          }
          RegisterAccountError.TotpMissingOrIncorrect -> {
            // For now this error only means TOTP, but in the future it will indicate that some two-factor method is
            // required, so we treat it generically and route through the method selection screen.
            Log.w(TAG, "[Next] A two-factor code is required. Sending the user to two-factor method selection.")
            stateEmitter(inputState.copy(isLoggingIn = false))
            parentEventEmitter.navigateTo(RegistrationRoute.TwoFactorSelection(methods = listOf(TwoFactorMethod.AuthenticatorApp)))
          }
          is RegisterAccountError.InvalidRequest,
          is RegisterAccountError.InvalidReceiptCredentialPresentation,
          RegisterAccountError.PostQuantumRatchetRequired -> {
            Log.w(TAG, "[Next] Unexpected registration error: $error")
            stateEmitter(inputState.copy(isLoggingIn = false, loginError = SignalLoginError.UnknownError))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[Next] Network error.", result.networkError)
        stateEmitter(inputState.copy(isLoggingIn = false, loginError = SignalLoginError.NetworkError))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[Next] Application error.", result.cause)
        stateEmitter(inputState.copy(isLoggingIn = false, loginError = SignalLoginError.UnknownError))
      }
    }
  }

  private suspend fun hasRemoteBackup(aep: AccountEntropyPool): Boolean {
    val result = repository.getAndMaybeHealRemoteBackupInfo(aep)

    return when {
      result is RequestResult.Success -> true
      result is RequestResult.NonSuccess && result.error is NetworkController.GetBackupInfoError.NoBackup -> false
      else -> {
        Log.w(TAG, "[hasRemoteBackup] Could not determine whether a remote backup exists ($result). Offering it anyway.")
        true
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val prefilledAccountId: String? = null
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SignalLoginCredentialEntryViewModel(repository, parentEventEmitter, prefilledAccountId) as T
    }
  }
}
