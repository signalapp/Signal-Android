package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class PayPalCreatePaymentMethodPayload {
  @JsonProperty
  private String returnUrl;

  @JsonProperty
  private String cancelUrl;

  PayPalCreatePaymentMethodPayload(String returnUrl, String cancelUrl) {
    this.returnUrl = returnUrl;
    this.cancelUrl = cancelUrl;
  }
}
