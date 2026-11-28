/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

import org.signal.core.util.Base64
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest
import org.whispersystems.signalservice.internal.push.DonationProcessor

internal class BoostReceiptCredentialRequestJson(
  val paymentIntentId: String,
  val receiptCredentialRequest: String,
  val processor: String
) {
  constructor(paymentIntentId: String, receiptCredentialRequest: ReceiptCredentialRequest, processor: DonationProcessor) : this(
    paymentIntentId = paymentIntentId,
    receiptCredentialRequest = Base64.encodeWithPadding(receiptCredentialRequest.serialize()),
    processor = processor.code
  )
}
