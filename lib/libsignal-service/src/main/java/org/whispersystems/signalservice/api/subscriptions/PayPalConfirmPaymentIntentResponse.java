package org.whispersystems.signalservice.api.subscriptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response object from creating a payment intent via PayPal
 */
public class PayPalConfirmPaymentIntentResponse {

  private final String paymentId;

  @JsonCreator
  public PayPalConfirmPaymentIntentResponse(@JsonProperty("paymentId") String paymentId) {
    this.paymentId   = paymentId;
  }

  public String getPaymentId() {
    return paymentId;
  }
}
