/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import org.signal.core.util.censor
import org.signal.registration.screens.aepentry.AepInput
import org.signal.registration.screens.shared.AccountIdError
import org.signal.registration.screens.shared.AccountIdFormat

/**
 * State for the screen where a user types both halves of a Signal Login in: the account ID and the recovery key that
 * pairs with it. [mode] says which of the two jobs the screen is doing, which the flow pairs with a matching view model.
 *
 * [accountId] holds the ID without any of the formatting the user sees: the screen renders the dashes and the
 * uppercasing itself, so what is stored here is always the raw lowercase value.
 */
data class SignalLoginCredentialEntryState(
  val mode: Mode = Mode.Login,
  val accountId: String = "",
  val accountIdError: AccountIdError? = null,
  /** Whether [accountId] was handed to the screen by the flow rather than typed by the user, and so isn't user input. */
  val isAccountIdPrefilled: Boolean = false,
  val recoveryKey: AepInput = AepInput(),
  /** Whether the recovery key is spelled out rather than masked like a password. */
  val isRecoveryKeyRevealed: Boolean = false,
  /** The service rejected the pair. Either half could be at fault, so both fields are flagged rather than just one. */
  val areCredentialsIncorrect: Boolean = false,
  val isLoggingIn: Boolean = false,
  val loginError: SignalLoginError? = null
) {

  /** Whether both halves of the login are complete and well-formed enough to attempt. */
  val isNextEnabled: Boolean
    get() = accountId.length == AccountIdFormat.ACCOUNT_ID_LENGTH &&
      accountIdError == null &&
      recoveryKey.isValid &&
      recoveryKey.error == null &&
      !areCredentialsIncorrect &&
      !isLoggingIn

  /** Allow prompting if it's empty or came pre-filled */
  val canPromptPasswordManager: Boolean
    get() = recoveryKey.enteredText.isEmpty() && (accountId.isEmpty() || isAccountIdPrefilled)

  override fun toString(): String = "SignalLoginCredentialEntryState(mode=$mode, accountId=${accountId.censor()}, accountIdError=$accountIdError, isAccountIdPrefilled=$isAccountIdPrefilled, recoveryKey=$recoveryKey, isRecoveryKeyRevealed=$isRecoveryKeyRevealed, areCredentialsIncorrect=$areCredentialsIncorrect, isLoggingIn=$isLoggingIn, loginError=$loginError)"

  enum class Mode {
    /** The user owns a Signal Login from before and is typing it in to get back into their account. */
    Login,

    /** The user just bought a Signal Login and is typing it back to prove they recorded it. */
    ConfirmSaved
  }
}

/** A login failure that the text fields can't express, so it gets a dialog instead. */
sealed interface SignalLoginError {
  data object RateLimited : SignalLoginError
  data object NetworkError : SignalLoginError
  data object UnknownError : SignalLoginError
}
