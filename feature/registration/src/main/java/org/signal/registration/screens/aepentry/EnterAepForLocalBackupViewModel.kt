/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo

/**
 * Recovery key entry for a local V2 backup restore.
 *
 * When [isPreRegistration] is true (pre-registration manual restore), submitting the key first verifies it can
 * decrypt the selected backup, then attempts recovery-password registration with it -- the restore itself only ever
 * runs against a registered account. If the server rejects the recovery password, the key is valid but the backup
 * belongs to a different account: the user is warned and can choose to restore it anyway, which defers the import
 * until after they verify their number over SMS.
 *
 * When [isPreRegistration] is false (already registered), the key is simply handed back to the restore screen.
 */
class EnterAepForLocalBackupViewModel(
  private val isPreRegistration: Boolean,
  private val backupUri: String?,
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val resultBus: ResultEventBus,
  private val resultKey: String,
  isPasswordManagerAvailable: Boolean = false
) : EventDrivenViewModel<EnterAepEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(EnterAepForLocalBackupViewModel::class)
  }

  private val _state = MutableStateFlow(EnterAepState(isPasswordManagerAvailable = isPasswordManagerAvailable))
  val state: StateFlow<EnterAepState> = _state.asStateFlow()

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
        parentEventEmitter.navigateBack()
      }
      is EnterAepEvents.DismissError -> {
        stateEmitter(EnterAepScreenEventHandler.applyEvent(inputState, event))
      }
      is EnterAepEvents.ConfirmDifferentAccountRestore -> {
        applyConfirmDifferentAccountRestore(inputState, stateEmitter)
      }
      is EnterAepEvents.DismissDifferentAccountDialog -> {
        stateEmitter(inputState.copy(showDifferentAccountDialog = false))
      }
    }
  }

  private suspend fun applySubmit(inputState: EnterAepState, stateEmitter: (EnterAepState) -> Unit) {
    check(inputState.isBackupKeyValid) { "AEP is not valid, should not have gotten here." }

    if (!isPreRegistration) {
      resultBus.sendResult(resultKey, EnterAepForLocalBackupResult.RestoreReady(inputState.backupKey))
      parentEventEmitter.navigateBack()
      return
    }

    val aep = AccountEntropyPool(inputState.backupKey)

    stateEmitter(inputState.copy(isRegistering = true))

    // Confirm the key actually decrypts the backup before going to the server, so a recovery-password rejection can
    // only mean the backup belongs to a different account rather than a mistyped key.
    if (!repository.verifyLocalBackupKey(checkNotNull(backupUri).toUri(), aep)) {
      Log.w(TAG, "[Submit] Entered key cannot decrypt the selected backup.")
      stateEmitter(inputState.copy(isRegistering = false, aepValidationError = AepValidationError.Incorrect))
      return
    }

    parentEventEmitter(RegistrationFlowEvent.UserSuppliedAepSubmitted(aep))

    Log.i(TAG, "[Submit] Attempting registration with RRP derived from user-supplied AEP.")

    attemptToRegister(inputState, aep, provideRegistrationLock = false, stateEmitter)
  }

  private suspend fun attemptToRegister(inputState: EnterAepState, aep: AccountEntropyPool, provideRegistrationLock: Boolean, stateEmitter: (EnterAepState) -> Unit) {
    val e164 = checkNotNull(parentState.value.sessionE164) { "No e164 present in the flow state, should not have gotten here." }
    val masterKey = aep.deriveMasterKey()
    val recoveryPassword = masterKey.deriveRegistrationRecoveryPassword()
    val registrationLock = masterKey.deriveRegistrationLock().takeIf { provideRegistrationLock }

    when (val result = repository.registerAccountWithRecoveryPassword(e164, recoveryPassword, registrationLock, existingAccountEntropyPool = aep)) {
      is RequestResult.Success -> {
        Log.i(TAG, "[Submit] Successfully registered using RRP from user-supplied AEP. Proceeding with the restore.")
        val (response, keyMaterial) = result.result

        stateEmitter(inputState.copy(isRegistering = false))
        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool, response.storageCapable))
        resultBus.sendResult(resultKey, EnterAepForLocalBackupResult.RestoreReady(inputState.backupKey))
        parentEventEmitter.navigateBack()
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            Log.w(TAG, "[Submit] RRP incorrect, but the key decrypts the backup. The backup belongs to a different account. Message: ${error.message}")
            stateEmitter(inputState.copy(isRegistering = false, showDifferentAccountDialog = true))
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

  /**
   * The user chose to restore the different-account backup anyway. Force the session/SMS path (the recovery password
   * won't work), then hand control back so the flow verifies the number over SMS. The restore is resumed once
   * registration completes, keyed off the still-set [RegistrationFlowState.pendingRestoreOption] and the entered
   * [RegistrationFlowState.unverifiedRestoredAep].
   */
  private fun applyConfirmDifferentAccountRestore(inputState: EnterAepState, stateEmitter: (EnterAepState) -> Unit) {
    Log.i(TAG, "[ConfirmDifferentAccountRestore] Deferring the restore until after SMS verification.")

    stateEmitter(inputState.copy(showDifferentAccountDialog = false))

    parentEventEmitter(RegistrationFlowEvent.RecoveryPasswordInvalid)
    resultBus.sendResult(resultKey, EnterAepForLocalBackupResult.RegistrationDeferredToSms)
    parentEventEmitter.navigateBack()
  }

  class Factory(
    private val isPreRegistration: Boolean,
    private val backupUri: String?,
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val resultBus: ResultEventBus,
    private val resultKey: String,
    private val isPasswordManagerAvailable: Boolean = false
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return EnterAepForLocalBackupViewModel(isPreRegistration, backupUri, repository, parentState, parentEventEmitter, resultBus, resultKey, isPasswordManagerAvailable) as T
    }
  }
}

/** Result sent back to the local backup restore screen from [EnterAepForLocalBackupViewModel]. */
sealed interface EnterAepForLocalBackupResult {
  /** The account is registered (either it already was, or RRP registration just succeeded) and the backup can be restored with [key]. */
  data class RestoreReady(val key: String) : EnterAepForLocalBackupResult

  /** The backup belongs to a different account and the user chose to restore it anyway. Registration must happen over SMS first. */
  data object RegistrationDeferredToSms : EnterAepForLocalBackupResult
}
