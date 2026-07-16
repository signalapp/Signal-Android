/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import org.signal.core.util.censor

data class EnterAepState(
  /** The user's typed text, preserved verbatim (illegal chars stripped). Bound to the TextField so #/= stay visible as the user types them. */
  val enteredText: String = "",
  /** Storage-normalized lowercase form of [enteredText], used for validation and submit. */
  val backupKey: String = "",
  val isBackupKeyValid: Boolean = false,
  val aepValidationError: AepValidationError? = null,
  val chunkLength: Int = 4,
  val isRegistering: Boolean = false,
  val registrationError: RegistrationError? = null,
  /** The entered key decrypts the backup, but the backup belongs to a different account. Asks whether to restore it anyway after SMS verification. */
  val showDifferentAccountDialog: Boolean = false,
  /** Whether a password manager / credential provider is available to fill the recovery key. */
  val isPasswordManagerAvailable: Boolean = false
) {
  override fun toString(): String = "EnterAepState(enteredText=${enteredText.censor()}, backupKey=${backupKey.censor()}, isBackupKeyValid=$isBackupKeyValid, aepValidationError=$aepValidationError, chunkLength=$chunkLength, isRegistering=$isRegistering, registrationError=$registrationError, showDifferentAccountDialog=$showDifferentAccountDialog, isPasswordManagerAvailable=$isPasswordManagerAvailable)"
}

sealed interface AepValidationError {
  data class TooLong(val count: Int, val max: Int) : AepValidationError
  data object Invalid : AepValidationError
  data object Incorrect : AepValidationError
}

sealed interface RegistrationError {
  data object IncorrectRecoveryPassword : RegistrationError
  data object RateLimited : RegistrationError
  data object NetworkError : RegistrationError
  data object UnknownError : RegistrationError
}
