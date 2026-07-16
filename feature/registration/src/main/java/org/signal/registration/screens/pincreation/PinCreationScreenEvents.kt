/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pincreation

import org.signal.registration.RegistrationFlowState

sealed class PinCreationScreenEvents {
  /** The parent registration flow state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: RegistrationFlowState) : PinCreationScreenEvents()

  data class PinSubmitted(val pin: String) : PinCreationScreenEvents() {
    override fun toString(): String = "PinSubmitted(pin=${pin.length} chars)"
  }
  data object ToggleKeyboard : PinCreationScreenEvents()
  data object LearnMore : PinCreationScreenEvents()
  data object OptOut : PinCreationScreenEvents()
  data object BackToPinEntry : PinCreationScreenEvents()
  data object ServiceErrorDialogDismissed : PinCreationScreenEvents()
  data object NetworkErrorDialogDismissed : PinCreationScreenEvents()
}
