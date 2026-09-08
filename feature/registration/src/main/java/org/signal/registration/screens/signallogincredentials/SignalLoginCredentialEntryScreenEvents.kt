/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import org.signal.core.util.censor

sealed class SignalLoginCredentialEntryScreenEvents {
  /** The user tapped the back arrow. */
  data object BackClicked : SignalLoginCredentialEntryScreenEvents()

  /** The user edited the account ID field. Carries the raw text, formatting and all. */
  data class AccountIdChanged(val value: String) : SignalLoginCredentialEntryScreenEvents() {
    override fun toString(): String = "AccountIdChanged(value=${value.censor()})"
  }

  /** The user edited the recovery key field. Carries the raw text, formatting and all. */
  data class RecoveryKeyChanged(val value: String) : SignalLoginCredentialEntryScreenEvents() {
    override fun toString(): String = "RecoveryKeyChanged(value=${value.censor()})"
  }

  /** The user picked a saved login from the password manager prompt. Carries both halves as the manager stored them. */
  data class PasswordManagerCredentialSelected(val accountId: String, val recoveryKey: String) : SignalLoginCredentialEntryScreenEvents() {
    override fun toString(): String = "PasswordManagerCredentialSelected(accountId=${accountId.censor()}, recoveryKey=${recoveryKey.censor()})"
  }

  /** The user tapped the eye button that switches the recovery key between masked and spelled out. */
  data object RecoveryKeyVisibilityToggled : SignalLoginCredentialEntryScreenEvents()

  /** The user tapped "Need help?". Only reachable in [SignalLoginCredentialEntryState.Mode.Login]. */
  data object NeedHelpClicked : SignalLoginCredentialEntryScreenEvents()

  /** The user tapped "Show login info again". Only reachable in [SignalLoginCredentialEntryState.Mode.ConfirmSaved]. */
  data object ShowLoginInfoAgainClicked : SignalLoginCredentialEntryScreenEvents()

  /** The user submitted the login, either with the next button or the keyboard's done action. */
  data object NextClicked : SignalLoginCredentialEntryScreenEvents()

  /** The user completed the two-factor flow the login was bounced to. Carries the entered code. */
  data class TwoFactorCodeEntered(val code: String) : SignalLoginCredentialEntryScreenEvents() {
    override fun toString(): String = "TwoFactorCodeEntered(code=${code.censor()})"
  }

  /** The user dismissed the login error dialog. */
  data object DismissError : SignalLoginCredentialEntryScreenEvents()
}
