/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactornameentry

import org.signal.appsettings.account.TwoFactorMethod

/**
 * One-shot side effects that need the nav graph, and therefore have to be carried out by the fragment hosting
 * [TwoFactorNameEntryScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface TwoFactorNameEntryAction {

  /** Leave the screen. */
  data object NavigateBack : TwoFactorNameEntryAction

  /** The method has a name now, so go back to the account settings screen that lists it. */
  data object NavigateToAccountSettings : TwoFactorNameEntryAction

  /** Tell the user their second factor of [kind] was set up. */
  data class ShowMethodSetUp(val kind: TwoFactorMethod.Kind) : TwoFactorNameEntryAction

  /** Tell the user their second factor of [kind] was renamed. */
  data class ShowMethodRenamed(val kind: TwoFactorMethod.Kind) : TwoFactorNameEntryAction

  /** Tell the user the name didn't stick, so they know to try again. */
  data object ShowNameNotSaved : TwoFactorNameEntryAction
}
