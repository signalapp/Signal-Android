/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

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
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

class EnterAepForRemoteBackupPreRegistrationViewModel(
  private val e164: String,
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val resultBus: ResultEventBus,
  private val resultKey: String,
  isPasswordManagerAvailable: Boolean = false
) : EventDrivenViewModel<EnterAepEvents>(TAG, shouldLogEvents = true) {

  companion object {
    private val TAG = Log.tag(EnterAepForRemoteBackupPreRegistrationViewModel::class)
  }

  private val _state = MutableStateFlow(EnterAepState(isPasswordManagerAvailable = isPasswordManagerAvailable))
  val state: StateFlow<EnterAepState> = _state.asStateFlow()

  private val _actions = Channel<EnterAepScreenActions>(Channel.BUFFERED)
  val actions: Flow<EnterAepScreenActions> = _actions.receiveAsFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: EnterAepEvents) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(inputState: EnterAepState, event: EnterAepEvents, stateEmitter: (EnterAepState) -> Unit) {
    when (event) {
      is EnterAepEvents.BackupKeyChanged -> {
        stateEmitter(EnterAepScreenEventHandler.applyEvent(inputState, event))
      }
      is EnterAepEvents.Submit -> {
        applySubmit(inputState, stateEmitter)
      }
      is EnterAepEvents.Cancel -> {
        parentEventEmitter(RegistrationFlowEvent.NavigateBack)
      }
      is EnterAepEvents.DismissError -> {
        stateEmitter(EnterAepScreenEventHandler.applyEvent(inputState, event))
      }
      is EnterAepEvents.TryAnotherWay -> {
        stateEmitter(inputState.copy(registrationError = null, showVerifyWithSmsDialog = true))
      }
      is EnterAepEvents.RecoveryKeyHelp -> {
        _actions.trySend(EnterAepScreenActions.OpenRecoveryKeyHelpArticle)
      }
      is EnterAepEvents.ConfirmVerifyWithSms -> {
        applyConfirmVerifyWithSms(inputState, stateEmitter)
      }
      is EnterAepEvents.DismissVerifyWithSmsDialog -> {
        stateEmitter(inputState.copy(showVerifyWithSmsDialog = false))
      }
      is EnterAepEvents.ConfirmDifferentAccountRestore,
      is EnterAepEvents.DismissDifferentAccountDialog -> {
        error("Different-account handling only exists for local backup restores.")
      }
    }
  }

  /**
   * The user gave up on their recovery key and wants to verify over SMS instead. Thankfully we can just post a result
   * and navigate back, letting the phone number entry screen handle it.
   */
  private fun applyConfirmVerifyWithSms(inputState: EnterAepState, stateEmitter: (EnterAepState) -> Unit) {
    Log.i(TAG, "[ConfirmVerifyWithSms] Handing control back to phone number entry to verify over SMS.")

    stateEmitter(inputState.copy(showVerifyWithSmsDialog = false))

    parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
    resultBus.sendResult(resultKey, EnterAepForRemoteBackupResult.VerifyWithSms)
    parentEventEmitter.navigateBack()
  }

  private suspend fun applySubmit(inputState: EnterAepState, stateEmitter: (EnterAepState) -> Unit) {
    check(inputState.recoveryKey.isValid) { "AEP is not valid, should not have gotten here." }

    val aep = AccountEntropyPool(inputState.recoveryKey.normalized)

    stateEmitter(inputState.copy(isRegistering = true))
    parentEventEmitter(RegistrationFlowEvent.UserSuppliedAepSubmitted(aep))

    Log.i(TAG, "[Submit] Attempting registration with RRP derived from user-supplied AEP.")

    attemptToRegister(inputState, aep, provideRegistrationLock = false, stateEmitter)
  }

  private suspend fun attemptToRegister(inputState: EnterAepState, aep: AccountEntropyPool, provideRegistrationLock: Boolean, stateEmitter: (EnterAepState) -> Unit) {
    val masterKey = aep.deriveMasterKey()
    val recoveryPassword = masterKey.deriveRegistrationRecoveryPassword()
    val registrationLock = masterKey.deriveRegistrationLock().takeIf { provideRegistrationLock }

    when (val result = repository.registerAccountWithRecoveryPassword(e164, recoveryPassword, registrationLock, existingAccountEntropyPool = aep)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[Submit] Successfully registered using RRP from user-supplied AEP.")
        val (response, keyMaterial, aci) = result.result

        stateEmitter(inputState.copy(isRegistering = false))
        parentEventEmitter(RegistrationFlowEvent.Registered(aci, keyMaterial.accountEntropyPool, response.storageCapable, phoneNumberless = response.e164 == null))
        parentEventEmitter.navigateTo(RegistrationRoute.RemoteRestore(aep))
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[Submit] RRP incorrect. Message: ${error.message}")
            stateEmitter(
              inputState.copy(
                isRegistering = false,
                registrationError = RegistrationError.IncorrectRecoveryPassword,
                recoveryKey = inputState.recoveryKey.copy(error = AepValidationError.Incorrect)
              )
            )
          }
          is RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[Submit] Invalid request. Message: ${error.message}")
            stateEmitter(
              inputState.copy(
                isRegistering = false,
                registrationError = RegistrationError.UnknownError
              )
            )
          }
          is RegisterAccountError.RegistrationLock -> {
            if (provideRegistrationLock) {
              Log.w(TAG, "[Submit] Still registration locked after providing the reglock token derived from the AEP. Falling back to PIN entry.")
              stateEmitter(inputState.copy(isRegistering = false))
              parentEventEmitter.navigateTo(
                RegistrationRoute.PinEntryForRegistrationLock(
                  timeRemaining = error.data.timeRemaining,
                  svrCredentials = error.data.svr2Credentials
                )
              )
            } else {
              Log.w(TAG, "[Submit] Registration locked. Retrying with the reglock token derived from the AEP.")
              attemptToRegister(inputState, aep, provideRegistrationLock = true, stateEmitter)
            }
          }
          is RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[Submit] Rate limited (retryAfter: ${error.retryAfter}).")
            stateEmitter(inputState.copy(isRegistering = false, registrationError = RegistrationError.RateLimited))
          }
          is RegisterAccountError.SessionNotFoundOrNotVerified -> {
            error("[Submit] Session not found or not verified. This should not happen with RRP-based registration.")
          }
          is RegisterAccountError.DeviceTransferPossible -> {
            error("[Submit] Device transfer possible. This should not happen with RRP-based registration.")
          }
          is RegisterAccountError.InvalidReceiptCredentialPresentation,
          is RegisterAccountError.TwoFactorRequired,
          RegisterAccountError.PostQuantumRatchetRequired -> {
            Log.w(TAG, "[Submit] Unexpected registration error: $error")
            stateEmitter(
              inputState.copy(
                isRegistering = false,
                registrationError = RegistrationError.UnknownError
              )
            )
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[Submit] Network error.", result.networkError)
        stateEmitter(inputState.copy(isRegistering = false, registrationError = RegistrationError.NetworkError))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[Submit] Application error.", result.cause)
        stateEmitter(inputState.copy(isRegistering = false, registrationError = RegistrationError.UnknownError))
      }
    }
  }
}

/** Result sent back to phone number entry from [EnterAepForRemoteBackupPreRegistrationViewModel]. */
sealed interface EnterAepForRemoteBackupResult {
  /** The user chose to register by verifying their number over SMS instead of with their recovery key. */
  data object VerifyWithSms : EnterAepForRemoteBackupResult
}
