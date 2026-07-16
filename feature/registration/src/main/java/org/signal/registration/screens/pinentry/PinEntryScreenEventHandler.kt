/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

object PinEntryScreenEventHandler {

  fun applyEvent(state: PinEntryState, event: PinEntryScreenEvents): PinEntryState {
    return when (event) {
      PinEntryScreenEvents.ToggleKeyboard -> state.copy(isAlphanumericKeyboard = !state.isAlphanumericKeyboard)
      PinEntryScreenEvents.NetworkErrorDialogDismissed -> state.copy(dialogs = state.dialogs.copy(networkError = false))
      PinEntryScreenEvents.RateLimitedDialogDismissed -> state.copy(dialogs = state.dialogs.copy(rateLimitedRetryAfter = null))
      PinEntryScreenEvents.UnknownErrorDialogDismissed -> state.copy(dialogs = state.dialogs.copy(unknownError = false))
      else -> throw UnsupportedOperationException("This even is not handled generically!")
    }
  }
}
