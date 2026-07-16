/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.censor
import kotlin.time.Duration

data class PinCreationState(
  val isAlphanumericKeyboard: Boolean = false,
  val isConfirmEnabled: Boolean = false,
  val pinMismatch: Boolean = false,
  val loading: Boolean = false,
  val firstPin: String? = null,
  val accountEntropyPool: AccountEntropyPool? = null,
  val dialogs: Dialogs = Dialogs()
) {
  override fun toString(): String {
    return "PinCreationState(isAlphanumericKeyboard=$isAlphanumericKeyboard, isConfirmEnabled=$isConfirmEnabled, pinMismatch=$pinMismatch, loading=$loading, firstPin=${firstPin?.let { "${it.length} chars" }}, accountEntropyPool=${accountEntropyPool?.displayValue?.censor()}, dialogs=$dialogs)"
  }

  data class Dialogs(
    val serviceError: Boolean = false,
    val networkError: NetworkError? = null
  ) {
    data class NetworkError(val retryAfter: Duration?)
  }
}
