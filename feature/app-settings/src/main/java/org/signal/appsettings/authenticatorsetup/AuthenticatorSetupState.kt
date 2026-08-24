/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorsetup

data class AuthenticatorSetupState(
  /** The key the user hands to their authenticator app, either through the app link or by copying it. */
  val setupKey: String = ""
) {
  override fun toString(): String = "AuthenticatorSetupState(setupKey=${if (setupKey.isEmpty()) "empty" else "present"})"
}
