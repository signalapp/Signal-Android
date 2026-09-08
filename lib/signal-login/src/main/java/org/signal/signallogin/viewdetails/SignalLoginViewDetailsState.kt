/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.viewdetails

import org.signal.core.util.censor
import org.signal.signallogin.RecoveryKeyGroups

/**
 * State for the screen that shows the user the full keys that make up their Signal Login.
 */
data class SignalLoginViewDetailsState(
  val accountKey: String = "",
  val recoveryKey: String = ""
) {
  /** The recovery key broken into character groups, in display order. */
  val recoveryKeyGroups: RecoveryKeyGroups
    get() = RecoveryKeyGroups.from(recoveryKey)

  override fun toString(): String = "SignalLoginViewDetailsState(accountKey=${accountKey.censor()}, recoveryKey=${recoveryKey.censor()})"
}
