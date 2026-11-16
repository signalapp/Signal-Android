/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

/**
 * One-shot side effects that need an Activity, and therefore have to be carried out by the host rather than the screen
 * itself. Navigation doesn't come through here: the view model sends it straight to its parent.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface PrivacySettingsAction {

  /** Open the flow to change the legacy passphrase. */
  data object LaunchChangePassphrase : PrivacySettingsAction

  /** Open the system settings where the user can set up a device screen lock. */
  data object OpenDeviceSecuritySettings : PrivacySettingsAction

  /** Open the support article explaining incognito keyboard. */
  data object OpenIncognitoKeyboardLearnMore : PrivacySettingsAction

  /** Apply the screen security setting to the current window. */
  data class ApplyScreenSecurity(val enabled: Boolean) : PrivacySettingsAction
}
