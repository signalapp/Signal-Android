/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.phonenumberprivacy

/**
 * Everything [PhoneNumberPrivacySettingsScreen] needs to render.
 */
data class PhoneNumberPrivacySettingsState(
  val phoneNumberSharing: Boolean = false,
  val discoverableByPhoneNumber: Boolean = true,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Warns that turning off discovery by phone number makes the user harder to find. */
    data object ConfirmNotDiscoverable : Dialog
  }

  /** Whichever snackbar the screen is showing, if any. Cleared once it's dismissed. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** Explains that the user has to hide their number before they can turn off discovery. */
    data object HideNumberFirst : Snackbar
  }
}
