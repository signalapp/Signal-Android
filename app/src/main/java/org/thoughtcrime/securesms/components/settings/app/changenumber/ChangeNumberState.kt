/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.viewmodel.NumberViewState
import org.whispersystems.signalservice.api.svr.Svr3Credentials
import org.whispersystems.signalservice.internal.push.AuthCredentials

/**
 * State holder for [ChangeNumberViewModel]
 */
data class ChangeNumberState(
  val number: NumberViewState = NumberViewState.INITIAL,
  val enteredCode: String? = null,
  val enteredPin: String = "",
  val oldPhoneNumber: NumberViewState = NumberViewState.INITIAL,
  val sessionId: String? = null,
  val changeNumberOutcome: ChangeNumberOutcome? = null,
  val lockedTimeRemaining: Long = 0L,
  val svr2Credentials: AuthCredentials? = null,
  val svr3Credentials: Svr3Credentials? = null,
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
