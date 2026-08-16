/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

data class AccountSettingsState(
  val hasPin: Boolean = false,
  val hasRestoredAep: Boolean = false,
  val pinRemindersEnabled: Boolean = false,
  val registrationLockEnabled: Boolean = false,
  val userUnregistered: Boolean = false,
  val clientDeprecated: Boolean = false,
  val canTransferWhileUnregistered: Boolean = true,
  val signalLogin: SignalLogin? = null,
  val dialog: Dialog = Dialog.None
) {

  val isNotDeprecatedOrUnregistered: Boolean
    get() = !(userUnregistered || clientDeprecated)

  /**
   * The Signal Login and two-factor authentication sections, which only exist when phone-numberless registration is
   * enabled. Null means the sections aren't shown at all.
   */
  data class SignalLogin(
    val keyCount: Int,
    val hasAuthenticatorApp: Boolean
  )

  /** Whichever dialog the screen is showing, if any. Only one is ever up at a time. */
  sealed interface Dialog {
    data object None : Dialog

    /** Confirms wiping all app data, which is all a deprecated or unregistered client can do. */
    data object ConfirmDeleteAllData : Dialog

    /**
     * Collects the PIN, which the user has to get right before we'll stop reminding them of it.
     * [canSubmit] is decided by the view model, which owns the minimum PIN length.
     */
    data class ConfirmPinToDisableReminders(
      val pin: String = "",
      val isAlphanumericKeyboard: Boolean = false,
      val incorrectPin: Boolean = false,
      val canSubmit: Boolean = false
    ) : Dialog {
      override fun toString(): String = "ConfirmPinToDisableReminders(incorrectPin=$incorrectPin)"
    }

    /** Confirms turning registration lock on or off, then spins while we tell the server. */
    data class ConfirmRegistrationLock(
      val enable: Boolean,
      val inProgress: Boolean = false
    ) : Dialog
  }
}
