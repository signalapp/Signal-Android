/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import org.signal.core.util.censor
import kotlin.time.Duration

data class PinEntryState(
  val showNeedHelp: Boolean = false,
  val isAlphanumericKeyboard: Boolean = false,
  val loading: Boolean = false,
  val showNoDataToRestoreDialog: Boolean = false,
  val showContactSupportDialog: Boolean = false,
  val triesRemaining: Int? = null,
  /** True when the last wrong PIN the user entered matched the code they used to verify their phone number. */
  val enteredVerificationCode: Boolean = false,
  val mode: Mode = Mode.SvrRestore,
  val dialogs: Dialogs = Dialogs(),
  val e164: String? = null,
  /** The code the user used to verify their phone number, copied from the parent flow state. Used to detect when they re-enter it as their PIN. */
  val submittedVerificationCode: String? = null
) {
  override fun toString(): String {
    return "PinEntryState(showNeedHelp=$showNeedHelp, isAlphanumericKeyboard=$isAlphanumericKeyboard, loading=$loading, showNoDataToRestoreDialog=$showNoDataToRestoreDialog, triesRemaining=$triesRemaining, enteredVerificationCode=$enteredVerificationCode, mode=$mode, dialogs=$dialogs, e164=$e164, submittedVerificationCode=${submittedVerificationCode?.censor()})"
  }

  enum class Mode {
    RegistrationLock,
    SmsBypass,
    SvrRestore
  }

  data class Dialogs(
    val networkError: Boolean = false,
    /** When non-null, shows a rate limit error dialog. A non-positive duration indicates the server didn't say how long to wait. */
    val rateLimitedRetryAfter: Duration? = null,
    val unknownError: Boolean = false
  )
}
