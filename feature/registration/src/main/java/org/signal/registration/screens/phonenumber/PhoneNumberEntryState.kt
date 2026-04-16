/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.phonenumber

import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.PendingRestoreOption
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.util.DebugLoggable
import org.signal.registration.util.DebugLoggableModel
import kotlin.time.Duration

data class PhoneNumberEntryState(
  val regionCode: String = "",
  val countryCode: String = "",
  val countryName: String = "",
  val countryEmoji: String = "",
  val nationalNumber: String = "",
  val formattedNumber: String = "",
  val sessionE164: String? = null,
  val sessionMetadata: SessionMetadata? = null,
  val showSpinner: Boolean = false,
  val showDialog: Boolean = false,
  val oneTimeEvent: OneTimeEvent? = null,
  val preExistingRegistrationData: PreExistingRegistrationData? = null,
  val restoredSvrCredentials: List<NetworkController.SvrCredentials> = emptyList(),
  val pendingRestoreOption: PendingRestoreOption? = null
) : DebugLoggableModel() {
  sealed interface OneTimeEvent : DebugLoggable {
    data object NetworkError : OneTimeEvent
    data object UnknownError : OneTimeEvent
    data class RateLimited(val retryAfter: Duration) : OneTimeEvent
    data object UnableToSendSms : OneTimeEvent
    data object CouldNotRequestCodeWithSelectedTransport : OneTimeEvent
  }
}
