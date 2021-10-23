package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DonationIntentResult {
  @JsonProperty("id")
  private String id;

  @JsonProperty("client_secret")
  private String clientSecret;

  public DonationIntentResult(@JsonProperty("id") String id, @JsonProperty("client_secret") String clientSecret) {
    this.id           = id;
    this.clientSecret = clientSecret;
  }

  public String getId() {
    return id;
  }

  public String getClientSecret() {
    return clientSecret;
  }
}
