/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import org.signal.core.models.AccountEntropyPool

object EnterAepScreenEventHandler {

  fun applyEvent(state: EnterAepState, event: EnterAepEvents): EnterAepState {
    return when (event) {
      is EnterAepEvents.BackupKeyChanged -> applyBackupKeyChanged(state, event.value)
      is EnterAepEvents.DismissError -> state.copy(registrationError = null)
      else -> throw UnsupportedOperationException("This event is not handled generically!")
    }
  }

  private fun applyBackupKeyChanged(state: EnterAepState, key: String): EnterAepState {
    val newKey = AccountEntropyPool.removeIllegalCharacters(key)
      .take(AccountEntropyPool.LENGTH + 16)
      .lowercase()

    val isValid = AccountEntropyPool.isFullyValid(newKey)
    val isShort = newKey.length < AccountEntropyPool.LENGTH
    val isExact = newKey.length == AccountEntropyPool.LENGTH

    val previousError = state.aepValidationError

    var updatedError: AepValidationError? = when (previousError) {
      is AepValidationError.TooLong -> if (isShort || isExact) null else previousError.copy(count = newKey.length)
      AepValidationError.Invalid -> if (isValid) null else previousError
      AepValidationError.Incorrect -> null
      null -> null
    }

    if (updatedError == null) {
      updatedError = when {
        !isShort && !isExact -> AepValidationError.TooLong(newKey.length, AccountEntropyPool.LENGTH)
        !isValid && isExact -> AepValidationError.Invalid
        else -> null
      }
    }

    return state.copy(
      backupKey = newKey,
      isBackupKeyValid = isValid,
      aepValidationError = updatedError
    )
  }
}
