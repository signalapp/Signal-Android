/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactornameentry

import org.signal.appsettings.account.TwoFactorMethodNamed

/**
 * One-shot side effects that have to be carried out by whatever hosts [TwoFactorNameEntryScreen] rather than the
 * screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface TwoFactorNameEntryAction {

  /** The method has its name, so hand [result] to account settings and return there. */
  data class MethodNamed(val result: TwoFactorMethodNamed) : TwoFactorNameEntryAction
}
