/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpsetup

data class TotpSetupState(
  /** The key the user hands to their authenticator app, grouped for reading rather than for pasting. */
  val setupKey: String = "",
  /** True until the service has handed us a key, which is everything on this screen. */
  val loading: Boolean = true,
  val dialog: Dialog = Dialog.None
) {

  /** Nothing on this screen works without a key, so the buttons wait for one. */
  val canContinue: Boolean
    get() = !loading && setupKey.isNotEmpty()

  override fun toString(): String = "TotpSetupState(setupKey=${if (setupKey.isEmpty()) "empty" else "present"}, loading=$loading, dialog=$dialog)"

  sealed interface Dialog {
    data object None : Dialog

    /** The account already has the [maxApps] authenticator apps it's allowed, so there's no key to be had. */
    data class MaxAppsReached(val maxApps: Int) : Dialog

    /** We couldn't reach the service to ask for a key. */
    data object NetworkFailure : Dialog
  }
}
