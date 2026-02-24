/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest;
import org.signal.core.util.Base64;
import org.whispersystems.signalservice.internal.push.DonationProcessor;

class BoostReceiptCredentialRequestJson {
  @JsonProperty("paymentIntentId")
  private final String paymentIntentId;

  @JsonProperty("receiptCredentialRequest")
  private final String receiptCredentialRequest;

  @JsonProperty("processor")
  private final String processor;

  BoostReceiptCredentialRequestJson(String paymentIntentId, ReceiptCredentialRequest receiptCredentialRequest, DonationProcessor processor) {
    this.paymentIntentId          = paymentIntentId;
    this.receiptCredentialRequest = Base64.encodeWithPadding(receiptCredentialRequest.serialize());
    this.processor                = processor.getCode();
  }
}
