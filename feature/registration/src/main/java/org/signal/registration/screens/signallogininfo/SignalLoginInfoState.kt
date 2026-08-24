/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import org.signal.core.util.censor

/**
 * State for the screen that hands the user their newly-purchased Signal Login and asks them to save it.
 *
 * The account and recovery values are shown masked, with only [VISIBLE_SUFFIX_LENGTH] trailing characters revealed,
 * until the user opts into seeing the full credentials.
 */
data class SignalLoginInfoState(
  val accountIdentifier: String = "",
  val recoveryKey: String = "",
  val isPasswordManagerAvailable: Boolean = false,
  val showSpinner: Boolean = false,
  val dialogs: Dialogs = Dialogs()
) {
  companion object {
    const val VISIBLE_SUFFIX_LENGTH = 4
  }

  val accountSuffix: String
    get() = accountIdentifier.takeLast(VISIBLE_SUFFIX_LENGTH)

  val recoverySuffix: String
    get() = recoveryKey.takeLast(VISIBLE_SUFFIX_LENGTH)

  override fun toString(): String = "SignalLoginInfoState(accountIdentifier=${accountIdentifier.censor()}, recoveryKey=${recoveryKey.censor()}, " +
    "isPasswordManagerAvailable=$isPasswordManagerAvailable, showSpinner=$showSpinner, dialogs=$dialogs)"

  data class Dialogs(
    /** Shows the full, unmasked credentials. */
    val credentialDetails: Boolean = false,
    val saveFailed: Boolean = false,
    val unknownError: Boolean = false
  )
}
