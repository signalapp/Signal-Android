/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import org.signal.registration.util.DebugLoggable
import org.signal.registration.util.DebugLoggableModel
import kotlin.time.Duration

data class PinEntryState(
  val showNeedHelp: Boolean = false,
  val isAlphanumericKeyboard: Boolean = false,
  val loading: Boolean = false,
  val triesRemaining: Int? = null,
  val mode: Mode = Mode.SvrRestore,
  val oneTimeEvent: OneTimeEvent? = null,
  val e164: String? = null
) : DebugLoggableModel() {
  enum class Mode {
    RegistrationLock,
    SmsBypass,
    SvrRestore
  }

  sealed interface OneTimeEvent : DebugLoggable {
    data object NetworkError : OneTimeEvent
    data class RateLimited(val retryAfter: Duration) : OneTimeEvent
    data object SvrDataMissing : OneTimeEvent
    data object UnknownError : OneTimeEvent
  }
}
