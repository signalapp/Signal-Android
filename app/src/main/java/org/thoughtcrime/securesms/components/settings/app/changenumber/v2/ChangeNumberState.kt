/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber.v2

import org.thoughtcrime.securesms.registration.v2.data.network.Challenge
import org.thoughtcrime.securesms.registration.v2.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState
import org.whispersystems.signalservice.internal.push.AuthCredentials

/**
 * State holder for [ChangeNumberV2ViewModel]
 */
data class ChangeNumberState(
  val number: NumberViewState = NumberViewState.INITIAL,
  val enteredCode: String? = null,
  val enteredPin: String = "",
  val oldPhoneNumber: NumberViewState = NumberViewState.INITIAL,
  val sessionId: String? = null,
  val changeNumberOutcome: ChangeNumberOutcome? = null,
  val lockedTimeRemaining: Long = 0L,
  val svrCredentials: AuthCredentials? = null,
  val svrTriesRemaining: Int = 10,
  val incorrectCodeAttempts: Int = 0,
  val nextSmsTimestamp: Long = 0L,
  val nextCallTimestamp: Long = 0L,
  val inProgress: Boolean = false,
  val captchaToken: String? = null,
  val challengesRequested: List<Challenge> = emptyList(),
  val challengesPresented: Set<Challenge> = emptySet(),
  val allowedToRequestCode: Boolean = false
) {
  val challengesRemaining: List<Challenge> = challengesRequested.filterNot { it in challengesPresented }
}

sealed interface ChangeNumberOutcome {
  data object RecoveryPasswordWorked : ChangeNumberOutcome
  data object VerificationCodeWorked : ChangeNumberOutcome
  class ChangeNumberRequestOutcome(val result: VerificationCodeRequestResult) : ChangeNumberOutcome
}
