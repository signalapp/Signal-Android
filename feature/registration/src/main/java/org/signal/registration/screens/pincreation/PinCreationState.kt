/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import org.signal.core.models.AccountEntropyPool

data class PinCreationState(
  val isAlphanumericKeyboard: Boolean = false,
  val inputLabel: String? = null,
  val isConfirmEnabled: Boolean = false,
  val accountEntropyPool: AccountEntropyPool? = null
)
