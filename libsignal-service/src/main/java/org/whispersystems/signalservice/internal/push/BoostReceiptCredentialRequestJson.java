package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest;
import org.signal.core.util.Base64;

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
