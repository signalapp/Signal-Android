/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

data class EnterAepState(
  val recoveryKey: AepInput = AepInput(),
  val chunkLength: Int = 4,
  val isRegistering: Boolean = false,
  val registrationError: RegistrationError? = null,
  /** The entered key decrypts the backup, but the backup belongs to a different account. Asks whether to restore it anyway after SMS verification. */
  val showDifferentAccountDialog: Boolean = false,
  /** The user chose to try another way after entering an incorrect key. Offers to verify the number over SMS instead. */
  val showVerifyWithSmsDialog: Boolean = false,
  /** Whether a password manager / credential provider is available to fill the recovery key. */
  val isPasswordManagerAvailable: Boolean = false
)

sealed interface AepValidationError {
  data object Invalid : AepValidationError
  data object Incorrect : AepValidationError
}

sealed interface RegistrationError {
  data object IncorrectRecoveryPassword : RegistrationError

  /** The key verified against the account, but there is no remote backup to restore. Distinct from an incorrect key. */
  data object NoRemoteBackup : RegistrationError
  data object RateLimited : RegistrationError
  data object NetworkError : RegistrationError
  data object UnknownError : RegistrationError
}
