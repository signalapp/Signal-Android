/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

data class PinEntryState(
  val errorMessage: String? = null,
  val showNeedHelp: Boolean = false,
  val isNumericKeyboard: Boolean = true
)
