/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpnameentry

/**
 * One-shot side effects that need the nav graph, and therefore have to be carried out by the fragment hosting
 * [TotpNameEntryScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface TotpNameEntryAction {

  /** Leave the screen. */
  data object NavigateBack : TotpNameEntryAction

  /** The app has a name now, so go back to the account settings screen that lists it. */
  data object NavigateToAccountSettings : TotpNameEntryAction

  /** Tell the user their authenticator app was set up. */
  data object ShowTotpAppSetUp : TotpNameEntryAction

  /** Tell the user their authenticator app was renamed. */
  data object ShowTotpAppRenamed : TotpNameEntryAction

  /** Tell the user the name didn't stick, so they know to try again. */
  data object ShowNameNotSaved : TotpNameEntryAction
}
