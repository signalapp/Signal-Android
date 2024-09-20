/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.exceptions

import com.fasterxml.jackson.annotation.JsonCreator
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription.ChargeFailure
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription.Processor

/**
 * HTTP 440 Exception when something bad happens while updating a user's subscription level or
 * confirming a PayPal intent.
 */
class InAppPaymentProcessorError @JsonCreator constructor(
  val processor: Processor,
  val chargeFailure: ChargeFailure
) : NonSuccessfulResponseCodeException(440) {
  override fun toString(): String {
    return """
      DonationProcessorError (440)
      Processor: $processor
      Charge Failure: $chargeFailure
    """.trimIndent()
  }
}
