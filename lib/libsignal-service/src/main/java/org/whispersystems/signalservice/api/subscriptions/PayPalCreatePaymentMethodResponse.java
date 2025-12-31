package org.whispersystems.signalservice.api.subscriptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PayPalCreatePaymentMethodResponse {
  private final String approvalUrl;
  private final String token;

  @JsonCreator
  public PayPalCreatePaymentMethodResponse(@JsonProperty("approvalUrl") String approvalUrl, @JsonProperty("token") String token) {
    this.approvalUrl = approvalUrl;
    this.token       = token;
  }

  public String getApprovalUrl() {
    return approvalUrl;
  }

  public String getToken() {
    return token;
  }
}
