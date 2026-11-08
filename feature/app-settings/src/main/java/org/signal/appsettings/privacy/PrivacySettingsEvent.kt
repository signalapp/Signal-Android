/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import kotlin.time.Duration

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface PrivacySettingsEvent {

  /** The screen came back to the foreground, so the blocked count and device security may have changed. */
  data object ScreenResumed : PrivacySettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : PrivacySettingsEvent

  /** The screen finished scrolling to the payment lock row it was asked to show. */
  data object ScrolledToPaymentLock : PrivacySettingsEvent

  /** The user tapped the phone number row. */
  data object PhoneNumberClicked : PrivacySettingsEvent

  /** The user tapped the blocked row. */
  data object BlockedClicked : PrivacySettingsEvent

  /** The user flipped the read receipts switch. */
  data class ReadReceiptsToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user flipped the typing indicators switch. */
  data class TypingIndicatorsToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user tapped the default disappearing messages timer row. */
  data object DefaultTimerClicked : PrivacySettingsEvent

  /** The user tapped the switch to turn off the legacy passphrase, which asks them to confirm first. */
  data object DisablePassphraseClicked : PrivacySettingsEvent

  /** The user confirmed turning off the legacy passphrase. */
  data object DisablePassphraseConfirmed : PrivacySettingsEvent

  /** The user tapped the change passphrase row. */
  data object ChangePassphraseClicked : PrivacySettingsEvent

  /** The user flipped the passphrase inactivity timeout switch. */
  data class PassphraseTimeoutToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user tapped the passphrase inactivity timeout interval row, which asks them to pick a timeout. */
  data object PassphraseTimeoutIntervalClicked : PrivacySettingsEvent

  /** The user picked a passphrase inactivity timeout. */
  data class PassphraseTimeoutSelected(val timeout: Duration) : PrivacySettingsEvent

  /** The user tapped the screen lock row while the device has a screen lock. */
  data object ScreenLockClicked : PrivacySettingsEvent

  /** The user tapped the screen lock row while the device has no screen lock, so it's disabled. */
  data object DisabledScreenLockClicked : PrivacySettingsEvent

  /** The user flipped the screen security switch. */
  data class ScreenSecurityToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user flipped the incognito keyboard switch. */
  data class IncognitoKeyboardToggled(val enabled: Boolean) : PrivacySettingsEvent

  /** The user tapped "Learn more" under the incognito keyboard switch. */
  data object IncognitoKeyboardLearnMoreClicked : PrivacySettingsEvent

  /** The user tapped the switch to turn payment lock on. */
  data object EnablePaymentLockClicked : PrivacySettingsEvent

  /** The user authenticated to turn payment lock off. Only sent once authentication succeeds. */
  data object PaymentLockDisableAuthenticated : PrivacySettingsEvent

  /** The user chose to open the system settings to set up a device screen lock. */
  data object OpenDeviceSecuritySettingsClicked : PrivacySettingsEvent

  /** The host couldn't open any of the system settings screens for setting up a device screen lock. */
  data object DeviceSecuritySettingsUnavailable : PrivacySettingsEvent

  /** The user tapped the advanced row. */
  data object AdvancedClicked : PrivacySettingsEvent

  /** Dismisses whatever is in [PrivacySettingsState.dialog]. */
  data object DialogDismissed : PrivacySettingsEvent

  /** The snackbar in [PrivacySettingsState.snackbar] has gone away. */
  data object SnackbarDismissed : PrivacySettingsEvent
}
