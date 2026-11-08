/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpcodeentry

/**
 * One-shot side effects that need the nav graph, and therefore have to be carried out by the fragment hosting
 * [TotpCodeEntryScreen] rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface TotpCodeEntryAction {

  /** Leave the screen. */
  data object NavigateBack : TotpCodeEntryAction

  /** The new authenticator app is confirmed and the service gave it [appId], so go name it. */
  data class NavigateToNaming(val appId: Long, val createdAt: Long) : TotpCodeEntryAction

  /** Go back to setup, because the key the user was confirming is gone and there's nothing to retry against. */
  data object NavigateToSetup : TotpCodeEntryAction
}
