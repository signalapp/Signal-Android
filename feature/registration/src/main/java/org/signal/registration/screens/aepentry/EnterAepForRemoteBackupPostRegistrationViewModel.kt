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
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateTo

class EnterAepForRemoteBackupPostRegistrationViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  isPasswordManagerAvailable: Boolean = false
) : EventDrivenViewModel<EnterAepEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(EnterAepForRemoteBackupPostRegistrationViewModel::class)
  }

  private val _state = MutableStateFlow(EnterAepState(isPasswordManagerAvailable = isPasswordManagerAvailable))
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
      is EnterAepEvents.ConfirmDifferentAccountRestore,
      is EnterAepEvents.DismissDifferentAccountDialog -> {
        error("Different-account handling only exists for local backup restores.")
      }
    }
  }

  /**
   * The account is already registered, so we verify the entered key by checking it against the remote backup. This lets
   * us surface an [AepValidationError.Incorrect] error inline before navigating to the restore screen, rather than
   * failing partway through a restore with no recourse.
   */
  private suspend fun applySubmit(inputState: EnterAepState, stateEmitter: (EnterAepState) -> Unit) {
    check(inputState.isBackupKeyValid) { "AEP is not valid, should not have gotten here." }

    val aep = AccountEntropyPool(inputState.backupKey)

    stateEmitter(inputState.copy(isRegistering = true))

    Log.i(TAG, "[Submit] Verifying user-supplied AEP against remote backup.")

    when (val result = repository.verifyBackupKeyAssociatedWithAccount(aep)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[Submit] Backup key verified.")
        stateEmitter(inputState.copy(isRegistering = false))
        parentEventEmitter(RegistrationFlowEvent.UserSuppliedAepSubmitted(aep))
        parentEventEmitter.navigateTo(RegistrationRoute.RemoteRestore(aep))
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is NetworkController.VerifyBackupKeyError.IncorrectKey,
          is NetworkController.VerifyBackupKeyError.NoBackup -> {
            Log.w(TAG, "[Submit] Entered backup key is incorrect (error: $error).")
            stateEmitter(inputState.copy(isRegistering = false, aepValidationError = AepValidationError.Incorrect))
          }
          is NetworkController.VerifyBackupKeyError.RateLimited -> {
            Log.w(TAG, "[Submit] Rate limited (retryAfter: ${error.retryAfter}).")
            stateEmitter(inputState.copy(isRegistering = false, registrationError = RegistrationError.RateLimited))
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
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val isPasswordManagerAvailable: Boolean = false
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return EnterAepForRemoteBackupPostRegistrationViewModel(repository, parentEventEmitter, isPasswordManagerAvailable) as T
    }
  }
}
