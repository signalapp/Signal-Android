/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorsetup

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out by the fragment
 * hosting [AuthenticatorSetupScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AuthenticatorSetupAction {

  /** Leave the screen. */
  data object NavigateBack : AuthenticatorSetupAction

  /** Hand [uri] off to whichever authenticator app the user has installed. */
  data class LaunchAuthenticatorApp(val uri: String) : AuthenticatorSetupAction {
    override fun toString(): String = "LaunchAuthenticatorApp()"
  }

  /** Put [key] on the clipboard. */
  data class CopyKeyToClipboard(val key: String) : AuthenticatorSetupAction {
    override fun toString(): String = "CopyKeyToClipboard()"
  }

  /** Tell the user the setup key was copied. */
  data object ShowKeyCopied : AuthenticatorSetupAction

  /** Tell the user we couldn't find an app to hand the setup key to. */
  data object ShowNoAuthenticatorAppFound : AuthenticatorSetupAction

  /** Move on to the screen where the user enters a code from their authenticator app. */
  data object NavigateToCodeEntry : AuthenticatorSetupAction
}
