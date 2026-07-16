/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import org.signal.registration.RegistrationFlowState

sealed class PinEntryScreenEvents {
  /** The parent registration flow state changed and needs to be merged into this screen's state. */
  data class ParentStateChanged(val parentState: RegistrationFlowState) : PinEntryScreenEvents()

  data class PinEntered(val pin: String) : PinEntryScreenEvents() {
    override fun toString(): String = "PinEntered(pin=${pin.length} chars)"
  }
  data object ToggleKeyboard : PinEntryScreenEvents()

  /** The user dismissed the network error dialog. */
  data object NetworkErrorDialogDismissed : PinEntryScreenEvents()

  /** The user dismissed the rate limited dialog. */
  data object RateLimitedDialogDismissed : PinEntryScreenEvents()

  /** The user dismissed the unknown error dialog. */
  data object UnknownErrorDialogDismissed : PinEntryScreenEvents()
  data object Skip : PinEntryScreenEvents()
  data object CreateNewPin : PinEntryScreenEvents()
  data object ContactSupport : PinEntryScreenEvents()
}
