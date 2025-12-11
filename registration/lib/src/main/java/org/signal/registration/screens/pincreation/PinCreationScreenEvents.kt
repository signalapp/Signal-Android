/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

sealed class PinCreationScreenEvents {
  data class PinSubmitted(val pin: String) : PinCreationScreenEvents()
  data object ToggleKeyboard : PinCreationScreenEvents()
  data object LearnMore : PinCreationScreenEvents()
}
