/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpsetup

/**
 * One-shot side effects that need a Context, and therefore have to be carried out by whatever hosts
 * [TotpSetupScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface TotpSetupAction {

  /** Hand [uri] off to whichever authenticator app the user has installed. */
  data class LaunchTotpApp(val uri: String) : TotpSetupAction {
    override fun toString(): String = "LaunchTotpApp()"
  }

  /** Put [key] on the clipboard. */
  data class CopyKeyToClipboard(val key: String) : TotpSetupAction {
    override fun toString(): String = "CopyKeyToClipboard()"
  }
}
