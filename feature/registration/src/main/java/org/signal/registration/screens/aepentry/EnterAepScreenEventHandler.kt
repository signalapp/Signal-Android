/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

object EnterAepScreenEventHandler {

  fun applyEvent(state: EnterAepState, event: EnterAepEvents): EnterAepState {
    return when (event) {
      is EnterAepEvents.BackupKeyChanged -> state.copy(recoveryKey = AepInput.from(event.value, state.recoveryKey.error))
      is EnterAepEvents.DismissError -> state.copy(registrationError = null)
      else -> throw UnsupportedOperationException("This event is not handled generically!")
    }
  }
}
