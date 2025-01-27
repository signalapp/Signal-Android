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
    val newKey = AccountEntropyPool.removeIllegalCharacters(key).lowercase()
    val changed = newKey != backupKey
    backupKey = newKey
    store.update {
      val isValid = validateContents(backupKey)
      val isShort = backupKey.length < it.requiredLength
      val isExact = backupKey.length == it.requiredLength

      var updatedError: AEPValidationError? = checkErrorStillApplies(it.aepValidationError, isShort || isExact, isValid, changed)
      if (updatedError == null) {
        updatedError = checkForNewError(isShort, isExact, isValid, it.requiredLength)
      }

      it.copy(backupKeyValid = isValid, aepValidationError = updatedError)
    }
  }

  private fun validateContents(backupKey: String): Boolean {
    return AccountEntropyPool.isFullyValid(backupKey)
  }

  private fun checkErrorStillApplies(error: AEPValidationError?, isShortOrExact: Boolean, isValid: Boolean, isChanged: Boolean): AEPValidationError? {
    return when (error) {
      is AEPValidationError.TooLong -> if (isShortOrExact) null else error.copy(count = backupKey.length)
      AEPValidationError.Invalid -> if (isValid) null else error
      AEPValidationError.Incorrect -> if (isChanged) null else error
      null -> null
    }
  }

  private fun checkForNewError(isShort: Boolean, isExact: Boolean, isValid: Boolean, requiredLength: Int): AEPValidationError? {
    return if (!isShort && !isExact) {
      AEPValidationError.TooLong(backupKey.length, requiredLength)
    } else if (!isValid && isExact) {
      AEPValidationError.Invalid
    } else {
      null
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

  data class EnterBackupKeyState(
    val backupKeyValid: Boolean = false,
    val requiredLength: Int,
    val chunkLength: Int,
    val isRegistering: Boolean = false,
    val showRegistrationError: Boolean = false,
    val registerAccountResult: RegisterAccountResult? = null,
    val aepValidationError: AEPValidationError? = null
  )

  sealed interface AEPValidationError {
    data class TooLong(val count: Int, val max: Int) : AEPValidationError
    data object Invalid : AEPValidationError
    data object Incorrect : AEPValidationError
  }
}
