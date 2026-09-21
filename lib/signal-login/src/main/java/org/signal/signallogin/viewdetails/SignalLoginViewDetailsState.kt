/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.viewdetails

import org.signal.core.util.censor
import org.signal.signallogin.RecoveryKeyGroups

/**
 * State for the screen that shows the user the full keys that make up their Signal Login.
 *
 * [showResetRecoveryKeyButton] is only true when the screen is reached from account settings, which is the only entry
 * point that can put the user through a recovery key reset. While [resetRecoveryKeyButtonLoading] is true we don't yet
 * know whether the user has any resets left, so a spinner stands in for the button.
 *
 * [showSaveToPasswordManagerButton] is false during registration, where the only save option we offer is the PDF.
 *
 * [isPasswordManagerAvailable] is false when the device has no password manager at all. The save button is still shown
 * then, but styled as disabled, so the user can find out why it isn't an option rather than wonder where it went.
 */
data class SignalLoginViewDetailsState(
  val accountKey: String = "",
  val recoveryKey: String = "",
  val showSaveToPasswordManagerButton: Boolean = true,
  val isPasswordManagerAvailable: Boolean = true,
  val showResetRecoveryKeyButton: Boolean = false,
  val resetRecoveryKeyButtonLoading: Boolean = false
) {
  /** The recovery key broken into character groups, in display order. */
  val recoveryKeyGroups: RecoveryKeyGroups
    get() = RecoveryKeyGroups.from(recoveryKey)

  override fun toString(): String = "SignalLoginViewDetailsState(accountKey=${accountKey.censor()}, recoveryKey=${recoveryKey.censor()}, showSaveToPasswordManagerButton=$showSaveToPasswordManagerButton, isPasswordManagerAvailable=$isPasswordManagerAvailable, showResetRecoveryKeyButton=$showResetRecoveryKeyButton, resetRecoveryKeyButtonLoading=$resetRecoveryKeyButtonLoading)"
}
