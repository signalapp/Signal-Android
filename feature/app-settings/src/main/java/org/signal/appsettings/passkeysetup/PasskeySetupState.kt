/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.passkeysetup

data class PasskeySetupState(
  val dialog: Dialog = Dialog.None
) {

  /** Whichever dialog the screen is showing, if any. Only one is ever up at a time. */
  sealed interface Dialog {
    data object None : Dialog

    /** Covers the two legs of the ceremony that talk to the service, which a tap otherwise looks ignored during. */
    data object InProgress : Dialog

    /** Explains that the account already has the [maxTwoFactorMethods] second factors it's allowed. */
    data class MaxTwoFactorMethodsReached(val maxTwoFactorMethods: Int) : Dialog
  }
}
