/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by the host rather than the
 * screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AccountSettingsAction {

  /** Open the flow for creating a PIN for the first time. */
  data object LaunchCreatePinFlow : AccountSettingsAction

  /** Open the flow for changing an existing PIN. */
  data object LaunchChangePinFlow : AccountSettingsAction

  /** Ask the user to get past their screen lock before we show them the account and recovery keys. */
  data object AuthenticateToViewSignalLoginDetails : AccountSettingsAction

  /** Ask the user to get past their screen lock before we send them into authenticator app setup. */
  data object AuthenticateToAddTotpApp : AccountSettingsAction

  /** Ask the user to get past their screen lock before we open passkey setup. */
  data object AuthenticateToAddPasskey : AccountSettingsAction

  /** Ask the user to get past their screen lock before we remove [method] from the account. */
  data class AuthenticateToRemoveMethod(val method: TwoFactorMethod) : AccountSettingsAction

  /** Send the user to the support article at [url]. */
  data class OpenSupportArticle(val url: String) : AccountSettingsAction

  /** Send the user somewhere they can download a newer build. */
  data object OpenPlayStore : AccountSettingsAction

  /** Open registration so the user can re-register. */
  data object LaunchReRegistration : AccountSettingsAction

  /** Ask the user to get past their screen lock before we send them into the delete account flow. */
  data object AuthenticateToDeleteAccount : AccountSettingsAction

  /** Wipe every bit of app data off this device. */
  data object WipeAllData : AccountSettingsAction
}
