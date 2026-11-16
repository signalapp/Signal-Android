/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedpin

/**
 * Everything that can happen on [AdvancedPinSettingsScreen].
 *
 * Events are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AdvancedPinSettingsEvent {

  /** The screen came to the foreground. */
  data object ScreenResumed : AdvancedPinSettingsEvent

  /** The user tapped the navigation icon. */
  data object NavigateBackClicked : AdvancedPinSettingsEvent

  /** The user tapped the row that turns the PIN on or off, whichever it currently offers. */
  data object PinToggleClicked : AdvancedPinSettingsEvent

  /** The user confirmed they want to record their payments recovery phrase. */
  data object RecordRecoveryPhraseConfirmed : AdvancedPinSettingsEvent

  /** The user confirmed they want to rotate their backup key so the PIN can be turned off. */
  data object RotateBackupKeyConfirmed : AdvancedPinSettingsEvent

  /** The current dialog was dismissed or cancelled. */
  data object DialogDismissed : AdvancedPinSettingsEvent

  /** The PIN was turned off after the user confirmed it in the opt-out dialog. */
  data object PinOptedOut : AdvancedPinSettingsEvent

  /** The user finished creating a PIN. */
  data object PinCreated : AdvancedPinSettingsEvent

  /** The backup key was rotated, so turning the PIN off can go ahead. */
  data object BackupKeyRotated : AdvancedPinSettingsEvent

  /** The snackbar finished showing. */
  data object SnackbarDismissed : AdvancedPinSettingsEvent
}
