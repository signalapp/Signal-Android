/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedpin

/**
 * [AdvancedPinSettingsScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class AdvancedPinSettingsState(
  /** The user has turned their PIN off, so the screen offers to turn it back on. */
  val hasOptedOutOfPin: Boolean = false,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** The PIN can't be turned off while registration lock is on. */
    data object RegistrationLockRequiresPin : Dialog

    /** The payments recovery phrase has to be recorded before the PIN can be turned off. */
    data object RecordPaymentsRecoveryPhrase : Dialog

    /** Turning the PIN off with backups on means rotating the backup key first. */
    data object RotateBackupKey : Dialog
  }

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** A PIN was just created. */
    data object PinCreated : Snackbar

    /** The PIN was just turned off. */
    data object PinDisabled : Snackbar
  }
}
