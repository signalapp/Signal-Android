/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

/**
 * One-shot side effects that need an Activity or the legacy nav graph, and therefore have to be carried out by
 * [AccountSettingsFragment] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AccountSettingsAction {

  /** Leave the screen. */
  data object NavigateBack : AccountSettingsAction

  /** Open the flow for creating a PIN for the first time. */
  data object LaunchCreatePinFlow : AccountSettingsAction

  /** Open the flow for changing an existing PIN. */
  data object LaunchChangePinFlow : AccountSettingsAction

  /** Tell the user their PIN was created. */
  data object ShowPinCreatedConfirmation : AccountSettingsAction

  /** Open the flow that sets up an authenticator app. */
  data object NavigateToAuthenticatorAppSetup : AccountSettingsAction

  /** Open the advanced PIN settings screen. */
  data object NavigateToAdvancedPinSettings : AccountSettingsAction

  /** Open the change phone number flow. */
  data object NavigateToChangePhoneNumber : AccountSettingsAction

  /** Open the flow that transfers this account to a new Android device. */
  data object NavigateToDeviceTransfer : AccountSettingsAction

  /** Open the flow that exports a copy of the user's account data. */
  data object NavigateToExportAccountData : AccountSettingsAction

  /** Send the user somewhere they can download a newer build. */
  data object OpenPlayStore : AccountSettingsAction

  /** Open registration so the user can re-register. */
  data object LaunchReRegistration : AccountSettingsAction

  /** Open the delete account flow. */
  data object NavigateToDeleteAccount : AccountSettingsAction

  /** Wipe every bit of app data off this device. */
  data object WipeAllData : AccountSettingsAction

  /** Tell the user that wiping app data didn't work. */
  data object ShowDataWipeFailed : AccountSettingsAction

  /** Tell the user that turning registration lock on didn't work. */
  data object ShowRegistrationLockEnableFailed : AccountSettingsAction

  /** Tell the user that turning registration lock off didn't work. */
  data object ShowRegistrationLockDisableFailed : AccountSettingsAction
}
