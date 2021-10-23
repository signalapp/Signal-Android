package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.zkgroup.receipts.ReceiptCredentialRequest;
import org.whispersystems.util.Base64;

class ReceiptCredentialRequestJson {
  @JsonProperty("receiptCredentialRequest")
  private final String receiptCredentialRequest;

  ReceiptCredentialRequestJson(ReceiptCredentialRequest receiptCredentialRequest) {
    this.receiptCredentialRequest = Base64.encodeBytes(receiptCredentialRequest.serialize());
  }
}
