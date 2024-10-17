/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.exceptions

import com.fasterxml.jackson.annotation.JsonCreator
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription.ChargeFailure

/**
 * HTTP 402 Exception when trying to submit credentials for a donation with
 * a failed payment.
 */
class InAppPaymentReceiptCredentialError @JsonCreator constructor(
  val chargeFailure: ChargeFailure
) : NonSuccessfulResponseCodeException(402) {
  override fun toString(): String {
    return """
      DonationReceiptCredentialError (402)
      Charge Failure: $chargeFailure
    """.trimIndent()
  }
}
