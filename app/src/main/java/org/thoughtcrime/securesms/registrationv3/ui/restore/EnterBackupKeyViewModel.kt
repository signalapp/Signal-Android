/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registrationv3.ui.restore.AccountEntropyPoolVerification.AEPValidationError
import org.whispersystems.signalservice.api.AccountEntropyPool

class EnterBackupKeyViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(EnterBackupKeyViewModel::class)
  }

  private val store = MutableStateFlow(
    EnterBackupKeyState(
      requiredLength = 64,
      chunkLength = 4
    )
  )

  var backupKey by mutableStateOf("")
    private set

  val state: StateFlow<EnterBackupKeyState> = store

  fun updateBackupKey(key: String) {
    val newKey = AccountEntropyPool.removeIllegalCharacters(key).take(AccountEntropyPool.LENGTH + 16).lowercase()
    val changed = newKey != backupKey
    backupKey = newKey
    store.update {
      val (isValid, updatedError) = AccountEntropyPoolVerification.verifyAEP(
        backupKey = backupKey,
        changed = changed,
        previousAEPValidationError = it.aepValidationError
      )
      it.copy(backupKeyValid = isValid, aepValidationError = updatedError)
    }
  }

  fun registering() {
    store.update { it.copy(isRegistering = true) }
  }

  fun handleRegistrationFailure(registerAccountResult: RegisterAccountResult) {
    store.update {
      if (it.isRegistering) {
        Log.w(TAG, "Unable to register [${registerAccountResult::class.simpleName}]", registerAccountResult.getCause())
        val incorrectKeyError = registerAccountResult is RegisterAccountResult.IncorrectRecoveryPassword

        if (incorrectKeyError && SignalStore.account.restoredAccountEntropyPool) {
          SignalStore.account.resetAccountEntropyPool()
        }

        it.copy(
          isRegistering = false,
          showRegistrationError = true,
          registerAccountResult = registerAccountResult,
          aepValidationError = if (incorrectKeyError) AEPValidationError.Incorrect else it.aepValidationError
        )
      } else {
        it
      }
    }
  }

  fun clearRegistrationError() {
    store.update {
      it.copy(
        showRegistrationError = false,
        registerAccountResult = null
      )
    }
  }

  fun handleBackupTierNotRestored() {
    store.update {
      it.copy(
        showBackupTierNotRestoreError = true
      )
    }
  }

  fun hideRestoreBackupKeyFailed() {
    store.update {
      it.copy(
        showBackupTierNotRestoreError = false
      )
    }
  }

  suspend fun performStorageServiceAccountRestoreIfNeeded() {
    if (SignalStore.account.restoredAccountEntropyPool || SignalStore.svr.masterKeyForInitialDataRestore != null) {
      store.update { it.copy(showBackupTierNotRestoreError = false, showStorageAccountRestoreProgress = true) }
      StorageServiceRestore.restore()
    }
  }

  data class EnterBackupKeyState(
    val backupKeyValid: Boolean = false,
    val requiredLength: Int,
    val chunkLength: Int,
    val isRegistering: Boolean = false,
    val showRegistrationError: Boolean = false,
    val showBackupTierNotRestoreError: Boolean = false,
    val registerAccountResult: RegisterAccountResult? = null,
    val aepValidationError: AEPValidationError? = null,
    val showStorageAccountRestoreProgress: Boolean = false
  )
}
