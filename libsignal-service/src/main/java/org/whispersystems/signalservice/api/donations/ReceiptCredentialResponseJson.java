/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialResponse;
import org.signal.core.util.Base64;

import java.io.IOException;

import javax.annotation.Nullable;

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

  public @Nullable ReceiptCredentialResponse getReceiptCredentialResponse() {
    return receiptCredentialResponse;
  }
}
