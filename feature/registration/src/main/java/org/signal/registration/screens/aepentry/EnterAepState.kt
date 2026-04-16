/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import org.signal.registration.util.DebugLoggableModel

data class EnterAepState(
  val backupKey: String = "",
  val isBackupKeyValid: Boolean = false,
  val aepValidationError: AepValidationError? = null,
  val chunkLength: Int = 4,
  val isRegistering: Boolean = false,
  val registrationError: RegistrationError? = null
) : DebugLoggableModel()

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
