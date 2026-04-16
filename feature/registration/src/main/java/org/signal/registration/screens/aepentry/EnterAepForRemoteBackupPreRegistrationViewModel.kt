/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.util.navigateTo

class EnterAepForRemoteBackupPreRegistrationViewModel(
  private val e164: String,
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<EnterAepEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(EnterAepForRemoteBackupPreRegistrationViewModel::class)
  }

  private val _state = MutableStateFlow(EnterAepState())

  val state: StateFlow<EnterAepState> = _state.asStateFlow()

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
    }
  }

  private suspend fun applySubmit(inputState: EnterAepState, stateEmitter: (EnterAepState) -> Unit) {
    check(inputState.isBackupKeyValid) { "AEP is not valid, should not have gotten here." }

    val aep = AccountEntropyPool(inputState.backupKey)
    val recoveryPassword = aep.deriveMasterKey().deriveRegistrationRecoveryPassword()

    stateEmitter(inputState.copy(isRegistering = true))
    parentEventEmitter(RegistrationFlowEvent.UserSuppliedAepSubmitted(aep))

    Log.i(TAG, "[Submit] Attempting registration with RRP derived from user-supplied AEP.")

    when (val result = repository.registerAccountWithRecoveryPassword(e164, recoveryPassword, existingAccountEntropyPool = aep)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[Submit] Successfully registered using RRP from user-supplied AEP.")
        val (_, keyMaterial) = result.result

        stateEmitter(inputState.copy(isRegistering = false))
        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool))
        parentEventEmitter.navigateTo(RegistrationRoute.RemoteRestore(aep))
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[Submit] RRP incorrect.")
            stateEmitter(
              inputState.copy(
                isRegistering = false,
                registrationError = RegistrationError.IncorrectRecoveryPassword,
                aepValidationError = AepValidationError.Incorrect
              )
            )
          }
          is NetworkController.RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[Submit] Invalid request. Message: ${error.message}")
            stateEmitter(
              inputState.copy(
                isRegistering = false,
                registrationError = RegistrationError.UnknownError
              )
            )
          }
          is NetworkController.RegisterAccountError.RegistrationLock -> {
            Log.w(TAG, "[Submit] Registration locked.")
            stateEmitter(inputState.copy(isRegistering = false))
            parentEventEmitter.navigateTo(
              RegistrationRoute.PinEntryForRegistrationLock(
                timeRemaining = error.data.timeRemaining,
                svrCredentials = error.data.svr2Credentials
              )
            )
          }
          is NetworkController.RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[Submit] Rate limited (retryAfter: ${error.retryAfter}).")
            stateEmitter(inputState.copy(isRegistering = false, registrationError = RegistrationError.RateLimited))
          }
          is NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified -> {
            error("[Submit] Session not found or not verified. This should not happen with RRP-based registration.")
          }
          is NetworkController.RegisterAccountError.DeviceTransferPossible -> {
            error("[Submit] Device transfer possible. This should not happen with RRP-based registration.")
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

  class Factory(
    private val e164: String,
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return EnterAepForRemoteBackupPreRegistrationViewModel(e164, repository, parentEventEmitter) as T
    }
  }
}
