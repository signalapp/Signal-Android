/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import kotlin.time.Duration

data class PinEntryState(
  val showNeedHelp: Boolean = false,
  val isAlphanumericKeyboard: Boolean = false,
  val loading: Boolean = false,
  val showNoDataToRestoreDialog: Boolean = false,
  val triesRemaining: Int? = null,
  val mode: Mode = Mode.SvrRestore,
  val dialogs: Dialogs = Dialogs(),
  val e164: String? = null
) {
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
