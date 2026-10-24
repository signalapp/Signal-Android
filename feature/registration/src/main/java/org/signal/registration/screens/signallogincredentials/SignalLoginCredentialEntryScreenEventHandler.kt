/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.registration.screens.aepentry.AepInput
import org.signal.registration.screens.shared.AccountIdFormat

/**
 * The events that mean the same thing no matter which job [SignalLoginCredentialEntryScreen] is doing, so that the view
 * model behind each [SignalLoginCredentialEntryState.Mode] only has to handle the ones that differ.
 */
object SignalLoginCredentialEntryScreenEventHandler {

  fun applyEvent(state: SignalLoginCredentialEntryState, event: SignalLoginCredentialEntryScreenEvents): SignalLoginCredentialEntryState {
    return when (event) {
      is SignalLoginCredentialEntryScreenEvents.AccountIdChanged -> {
        val accountId = AccountIdFormat.normalizeAndTruncate(event.value)
        state.copy(accountId = accountId, accountIdError = AccountIdFormat.validate(accountId), isAccountIdPrefilled = false, areCredentialsIncorrect = false)
      }

      is SignalLoginCredentialEntryScreenEvents.RecoveryKeyChanged -> {
        state.copy(recoveryKey = AepInput.from(event.value, state.recoveryKey.error), areCredentialsIncorrect = false)
      }

      is SignalLoginCredentialEntryScreenEvents.RecoveryKeyVisibilityToggled -> {
        state.copy(isRecoveryKeyRevealed = !state.isRecoveryKeyRevealed)
      }

      is SignalLoginCredentialEntryScreenEvents.DismissError -> {
        state.copy(loginError = null)
      }

      else -> throw UnsupportedOperationException("This event is not handled generically!")
    }
  }

  /**
   * Fills both fields from the password manager, keeping the current account ID if none came back. Callers check the
   * result straight away if it [SignalLoginCredentialEntryState.isNextEnabled].
   */
  fun applyPasswordManagerCredential(
    state: SignalLoginCredentialEntryState,
    event: SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected
  ): SignalLoginCredentialEntryState {
    val accountId = AccountIdFormat.normalizeAndTruncate(event.accountId).ifEmpty { state.accountId }
    return state.copy(
      accountId = accountId,
      accountIdError = AccountIdFormat.validate(accountId),
      isAccountIdPrefilled = false,
      recoveryKey = AepInput.from(event.recoveryKey),
      areCredentialsIncorrect = false
    )
  }

  /** Whether the account ID and recovery key in [state] are the given login. */
  fun matchesLogin(state: SignalLoginCredentialEntryState, aci: ACI, aep: AccountEntropyPool): Boolean {
    return AccountIdFormat.toAciOrNull(state.accountId) == aci && state.recoveryKey.normalized == aep.value
  }
}
