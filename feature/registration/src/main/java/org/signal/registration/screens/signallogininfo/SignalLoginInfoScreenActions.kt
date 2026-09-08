/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import org.signal.core.util.censor

/**
 * Things [SignalLoginInfoViewModel] needs an activity to do for it, since the credential manager can only be driven
 * from one.
 */
sealed interface SignalLoginInfoScreenActions {
  /** Hand both halves of the Signal Login to the system password manager to store. */
  data class SaveToPasswordManager(val accountId: String, val recoveryKey: String) : SignalLoginInfoScreenActions {
    override fun toString(): String = "SaveToPasswordManager(accountId=${accountId.censor()}, recoveryKey=${recoveryKey.censor()})"
  }

  /** Read the Signal Login back out of the password manager to prove it really landed there. */
  data class ReadBackFromPasswordManager(val accountId: String) : SignalLoginInfoScreenActions {
    override fun toString(): String = "ReadBackFromPasswordManager(accountId=${accountId.censor()})"
  }
}
