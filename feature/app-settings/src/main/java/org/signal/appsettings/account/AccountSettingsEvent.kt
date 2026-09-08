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

  /** The user tapped the Signal Login card, which shows the account and recovery keys. */
  data object AccountAndRecoveryClicked : AccountSettingsEvent

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

  /** The user tapped the authenticator app option in the two-factor set-up menu. */
  data object AddTotpAppClicked : AccountSettingsEvent

  /** The user tapped the learn more link on the dialog explaining the authenticator app limit. */
  data object LearnMoreClicked : AccountSettingsEvent

  /** The user tapped the rename option in [method]'s overflow menu. */
  data class RenameMethodClicked(val method: TwoFactorMethod) : AccountSettingsEvent

  /** The user tapped the remove option in [method]'s overflow menu, which asks for the screen lock first. */
  data class RemoveMethodClicked(val method: TwoFactorMethod) : AccountSettingsEvent

  /** The user got past their screen lock, so we can go on asking them to confirm removing [method]. */
  data class MethodRemovalAuthenticated(val method: TwoFactorMethod) : AccountSettingsEvent

  /** The screen lock turned the user away, so [RemoveMethodClicked] goes no further. */
  data object MethodRemovalAuthenticationFailed : AccountSettingsEvent

  /** The user confirmed removing the authenticator app named by the open dialog, which removes it. */
  data object RemoveTotpAppConfirmed : AccountSettingsEvent

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
