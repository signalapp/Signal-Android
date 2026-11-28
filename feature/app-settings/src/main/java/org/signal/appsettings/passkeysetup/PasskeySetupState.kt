/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.passkeysetup

data class PasskeySetupState(
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** There's no passkey provider on the device to create a credential with. */
    data object NoPasskeyProvider : Snackbar

    /** The ceremony didn't produce a passkey the service accepted. */
    data object PasskeyCreationFailed : Snackbar
  }

  /** Whichever dialog the screen is showing, if any. Only one is ever up at a time. */
  sealed interface Dialog {
    data object None : Dialog

    /** Covers the two legs of the ceremony that talk to the service, which a tap otherwise looks ignored during. */
    data object InProgress : Dialog

    /** Explains that the account already has the [maxTwoFactorMethods] second factors it's allowed. */
    data class MaxTwoFactorMethodsReached(val maxTwoFactorMethods: Int) : Dialog
  }
}
