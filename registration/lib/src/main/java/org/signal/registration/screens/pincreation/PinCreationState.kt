/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

data class PinCreationState(
  val isNumericKeyboard: Boolean = true,
  val inputLabel: String? = null,
  val isConfirmEnabled: Boolean = false
)
