/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginmanualsave

import org.signal.core.util.censor
import org.signal.signallogin.RecoveryKeyGroups

/**
 * State for the screen that spells out the user's Signal Login so they can record it themselves before being asked to
 * type it back in.
 */
data class SignalLoginViewDetailsForManualSaveState(
  val accountId: String = "",
  val recoveryKey: String = "",
  /** Whether the sheet that double-checks the user really saved their login is up. */
  val showConfirmSavedSheet: Boolean = false
) {
  /** The recovery key broken into character groups, in display order. */
  val recoveryKeyGroups: RecoveryKeyGroups
    get() = RecoveryKeyGroups.from(recoveryKey)

  override fun toString(): String = "SignalLoginViewDetailsForManualSaveState(accountId=${accountId.censor()}, recoveryKey=${recoveryKey.censor()}, showConfirmSavedSheet=$showConfirmSavedSheet)"
}
