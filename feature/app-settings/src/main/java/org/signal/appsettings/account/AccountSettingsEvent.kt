/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AccountSettingsEvent {

  /** The screen came back to the foreground, so everything we read out of storage may be stale. */
  data object ScreenResumed : AccountSettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : AccountSettingsEvent

  /** The user tapped the row that either creates or changes their PIN. */
  data object ModifyPinClicked : AccountSettingsEvent

  /** The user came back from the PIN creation flow having actually set a PIN. */
  data object PinCreated : AccountSettingsEvent

  /** The user flipped the PIN reminders toggle. Turning them off requires confirming the PIN first. */
  data class PinRemindersToggled(val enabled: Boolean) : AccountSettingsEvent

  /** The user typed in the PIN confirmation field. */
  data class PinEntryChanged(val pin: String) : AccountSettingsEvent {
    override fun toString(): String = "PinEntryChanged(length=${pin.length})"
  }

  /** The user asked to switch between the numeric and alphanumeric PIN keyboards. */
  data object PinKeyboardToggled : AccountSettingsEvent

  /** The user submitted the PIN they entered to turn reminders off. */
  data object DisablePinRemindersConfirmed : AccountSettingsEvent

  /** The user flipped the registration lock toggle, which asks them to confirm first. */
  data class RegistrationLockToggled(val enabled: Boolean) : AccountSettingsEvent

  /** The user confirmed turning registration lock on or off. */
  data object RegistrationLockConfirmed : AccountSettingsEvent

  /** The user tapped the authenticator app row in the two-factor authentication section. */
  data object AuthenticatorAppClicked : AccountSettingsEvent

  /** The user tapped the advanced PIN settings row. */
  data object AdvancedPinSettingsClicked : AccountSettingsEvent

  /** The user tapped the change phone number row. */
  data object ChangePhoneNumberClicked : AccountSettingsEvent

  /** The user tapped the row that transfers this account to a new Android device. */
  data object TransferAccountClicked : AccountSettingsEvent

  /** The user tapped the row that requests a copy of their account data. */
  data object RequestAccountDataClicked : AccountSettingsEvent

  /** The user tapped the update row, which only a deprecated client shows. */
  data object UpdateSignalClicked : AccountSettingsEvent

  /** The user tapped the re-register row, which only an unregistered client shows. */
  data object ReRegisterClicked : AccountSettingsEvent

  /** The user tapped the delete all data row, which asks them to confirm first. */
  data object DeleteAllDataClicked : AccountSettingsEvent

  /** The user confirmed wiping all app data. */
  data object DeleteAllDataConfirmed : AccountSettingsEvent

  /** The fragment reported that clearing application data failed. */
  data object DataWipeFailed : AccountSettingsEvent

  /** The user tapped the delete account row. */
  data object DeleteAccountClicked : AccountSettingsEvent

  /** Dismisses whatever is in [AccountSettingsState.dialog]. */
  data object DialogDismissed : AccountSettingsEvent
}
