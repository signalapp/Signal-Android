/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import kotlin.time.Duration

data class PinEntryState(
  val showNeedHelp: Boolean = false,
  val isNumericKeyboard: Boolean = true,
  val loading: Boolean = false,
  val triesRemaining: Int? = null,
  val oneTimeEvent: OneTimeEvent? = null
) {
  sealed interface OneTimeEvent {
    data object NetworkError : OneTimeEvent
    data class RateLimited(val retryAfter: Duration) : OneTimeEvent
    data object UnknownError : OneTimeEvent
  }
}
