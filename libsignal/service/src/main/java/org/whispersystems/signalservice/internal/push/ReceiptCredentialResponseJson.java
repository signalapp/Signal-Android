package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.receipts.ReceiptCredentialRequest;
import org.signal.zkgroup.receipts.ReceiptCredentialResponse;
import org.whispersystems.util.Base64;

import java.io.IOException;

class ReceiptCredentialResponseJson {

  private final ReceiptCredentialResponse receiptCredentialResponse;

  ReceiptCredentialResponseJson(@JsonProperty("receiptCredentialResponse") String receiptCredentialResponse) {
    ReceiptCredentialResponse response;
    try {
      response = new ReceiptCredentialResponse(Base64.decode(receiptCredentialResponse));
    } catch (IOException | InvalidInputException e) {
      response = null;
    }

    this.receiptCredentialResponse = response;
  }

  public ReceiptCredentialResponse getReceiptCredentialResponse() {
    return receiptCredentialResponse;
  }
}
