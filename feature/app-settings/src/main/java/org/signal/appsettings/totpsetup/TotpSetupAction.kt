/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpsetup

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out by the fragment
 * hosting [TotpSetupScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface TotpSetupAction {

  /** Leave the screen. */
  data object NavigateBack : TotpSetupAction

  /** Hand [uri] off to whichever authenticator app the user has installed. */
  data class LaunchTotpApp(val uri: String) : TotpSetupAction {
    override fun toString(): String = "LaunchTotpApp()"
  }

  /** Put [key] on the clipboard. */
  data class CopyKeyToClipboard(val key: String) : TotpSetupAction {
    override fun toString(): String = "CopyKeyToClipboard()"
  }

  /** Tell the user the setup key was copied. */
  data object ShowKeyCopied : TotpSetupAction

  /** Tell the user we couldn't find an app to hand the setup key to. */
  data object ShowNoTotpAppFound : TotpSetupAction

  /** Move on to the screen where the user enters a code from their authenticator app. */
  data object NavigateToCodeEntry : TotpSetupAction
}
