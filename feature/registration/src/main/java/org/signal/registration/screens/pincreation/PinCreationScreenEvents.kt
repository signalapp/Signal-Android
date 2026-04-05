/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import org.signal.registration.util.DebugLoggableModel

sealed class PinCreationScreenEvents : DebugLoggableModel() {
  data class PinSubmitted(val pin: String) : PinCreationScreenEvents()
  data object ToggleKeyboard : PinCreationScreenEvents()
  data object LearnMore : PinCreationScreenEvents()
}
