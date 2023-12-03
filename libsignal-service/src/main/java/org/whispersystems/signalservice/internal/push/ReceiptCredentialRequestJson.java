package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest;
import org.signal.core.util.Base64;

class ReceiptCredentialRequestJson {
  @JsonProperty("receiptCredentialRequest")
  private final String receiptCredentialRequest;

  ReceiptCredentialRequestJson(ReceiptCredentialRequest receiptCredentialRequest) {
    this.receiptCredentialRequest = Base64.encodeWithPadding(receiptCredentialRequest.serialize());
  }
}
