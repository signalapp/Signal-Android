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

  /** Ask the user to get past their screen lock before we show them the account and recovery keys. */
  data object AuthenticateToViewSignalLoginDetails : AccountSettingsAction

  /** Open the screen that shows the account and recovery keys that make up the Signal Login. */
  data object NavigateToSignalLoginDetails : AccountSettingsAction

  /** Open the flow that pairs a new authenticator app. */
  data object NavigateToTotpSetup : AccountSettingsAction

  /** Run the passkey creation ceremony, which needs an Activity to put the provider's sheet on screen. */
  data class CreatePasskey(val parameters: PasskeyCreationParameters) : AccountSettingsAction

  /** Open the screen that names [method], the passkey that was just added to the account and has no name yet. */
  data class NavigateToNameNewPasskey(val method: TwoFactorMethod) : AccountSettingsAction

  /** Open the screen that renames [method]. */
  data class NavigateToRenameMethod(val method: TwoFactorMethod) : AccountSettingsAction

  /** Ask the user to get past their screen lock before we remove [method] from the account. */
  data class AuthenticateToRemoveMethod(val method: TwoFactorMethod) : AccountSettingsAction

  /** Tell the user we couldn't confirm it was them, so whatever they asked for didn't happen. */
  data object ShowAuthenticationFailed : AccountSettingsAction

  /** Tell the user their second factor of [kind] was removed. */
  data class ShowMethodRemoved(val kind: TwoFactorMethod.Kind) : AccountSettingsAction

  /** Tell the user the removal didn't go through, so they know the method is still on the account. */
  data class ShowMethodRemovalFailed(val kind: TwoFactorMethod.Kind) : AccountSettingsAction

  /** Tell the user there's no passkey provider on the device to create a credential with. */
  data object ShowNoPasskeyProvider : AccountSettingsAction

  /** Tell the user the ceremony didn't produce a passkey. */
  data object ShowPasskeyCreationFailed : AccountSettingsAction

  /** Send the user to the support article at [url]. */
  data class OpenSupportArticle(val url: String) : AccountSettingsAction

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

  /** Ask the user to get past their screen lock before we send them into the delete account flow. */
  data object AuthenticateToDeleteAccount : AccountSettingsAction

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
