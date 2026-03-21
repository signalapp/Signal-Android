/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import org.signal.registration.util.DebugLoggableModel

data class EnterAepState(
  val backupKey: String = "",
  val isBackupKeyValid: Boolean = false,
  val aepValidationError: AepValidationError? = null,
  val chunkLength: Int = 4
) : DebugLoggableModel()

sealed interface AepValidationError {
  data class TooLong(val count: Int, val max: Int) : AepValidationError
  data object Invalid : AepValidationError
}
