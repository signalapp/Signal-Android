/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Everything [PrivacySettingsScreen] needs to render.
 */
data class PrivacySettingsState(
  val hasPhoneNumber: Boolean = true,
  val blockedCount: Int = 0,
  val readReceipts: Boolean = false,
  val typingIndicators: Boolean = false,
  val universalExpireTimer: Duration = Duration.ZERO,
  val isObsoletePassphraseEnabled: Boolean = false,
  val isObsoletePassphraseTimeoutEnabled: Boolean = false,
  val obsoletePassphraseTimeout: Duration = 5.minutes,
  val isDeviceSecure: Boolean = true,
  val screenLock: Boolean = false,
  val screenLockTimeout: Duration = Duration.ZERO,
  val screenSecurity: Boolean = false,
  val incognitoKeyboard: Boolean = false,
  val paymentLock: Boolean = false,
  val scrollToPaymentLock: Boolean = false,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** Screen lock can't do anything without a device credential, so it reads as off until one is set. */
  val isScreenLockOn: Boolean
    get() = isDeviceSecure && screenLock

  /** Payment lock can't do anything without a device credential, so it reads as off until one is set. */
  val isPaymentLockOn: Boolean
    get() = isDeviceSecure && paymentLock

  /** A screen lock timeout of zero means "immediately", which the app treats as one minute. */
  val effectiveScreenLockTimeout: Duration
    get() = if (screenLockTimeout == Duration.ZERO) 1.minutes else screenLockTimeout

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Confirms turning off the legacy passphrase, which can't be undone. */
    data object ConfirmDisablePassphrase : Dialog

    /** Lets the user pick how long Signal can be inactive before the passphrase is required again. */
    data object PassphraseTimeout : Dialog

    /** Explains that payment lock needs a device screen lock, and offers to open the system settings. */
    data object PaymentLockNeedsDeviceSecurity : Dialog
  }

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** Explains that screen lock needs a device screen lock. */
    data object ScreenLockNeedsDeviceSecurity : Snackbar

    /** Explains that there is no legacy passphrase to change. */
    data object PassphraseNotSet : Snackbar

    /** The system settings for setting up a device screen lock couldn't be opened. */
    data object DeviceSecuritySettingsUnavailable : Snackbar
  }
}
