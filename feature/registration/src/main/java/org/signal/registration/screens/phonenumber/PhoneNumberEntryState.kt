/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import org.signal.registration.NetworkController.SessionMetadata
import kotlin.time.Duration

data class PhoneNumberEntryState(
  val regionCode: String = "US",
  val countryCode: String = "1",
  val nationalNumber: String = "",
  val formattedNumber: String = "",
  val sessionMetadata: SessionMetadata? = null,
  val showFullScreenSpinner: Boolean = false,
  val oneTimeEvent: OneTimeEvent? = null
) {
  sealed interface OneTimeEvent {
    data object NetworkError : OneTimeEvent
    data object UnknownError : OneTimeEvent
    data class RateLimited(val retryAfter: Duration) : OneTimeEvent
    data object ThirdPartyError : OneTimeEvent
    data object CouldNotRequestCodeWithSelectedTransport : OneTimeEvent
  }
}
