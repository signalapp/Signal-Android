/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorcodeentry

/**
 * One-shot side effects that need the nav graph, and therefore have to be carried out by the fragment hosting
 * [AuthenticatorCodeEntryScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AuthenticatorCodeEntryAction {

  /** Leave the screen. */
  data object NavigateBack : AuthenticatorCodeEntryAction

  /** The authenticator app is set up, so go back to account settings. */
  data object NavigateToAccountSettings : AuthenticatorCodeEntryAction

  /** Tell the user their authenticator app was added. */
  data object ShowAuthenticatorAppAdded : AuthenticatorCodeEntryAction
}
