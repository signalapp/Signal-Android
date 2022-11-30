package org.whispersystems.signalservice.api.subscriptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response object from creating a payment intent via PayPal
 */
public class PayPalCreatePaymentIntentResponse {

  private final String approvalUrl;
  private final String paymentId;

  @JsonCreator
  public PayPalCreatePaymentIntentResponse(@JsonProperty("approvalUrl") String approvalUrl, @JsonProperty("paymentId") String paymentId) {
    this.approvalUrl = approvalUrl;
    this.paymentId   = paymentId;
  }

  public String getApprovalUrl() {
    return approvalUrl;
  }

  public String getPaymentId() {
    return paymentId;
  }
}
