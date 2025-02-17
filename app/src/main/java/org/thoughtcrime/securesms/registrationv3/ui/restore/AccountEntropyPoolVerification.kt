/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import org.thoughtcrime.securesms.restore.enterbackupkey.PostRegistrationEnterBackupKeyViewModel
import org.whispersystems.signalservice.api.AccountEntropyPool

/**
 * Help verify a potential string could be an [AccountEntropyPool] string. Intended only
 * for use in [EnterBackupKeyViewModel] and [PostRegistrationEnterBackupKeyViewModel].
 */
object AccountEntropyPoolVerification {

  /**
   * Given a backup key and metadata around it's previous verification state, provide an updated or new state.
   *
   * @param backupKey key to verify
   * @param changed if the key has changed from the previous verification attempt
   * @param previousAEPValidationError the error if any of the previous verification attempt
   * @return [Pair] of is contents generally valid and any still present or new validation error
   */
  fun verifyAEP(backupKey: String, changed: Boolean, previousAEPValidationError: AEPValidationError?): Pair<Boolean, AEPValidationError?> {
    val isValid = validateContents(backupKey)
    val isShort = backupKey.length < AccountEntropyPool.LENGTH
    val isExact = backupKey.length == AccountEntropyPool.LENGTH

    var updatedError: AEPValidationError? = checkErrorStillApplies(backupKey, previousAEPValidationError, isShort || isExact, isValid, changed)
    if (updatedError == null) {
      updatedError = checkForNewError(backupKey, isShort, isExact, isValid)
    }

    return isValid to updatedError
  }

  private fun validateContents(backupKey: String): Boolean {
    return AccountEntropyPool.isFullyValid(backupKey)
  }

  private fun checkErrorStillApplies(backupKey: String, error: AEPValidationError?, isShortOrExact: Boolean, isValid: Boolean, isChanged: Boolean): AEPValidationError? {
    return when (error) {
      is AEPValidationError.TooLong -> if (isShortOrExact) null else error.copy(count = backupKey.length)
      AEPValidationError.Invalid -> if (isValid) null else error
      AEPValidationError.Incorrect -> if (isChanged) null else error
      null -> null
    }
  }

  private fun checkForNewError(backupKey: String, isShort: Boolean, isExact: Boolean, isValid: Boolean): AEPValidationError? {
    return if (!isShort && !isExact) {
      AEPValidationError.TooLong(backupKey.length, AccountEntropyPool.LENGTH)
    } else if (!isValid && isExact) {
      AEPValidationError.Invalid
    } else {
      null
    }
  }

  sealed interface AEPValidationError {
    data class TooLong(val count: Int, val max: Int) : AEPValidationError
    data object Invalid : AEPValidationError
    data object Incorrect : AEPValidationError
  }
}
