/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.core.util.logging.Log
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.screens.util.navigateBack

class EnterAepViewModel(
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  private val resultBus: ResultEventBus,
  private val resultKey: String
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(EnterAepViewModel::class)
  }

  private val _state = MutableStateFlow(EnterAepState())
  val state: StateFlow<EnterAepState> = _state.asStateFlow()

  fun onEvent(event: EnterAepEvents) {
    Log.d(TAG, "[Event] $event")
    when (event) {
      is EnterAepEvents.BackupKeyChanged -> applyBackupKeyChanged(event.value)
      is EnterAepEvents.Submit -> {
        if (_state.value.isBackupKeyValid) {
          resultBus.sendResult(resultKey, _state.value.backupKey)
          parentEventEmitter.navigateBack()
        }
      }
      is EnterAepEvents.Cancel -> {
        parentEventEmitter.navigateBack()
      }
    }
  }

  private fun applyBackupKeyChanged(key: String) {
    val newKey = AccountEntropyPool.removeIllegalCharacters(key)
      .take(AccountEntropyPool.LENGTH + 16)
      .lowercase()

    val changed = newKey != _state.value.backupKey
    val isValid = AccountEntropyPool.isFullyValid(newKey)
    val isShort = newKey.length < AccountEntropyPool.LENGTH
    val isExact = newKey.length == AccountEntropyPool.LENGTH

    val previousError = _state.value.aepValidationError

    // Check if previous error still applies
    var updatedError: AepValidationError? = when (previousError) {
      is AepValidationError.TooLong -> if (isShort || isExact) null else previousError.copy(count = newKey.length)
      AepValidationError.Invalid -> if (isValid) null else previousError
      null -> null
    }

    // Check for new errors
    if (updatedError == null) {
      updatedError = when {
        !isShort && !isExact -> AepValidationError.TooLong(newKey.length, AccountEntropyPool.LENGTH)
        !isValid && isExact -> AepValidationError.Invalid
        else -> null
      }
    }

    _state.update {
      it.copy(
        backupKey = newKey,
        isBackupKeyValid = isValid,
        aepValidationError = updatedError
      )
    }
  }

  class Factory(
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val resultBus: ResultEventBus,
    private val resultKey: String
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return EnterAepViewModel(parentEventEmitter, resultBus, resultKey) as T
    }
  }
}
